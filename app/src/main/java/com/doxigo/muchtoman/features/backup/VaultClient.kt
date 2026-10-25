package com.doxigo.muchtoman.features.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The client half of the optional vault: PUT/GET/DELETE an **opaque, already-encrypted**
 * envelope, addressed by an unguessable id.
 *
 * There is no account, no email, no token and no request signing. The credential *is* the
 * recovery code, and it is used locally: [VaultRecoveryCode.parse] turns the 12 words into a
 * 32-byte key, [VaultRecoveryCode.VaultKey.id] is `SHA-256("…id…" ‖ key)`, and only that hash
 * ever reaches the network. The server therefore cannot derive the key from the id it stores,
 * and an operator who reads every URL sees a 64-character hex string and nothing else.
 *
 * **Plaintext never leaves this class.** [upload] refuses bytes that are not an [BackupFile]
 * envelope, so the one mistake this design is exposed to — wiring the exporter's output around
 * the encryptor — produces an error instead of a leak. The size cap is enforced before the
 * transport is touched, so an oversized payload costs no request.
 *
 * The transport is injected. The real one is [HttpVaultTransport]; the tests use a fake and
 * never open a socket.
 */
const val MAX_VAULT_BYTES: Int = 24 * 1024 * 1024
const val VAULT_PATH: String = "/v1/vault"

data class VaultRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
)

data class VaultResponse(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    /** Transport-level failure, already truncated; never contains a request or response body. */
    val error: String = "",
)

interface VaultTransport {
    fun send(request: VaultRequest): VaultResponse
}

sealed class VaultUpload {
    data object Ok : VaultUpload()

    /** No request was made: the bytes were not a backup envelope, or were over the cap. */
    data class Refused(val reason: String) : VaultUpload()

    data class Failed(val status: Int, val reason: String) : VaultUpload()
}

sealed class VaultDownload {
    data class Found(val envelope: ByteArray) : VaultDownload()

    data object Missing : VaultDownload()

    data class Failed(val status: Int, val reason: String) : VaultDownload()
}

class VaultClient(
    baseUrl: String,
    private val transport: VaultTransport,
    private val maxBytes: Int = MAX_VAULT_BYTES,
) {

    val baseUrl: String = normalizeBase(baseUrl)
        ?: throw IllegalArgumentException("vault address must be https (or http on this machine)")

    fun vaultUrl(key: VaultRecoveryCode.VaultKey): String {
        require(VAULT_ID.matches(key.id)) { "not a vault id" }
        return "$baseUrl$VAULT_PATH/${key.id}"
    }

    fun upload(key: VaultRecoveryCode.VaultKey, envelope: ByteArray): VaultUpload {
        if (envelope.size > maxBytes) return VaultUpload.Refused("too_large")
        if (!BackupFile.isEnvelope(envelope)) return VaultUpload.Refused("not_an_envelope")
        val response = transport.send(
            VaultRequest(
                method = "PUT",
                url = vaultUrl(key),
                headers = mapOf(
                    "Content-Type" to "application/octet-stream",
                    "Accept" to "application/octet-stream",
                    "Cache-Control" to "no-store",
                ),
                body = envelope,
            )
        )
        return if (response.status in 200..299) VaultUpload.Ok
        else VaultUpload.Failed(response.status, reasonOf(response))
    }

    fun download(key: VaultRecoveryCode.VaultKey): VaultDownload {
        val response = transport.send(
            VaultRequest(
                method = "GET",
                url = vaultUrl(key),
                headers = mapOf("Accept" to "application/octet-stream", "Cache-Control" to "no-store"),
            )
        )
        return when {
            response.status == 404 -> VaultDownload.Missing
            response.status !in 200..299 -> VaultDownload.Failed(response.status, reasonOf(response))
            response.body.isEmpty() -> VaultDownload.Failed(0, "empty_response")
            response.body.size > maxBytes -> VaultDownload.Failed(0, "too_large")
            // A vault that hands back something that is not this app's envelope is either a
            // wrong id or a compromised/misconfigured endpoint. Never hand it to the parser.
            !BackupFile.isEnvelope(response.body) -> VaultDownload.Failed(0, "invalid_envelope")
            else -> VaultDownload.Found(response.body)
        }
    }

    /** True when the server confirmed the delete, or the blob was already gone. */
    fun delete(key: VaultRecoveryCode.VaultKey): Boolean {
        val response = transport.send(
            VaultRequest(method = "DELETE", url = vaultUrl(key), headers = mapOf("Cache-Control" to "no-store"))
        )
        return response.status in 200..299 || response.status == 404
    }

    private fun reasonOf(response: VaultResponse): String =
        response.error.takeIf { it.isNotBlank() } ?: "http_${response.status}"

    private companion object {
        val VAULT_ID = Regex("^[a-f0-9]{64}$")

        fun normalizeBase(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val url = runCatching { URL(trimmed) }.getOrNull() ?: return null
            if (url.userInfo != null || url.query != null || url.ref != null) return null
            val https = url.protocol.equals("https", ignoreCase = true)
            val localHttp = url.protocol.equals("http", ignoreCase = true) &&
                url.host.lowercase() in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1", "[::1]")
            if (!https && !localHttp) return null
            return url.toExternalForm().trimEnd('/')
        }
    }
}

/**
 * The shipping transport. No logging of any kind: not the URL, not the status, not the
 * exception text beyond a short reason string. Redirects are off — a vault address is a
 * destination she configured, not something to be bounced to — and responses are capped.
 */
class HttpVaultTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxResponseBytes: Int = MAX_VAULT_BYTES,
) : VaultTransport {

    override fun send(request: VaultRequest): VaultResponse {
        var connection: HttpURLConnection? = null
        return try {
            val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                useCaches = false
                for ((name, value) in request.headers) setRequestProperty(name, value)
                if (request.body.isNotEmpty()) {
                    doOutput = true
                    setFixedLengthStreamingMode(request.body.size)
                }
            }
            connection = conn
            if (request.body.isNotEmpty()) {
                conn.outputStream.use { it.write(request.body) }
            }
            val status = conn.responseCode
            val stream: InputStream? = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { readLimited(it, maxResponseBytes) } ?: ByteArray(0)
            VaultResponse(status, body)
        } catch (e: Exception) {
            VaultResponse(0, ByteArray(0), e.javaClass.simpleName)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun readLimited(stream: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IllegalStateException("response too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
