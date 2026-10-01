package com.ezzy.vault.sync

import android.content.Context
import android.os.Build
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.model.FieldDraft
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.data.model.ItemDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/** Where the browser link stands, as the Connect Browser screen draws it. */
sealed interface LinkState {
    /** Nothing linked and nothing in progress. The server is not running. */
    data object Off : LinkState

    /**
     * Showing the phone's address and a one-time code for the user to type into Chrome. The
     * address only says where to knock; the code is the secret both sides derive the key from.
     * [host] is null when the phone is not on Wi-Fi.
     */
    data class Pairing(
        val host: String?,
        val port: Int,
        val code: String,
        val expiresAt: Long,
    ) : LinkState

    /** Chrome sent the right code; now the user picks the PIN that will open it. */
    data class SetPin(val browserName: String) : LinkState

    /** PIN chosen; waiting for the extension to come and collect it. */
    data class WaitingForBrowser(val browserName: String) : LinkState

    data class Linked(val browser: LinkedBrowser, val host: String?, val port: Int) : LinkState
}

/**
 * The phone's half of the Chrome extension: pairing, the session key, and every request the
 * extension can make. Lives for the whole process in [AppContainer]; [SyncService] keeps the
 * process (and so the server) alive while a browser is linked.
 *
 * Nothing here ever leaves the local network, and nothing is readable on it: past pairing, every
 * message both ways is AES-GCM sealed with a key only this phone and that one browser hold.
 */
class BrowserLink(private val context: Context, private val container: AppContainer) {

