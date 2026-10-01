package com.ezzy.vault.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Just enough HTTP/1.1 for the extension to talk to: one request per connection, a JSON body in,
 * a JSON body out. Every byte that matters is already sealed by [SyncCrypto] before it gets here,
 * so this layer only has to move it — no TLS, no keep-alive, no chunked encoding.
 *
 * The CORS and Private Network Access headers are what let a page in Chrome reach a phone on the
 * local network at all; they grant nothing on their own, since every real request still has to
 * open with the session key.
 */
class LanHttpServer(
    private val handler: suspend (method: String, path: String, body: ByteArray) -> Reply,
) {

    data class Reply(val status: Int, val body: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: ServerSocket? = null

    val port: Int get() = socket?.localPort ?: -1
    val isRunning: Boolean get() = socket?.isClosed == false

    /** Binds the first free port from [PORTS]. Returns false if none could be opened. */
    fun start(): Boolean {
        if (isRunning) return true
        val bound = PORTS.firstNotNullOfOrNull { port ->
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            }.getOrNull()
        } ?: return false
        socket = bound
        scope.launch {
            while (!bound.isClosed) {
                val client = runCatching { bound.accept() }.getOrNull() ?: break
                launch { serve(client) }
            }
        }
        return true
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        scope.cancel()
    }

    private suspend fun serve(client: Socket) {
        client.use { conn ->
            runCatching {
                conn.soTimeout = 15_000
                val input = BufferedInputStream(conn.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')

                var contentLength = 0
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    val name = line.substringBefore(':').trim().lowercase()
                    if (name == "content-length") {
                        contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    }
                }
                if (contentLength > MAX_BODY) {
                    write(conn, Reply(413, """{"error":"too_large"}"""))
                    return
                }
                val body = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(body, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }

                val reply = if (method == "OPTIONS") Reply(204, "") else handler(method, path, body)
                write(conn, reply)
            }
        }
    }

    private fun write(conn: Socket, reply: Reply) {
        val payload = reply.body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ${reply.status} ${reason(reply.status)}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Access-Control-Allow-Private-Network: true\r\n")
            append("Access-Control-Max-Age: 600\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        conn.getOutputStream().apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(payload)
            flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.US_ASCII.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
            if (buffer.size() > 8_192) return null
        }
        return buffer.toString(Charsets.US_ASCII.name())
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        413 -> "Payload Too Large"
        else -> "Error"
    }

    companion object {
        /** A fixed, unusual range so the extension's network search only has a few ports to try. */
        val PORTS = 47821..47825
        private const val MAX_BODY = 2 * 1024 * 1024
    }
}
