package com.ezzy.vault.sync

import android.content.Context
import com.ezzy.vault.data.crypto.KeystoreCrypto

/** The one browser this phone is linked to. Only ever one: pairing a new one replaces it. */
data class LinkedBrowser(
    val deviceId: String,
    val name: String,
    val pairedAt: Long,
    /** False between the QR scan and the moment the extension collects its PIN. */
    val active: Boolean,
    val lastSyncAt: Long,
)

/**
 * Where the link survives a restart. The session key itself is sealed with a Keystore key that
 * never leaves this phone, the same way every other secret EZZY writes to disk is — the
 * preferences file on its own is not enough to talk to the extension.
 */
class BrowserLinkStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val sealer = KeystoreCrypto(KEY_ALIAS)

    /** A random id for this phone, so the extension can recognise it again after an IP change. */
    val phoneId: String
        get() = prefs.getString(PHONE_ID, null) ?: SyncCrypto.hex(SyncCrypto.randomBytes(12)).also {
            prefs.edit().putString(PHONE_ID, it).apply()
        }

    fun browser(): LinkedBrowser? {
        val id = prefs.getString(DEVICE_ID, null) ?: return null
        return LinkedBrowser(
            deviceId = id,
            name = prefs.getString(DEVICE_NAME, null) ?: "Chrome",
            pairedAt = prefs.getLong(PAIRED_AT, 0L),
            active = prefs.getBoolean(ACTIVE, false),
            lastSyncAt = prefs.getLong(LAST_SYNC, 0L),
        )
    }

    fun sessionKey(): ByteArray? {
        val sealed = prefs.getString(SEALED_KEY, null) ?: return null
        return runCatching { sealer.decrypt(SyncCrypto.unb64(sealed)) }.getOrNull()
    }

    fun save(browser: LinkedBrowser, key: ByteArray) {
        prefs.edit()
            .putString(DEVICE_ID, browser.deviceId)
            .putString(DEVICE_NAME, browser.name)
            .putLong(PAIRED_AT, browser.pairedAt)
            .putBoolean(ACTIVE, browser.active)
            .putLong(LAST_SYNC, browser.lastSyncAt)
            .putString(SEALED_KEY, SyncCrypto.b64(sealer.encrypt(key)))
            .commit()
    }

    fun markActive() {
        prefs.edit().putBoolean(ACTIVE, true).commit()
    }

    fun markSynced(at: Long) {
        prefs.edit().putLong(LAST_SYNC, at).apply()
    }

    fun clear() {
        prefs.edit()
            .remove(DEVICE_ID)
            .remove(DEVICE_NAME)
            .remove(PAIRED_AT)
            .remove(ACTIVE)
            .remove(LAST_SYNC)
            .remove(SEALED_KEY)
            .commit()
        sealer.deleteKey()
    }

    private companion object {
        const val FILE = "ezzy_browser_link"
        const val KEY_ALIAS = "ezzy_browser_link_key"
        const val PHONE_ID = "phone_id"
        const val DEVICE_ID = "device_id"
        const val DEVICE_NAME = "device_name"
        const val PAIRED_AT = "paired_at"
        const val ACTIVE = "active"
        const val LAST_SYNC = "last_sync"
        const val SEALED_KEY = "sealed_key"
    }
}