    private val store = BrowserLinkStore(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private var server: LanHttpServer? = null
    private var sessionKey: ByteArray? = null
    private var pairing: LinkState.Pairing? = null
    private var pairingFailures = 0
    /** The PIN the user just chose, held in memory only until the extension collects it. */
    private var pendingPin: String? = null
    private val seenRequests = LinkedHashMap<String, Long>()
    private val notifyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncsInFlight = java.util.concurrent.atomic.AtomicInteger(0)

    private val _state = MutableStateFlow<LinkState>(LinkState.Off)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    init {
        val saved = store.browser()
        // A pairing that never got as far as handing over its PIN is useless after a restart —
        // the PIN only ever lived in memory — so it is dropped rather than left half-done.
        if (saved != null && !saved.active) store.clear()
        refreshState()
    }

    val hasLinkedBrowser: Boolean get() = store.browser()?.active == true

    /** True whenever something needs the server: a pairing in progress or a linked browser. */
    val needsServer: Boolean get() = _state.value != LinkState.Off

    // ---- Called from the Connect Browser screen ------------------------------------------------

    /** Starts (or restarts) pairing with a fresh code. Replaces any browser linked before. */
    suspend fun startPairing(): LinkState.Pairing? = mutex.withLock {
        if (!ensureServer()) return null
        val code = buildString {
            val bytes = SyncCrypto.randomBytes(CODE_LENGTH)
            bytes.forEach { append(CODE_ALPHABET[(it.toInt() and 0xFF) % CODE_ALPHABET.length]) }
        }
        pairingFailures = 0
        pendingPin = null
        val next = LinkState.Pairing(
            host = localAddress(),
            port = server?.port ?: -1,
            code = code,
            expiresAt = System.currentTimeMillis() + CODE_LIFETIME_MS,
        )
        pairing = next
        _state.value = next
        SyncService.start(context)
        next
    }

    /** Leaves the pairing screen without linking anything. */
    suspend fun cancelPairing() = mutex.withLock {
        pairing = null
        val browser = store.browser()
        if (browser != null && !browser.active) {
            store.clear()
            sessionKey = null
        }
        pendingPin = null
        refreshState()
        if (_state.value == LinkState.Off) stopServer()
    }

    /** Called once the user has typed and confirmed their PIN on the phone. */
    suspend fun setPin(pin: String) = mutex.withLock {
        val browser = store.browser() ?: return@withLock
        pendingPin = pin
        _state.value = LinkState.WaitingForBrowser(browser.name)
    }

    /** Forgets the browser. Its next request is refused, which tells it to wipe itself. */
    suspend fun disconnect() = mutex.withLock {
        store.clear()
        sessionKey = null
        pairing = null
        pendingPin = null
        _state.value = LinkState.Off
        stopServer()
    }

    /** Started by [SyncService]; safe to call more than once. */
    fun ensureServerRunning(): Boolean = ensureServer()

    fun stopServer() {
        server?.stop()
        server = null
        SyncService.stop(context)
    }

    /** The phone's own address on the Wi-Fi it is connected to, or null off Wi-Fi. */
    fun localAddress(): String? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }
            .getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { iface ->
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { iface.name to it.hostAddress }
            }
        // Wi-Fi first, then a hotspot the phone itself is running, then anything else local.
        return (candidates.firstOrNull { it.first.startsWith("wlan") }
            ?: candidates.firstOrNull { it.first.startsWith("ap") || it.first.startsWith("swlan") }
            ?: candidates.firstOrNull())?.second
    }

    // ---- The server ------------------------------------------------------------------------------

    private fun ensureServer(): Boolean {
        server?.takeIf { it.isRunning }?.let { return true }
        val next = LanHttpServer(::handle)
        if (!next.start()) return false
        server = next
        refreshState()
        return true
    }

    private fun refreshState() {
        val browser = store.browser()
        _state.value = when {
            pairing != null -> pairing!!
            browser == null -> LinkState.Off
            !browser.active && pendingPin != null -> LinkState.WaitingForBrowser(browser.name)
            !browser.active -> LinkState.SetPin(browser.name)
            else -> LinkState.Linked(browser, localAddress(), server?.port ?: -1)
        }
    }

    private suspend fun handle(method: String, path: String, body: ByteArray): LanHttpServer.Reply =
        when {
            method == "GET" && path == "/ping" -> LanHttpServer.Reply(
                200,
                """{"app":"ezzy","v":$PROTOCOL_VERSION,"phoneId":"${store.phoneId}"}""",
            )
            method == "POST" && path == "/pair" -> mutex.withLock { pair(body) }
            method == "POST" && path == "/api" -> api(body)
            else -> LanHttpServer.Reply(404, """{"error":"not_found"}""")
        }

    private fun pair(body: ByteArray): LanHttpServer.Reply {
        val current = pairing ?: return error(409, "not_pairing")
        if (System.currentTimeMillis() > current.expiresAt) return error(409, "code_expired")
        val request = runCatching { json.decodeFromString(PairRequest.serializer(), body.decodeToString()) }
            .getOrNull() ?: return error(400, "bad_request")
        if (request.v != PROTOCOL_VERSION) return error(409, "version_mismatch")

        val codeKey = codeKey(current.code)
        val extensionPub = runCatching { SyncCrypto.unb64(request.pub) }.getOrNull()
            ?: return error(400, "bad_request")
        val mac = runCatching { SyncCrypto.unb64(request.mac) }.getOrNull() ?: return error(400, "bad_request")
        if (!SyncCrypto.constantTimeEquals(SyncCrypto.hmac(codeKey, extensionPub), mac)) {
            // A handful of wrong guesses burns the code, so it cannot be ground through.
            if (++pairingFailures >= MAX_PAIRING_FAILURES) {
                pairing = null
                _state.value = LinkState.Off
            }
            return error(403, "wrong_code")
        }

        val peer = runCatching { SyncCrypto.publicKeyFromSpki(extensionPub) }.getOrNull()
            ?: return error(400, "bad_key")
        val own = SyncCrypto.newKeyPair()
        val shared = SyncCrypto.ecdh(own.private, peer)
        val key = SyncCrypto.hkdf(shared, codeKey, KEY_INFO.toByteArray())
        val ownPub = own.public.encoded

        val browser = LinkedBrowser(
            deviceId = SyncCrypto.hex(SyncCrypto.randomBytes(16)),
            name = request.name.trim().take(60).ifEmpty { "Chrome" },
            pairedAt = System.currentTimeMillis(),
            active = false,
            lastSyncAt = 0L,
        )
        store.save(browser, key)
        sessionKey = key
        pairing = null
        pendingPin = null
        seenRequests.clear()
        _state.value = LinkState.SetPin(browser.name)

        val response = PairResponse(
            v = PROTOCOL_VERSION,
            pub = SyncCrypto.b64(ownPub),
            deviceId = browser.deviceId,
            phoneId = store.phoneId,
            phoneName = phoneName(),
            mac = SyncCrypto.b64(SyncCrypto.hmac(codeKey, ownPub + extensionPub)),
        )
        return LanHttpServer.Reply(200, json.encodeToString(PairResponse.serializer(), response))
    }

    private suspend fun api(body: ByteArray): LanHttpServer.Reply {
        val sealed = runCatching { json.decodeFromString(SealedRequest.serializer(), body.decodeToString()) }
            .getOrNull() ?: return error(400, "bad_request")
        val browser = store.browser()
        if (browser == null || browser.deviceId != sealed.d) return error(401, "unknown_device")
        val key = sessionKey ?: store.sessionKey()?.also { sessionKey = it } ?: return error(401, "unknown_device")

        val plain = runCatching {
            SyncCrypto.open(key, SyncCrypto.unb64(sealed.iv), SyncCrypto.unb64(sealed.c), "ezzy-req:${sealed.d}".toByteArray())
        }.getOrNull() ?: return error(401, "bad_seal")
        val request = runCatching { json.decodeFromString(ApiRequest.serializer(), plain.decodeToString()) }
            .getOrNull() ?: return error(400, "bad_request")

        val now = System.currentTimeMillis()
        synchronized(seenRequests) {
            // The laptop's clock and the phone's can drift; ten minutes is generous for that and
            // still short enough that a recorded request is useless soon after.
            if (kotlin.math.abs(now - request.ts) > REPLAY_WINDOW_MS) return error(400, "stale_request")
            seenRequests.entries.removeAll { now - it.value > REPLAY_WINDOW_MS * 2 }
            if (seenRequests.containsKey(request.rid)) return error(400, "replayed_request")
            seenRequests[request.rid] = now
        }

        // Only real data moves get the "Syncing… → Done" notification; status polls stay silent.
        val announce = browser.active && request.op in DATA_OPS
        val startedAt = System.currentTimeMillis()
        if (announce && syncsInFlight.getAndIncrement() == 0) SyncNotifier.syncing(context)
        val outcome = runCatching { dispatch(request, browser) }
        if (announce) {
            notifyScope.launch {
                // Android drops notification updates that land too close together, so "Syncing…"
                // stays up a moment before it turns into "Done".
                delay((MIN_SYNCING_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
                // A save is usually followed straight away by a snapshot pull: only the last of
                // overlapping syncs may turn "Syncing…" into "Done".
                if (syncsInFlight.decrementAndGet() == 0) {
                    if (outcome.isSuccess) SyncNotifier.syncDone(context) else SyncNotifier.clearSync(context)
                }
            }
        }
        val response = ApiResponse(
            rid = request.rid,
            ok = outcome.isSuccess,
            r = outcome.getOrNull(),
            err = outcome.exceptionOrNull()?.let { it.message ?: "error" },
        )
        if (request.op != "logout") {
            store.markSynced(now)
            if (store.browser()?.active == true) {
                _state.value = LinkState.Linked(store.browser()!!, localAddress(), server?.port ?: -1)
            }
        }
        val (iv, ciphertext) = SyncCrypto.seal(
            key,
            json.encodeToString(ApiResponse.serializer(), response).toByteArray(),
            "ezzy-res:${sealed.d}".toByteArray(),
        )
        return LanHttpServer.Reply(
            200,
            json.encodeToString(SealedResponse.serializer(), SealedResponse(SyncCrypto.b64(iv), SyncCrypto.b64(ciphertext))),
        )
    }

    // ---- Operations ------------------------------------------------------------------------------

    private suspend fun dispatch(request: ApiRequest, browser: LinkedBrowser): JsonElement? {
        if (!browser.active && request.op != "status") throw IllegalStateException("pin_pending")
        val repo = container.repository
        return when (request.op) {
            "status" -> encode(status(browser))
            "snapshot" -> encode(snapshot())
            "saveItem" -> {
                val args = decode<SaveItemArgs>(request.a)
                val id = saveItem(args)
                encode(SavedResult(id, revision()))
            }
            "deleteItem" -> {
                repo.deleteItem(decode<IdArgs>(request.a).id)
                encode(SavedResult("", revision()))
            }
            "setPinned" -> {
                val args = decode<PinArgs>(request.a)
                repo.setPinned(args.id, args.pinned)
                encode(SavedResult(args.id, revision()))
            }
            "touch" -> {
                repo.markUsed(decode<IdArgs>(request.a).id)
                null
            }
            "setSectionLock" -> {
                val args = decode<SectionLockArgs>(request.a)
                container.settings.setSectionLocked(args.categoryId, args.locked)
                encode(SavedResult(args.categoryId, revision()))
            }
            "attachment" -> encode(attachment(decode<IdArgs>(request.a).id))
            "logout" -> {
                disconnect()
                null
            }
            else -> throw IllegalArgumentException("unknown_op")
        }
    }

    private suspend fun status(browser: LinkedBrowser): StatusResult = mutex.withLock {
        if (!browser.active) {
            val pin = pendingPin ?: return@withLock StatusResult(revision = "", pinPending = true)
            pendingPin = null
            store.markActive()
            _state.value = LinkState.Linked(store.browser()!!, localAddress(), server?.port ?: -1)
            SyncNotifier.connected(context, browser.name)
            return@withLock StatusResult(revision = revision(), pin = pin)
        }
        StatusResult(revision = revision())
    }

    private suspend fun saveItem(args: SaveItemArgs): String {
        val repo = container.repository
        val db = container.database
        val title = args.title.trim()
        require(title.isNotEmpty()) { "title_required" }
        requireNotNull(db.categoryDao().getById(args.categoryId)) { "unknown_section" }
        val templateId = args.templateId?.takeIf { db.templateDao().getById(it) != null }

        val existing = args.id?.let { repo.item(it) }
        val base = if (existing != null) repo.draftFor(existing.item.id, args.categoryId)
        else ItemDraft(categoryId = args.categoryId)
        val draft = base.copy(
            categoryId = args.categoryId,
            templateId = templateId ?: base.templateId.takeIf { existing != null },
            title = title.take(200),
            note = args.note.take(20_000),
            isPinned = args.isPinned,
            // Fresh ids every time: the field rows are rewritten wholesale on save, and an id
            // that came over the network is never trusted to be unique across the vault.
            fields = args.fields.take(200).map {
                FieldDraft(
                    id = UUID.randomUUID().toString(),
                    label = it.label.take(120),
                    value = it.value.take(20_000),
                    type = FieldType.from(it.type),
                )
            },
        )
        return repo.saveItem(draft)
    }

    private suspend fun attachment(id: String): AttachmentResult {
        val row = container.database.attachmentDao().getAll().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("not_found")
        require(row.sizeBytes <= MAX_ATTACHMENT_BYTES) { "too_large" }
        val bytes = container.repository.attachmentBytes(row.storedName)
            ?: throw IllegalStateException("unreadable")
        return AttachmentResult(row.displayName, row.mimeType, SyncCrypto.b64(bytes))
    }

    private suspend fun snapshot(): Snapshot {
        val body = snapshotBody()
        return body.copy(revision = revisionOf(body))
    }

    private suspend fun revision(): String = revisionOf(snapshotBody())

    private fun revisionOf(body: Snapshot): String =
        SyncCrypto.hex(SyncCrypto.sha256(json.encodeToString(Snapshot.serializer(), body).toByteArray())).take(32)

    private suspend fun snapshotBody(): Snapshot {
        val db = container.database
        val repo = container.repository
        val settings = container.settings.settings.first()
        val categories = db.categoryDao().getAll()
        val groups = db.itemGroupDao().getAll()
        val templates = db.templateDao().getAll()
        val items = db.itemDao().getAll().sortedBy { it.item.id }

        return Snapshot(
            revision = "",
            phoneName = phoneName(),
            ownerName = settings.displayName,
            clipboardClearSeconds = settings.clipboardClearSeconds,
            maskSecrets = settings.maskSecrets,
            categories = categories.map {
                SnapCategory(it.id, it.name, it.iconKey, it.colorKey, it.sortOrder, it.id in settings.lockedSections)
            },
            groups = groups.map { SnapGroup(it.id, it.categoryId, it.name, it.sortOrder) },
            templates = templates.map { template ->
                val spec = repo.decodeSpec(template.specJson)
                SnapTemplate(
                    id = template.id,
                    name = template.name,
                    iconKey = template.iconKey,
                    titleHint = spec.titleHint,
                    fields = spec.fields.map { SnapTemplateField(it.label, it.type.name, it.hint, it.required) },
                )
            },
            items = items.map { entry ->
                SnapItem(
                    id = entry.item.id,
                    categoryId = entry.item.categoryId,
                    templateId = entry.item.templateId,
                    groupId = entry.item.groupId,
                    title = entry.item.title,
                    subtitle = entry.item.subtitle,
                    note = entry.item.note,
                    isPinned = entry.item.isPinned,
                    createdAt = entry.item.createdAt,
                    updatedAt = entry.item.updatedAt,
                    // Left out of the hash on purpose: opening an entry would otherwise count
                    // as a change and make every browser refetch the whole vault.
                    lastUsedAt = 0L,
                    fields = entry.sortedFields.map { SnapField(it.id, it.label, it.value, it.type.name) },
                    attachments = entry.sortedAttachments.map {
                        SnapAttachment(it.id, it.displayName, it.caption, it.mimeType, it.sizeBytes)
                    },
                )
            },
        )
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private inline fun <reified T> decode(element: JsonElement?): T =
        json.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), requireNotNull(element) { "missing_args" })

    private inline fun <reified T> encode(value: T): JsonElement = json.encodeToJsonElement(value)

    private fun error(status: Int, code: String) = LanHttpServer.Reply(status, """{"error":"$code"}""")

    private fun codeKey(code: String): ByteArray =
        SyncCrypto.sha256("ezzy-pair-v1:${normalizeCode(code)}".toByteArray())

    private fun phoneName(): String =
        listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
            .distinct()
            .joinToString(" ")
            .take(60)

    companion object {
        /** No 0/O or 1/I, so a code read off the screen cannot be typed wrong. */
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 8
        const val CODE_LIFETIME_MS = 5 * 60_000L
        private const val MAX_PAIRING_FAILURES = 5
        private const val REPLAY_WINDOW_MS = 10 * 60_000L
        private const val MAX_ATTACHMENT_BYTES = 12L * 1024 * 1024
        private const val KEY_INFO = "ezzy-sync-key-v1"
        private const val MIN_SYNCING_MS = 900L
        /** Requests that actually move vault data, as opposed to polls and housekeeping. */
        private val DATA_OPS = setOf("snapshot", "saveItem", "deleteItem", "setPinned", "setSectionLock")

        fun normalizeCode(code: String): String =
            code.uppercase().filter { it.isLetterOrDigit() }

        /** "ABCD-EFGH" — how the code is shown for typing. */
        fun displayCode(code: String): String =
            normalizeCode(code).chunked(4).joinToString("-")
    }
}
