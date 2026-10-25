package com.doxigo.muchtoman.features.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultClientTest {

    private class RecordingTransport(var response: VaultResponse = VaultResponse(204)) : VaultTransport {
        val requests = mutableListOf<VaultRequest>()
        override fun send(request: VaultRequest): VaultResponse {
            requests += request
            return response
        }
    }

    private val code = VaultRecoveryCode.encode(ByteArray(VaultRecoveryCode.ENTROPY_BYTES) { 3 })
    private val key = VaultRecoveryCode.parse(code)!!
    private val passphrase = "correct horse battery".toCharArray()

    private fun envelope(size: Int = 256): ByteArray {
        val plaintext = ByteArray(size) { (it % 251).toByte() }
        return BackupFile.create(plaintext, passphrase, iterations = 100)
    }

    @Test
    fun `upload builds a PUT to the hash of the recovery key`() {
        val transport = RecordingTransport()
        val client = VaultClient("https://vault.example", transport)
        val body = envelope()

        assertEquals(VaultUpload.Ok, client.upload(key, body))

        val request = transport.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("https://vault.example/v1/vault/${key.id}", request.url)
        assertEquals("application/octet-stream", request.headers["Content-Type"])
        assertEquals("no-store", request.headers["Cache-Control"])
        assertArrayEquals(body, request.body)
        assertTrue(request.url.matches(Regex("^https://vault\\.example/v1/vault/[a-f0-9]{64}$")))
    }

    @Test
    fun `no recovery word or passphrase reaches the request`() {
        val transport = RecordingTransport()
        val client = VaultClient("https://vault.example", transport)
        client.upload(key, envelope())

        val request = transport.requests.single()
        val visible = buildString {
            append(request.url)
            for ((name, value) in request.headers) append(' ').append(name).append('=').append(value)
        }
        for (word in code.split(' ')) {
            assertFalse("request leaked a recovery word", visible.contains(word))
        }
        assertFalse(visible.contains("recovery"))
        assertFalse(visible.contains("passphrase"))
        assertFalse(visible.contains(String(passphrase)))
    }

    @Test
    fun `plaintext is refused before the transport is touched`() {
        val transport = RecordingTransport()
        val client = VaultClient("https://vault.example", transport)
        val result = client.upload(key, """{"schemaVersion":1,"prefs":{}}""".toByteArray())

        assertEquals(VaultUpload.Refused("not_an_envelope"), result)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `the size cap is enforced before the transport is touched`() {
        val transport = RecordingTransport()
        val client = VaultClient("https://vault.example", transport, maxBytes = 64)
        val result = client.upload(key, envelope(size = 512))

        assertEquals(VaultUpload.Refused("too_large"), result)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `download returns the stored envelope`() {
        val body = envelope()
        val transport = RecordingTransport(VaultResponse(200, body))
        val client = VaultClient("https://vault.example", transport)

        val result = client.download(key)
        assertTrue(result is VaultDownload.Found)
        assertArrayEquals(body, (result as VaultDownload.Found).envelope)
        assertEquals("GET", transport.requests.single().method)
    }

    @Test
    fun `download maps missing, failure, and a non-envelope body`() {
        val missing = RecordingTransport(VaultResponse(404))
        assertEquals(VaultDownload.Missing, VaultClient("https://vault.example", missing).download(key))

        val failed = RecordingTransport(VaultResponse(500, "{}".toByteArray(), "server"))
        assertEquals(
            VaultDownload.Failed(500, "server"),
            VaultClient("https://vault.example", failed).download(key),
        )

        val wrongBody = RecordingTransport(VaultResponse(200, "not a backup".toByteArray()))
        assertEquals(
            VaultDownload.Failed(0, "invalid_envelope"),
            VaultClient("https://vault.example", wrongBody).download(key),
        )

        val empty = RecordingTransport(VaultResponse(200))
        assertEquals(
            VaultDownload.Failed(0, "empty_response"),
            VaultClient("https://vault.example", empty).download(key),
        )
    }

    @Test
    fun `delete treats already-gone as success`() {
        assertEquals(true, VaultClient("https://vault.example", RecordingTransport(VaultResponse(204))).delete(key))
        assertEquals(true, VaultClient("https://vault.example", RecordingTransport(VaultResponse(404))).delete(key))
        assertEquals(false, VaultClient("https://vault.example", RecordingTransport(VaultResponse(500))).delete(key))
    }

    @Test
    fun `a vault address must be https, with a loopback exception for development`() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultClient("http://vault.example", RecordingTransport())
        }
        assertThrows(IllegalArgumentException::class.java) {
            VaultClient("ftp://vault.example", RecordingTransport())
        }
        assertThrows(IllegalArgumentException::class.java) {
            VaultClient("https://user:pass@vault.example", RecordingTransport())
        }
        // The emulator / adb-reverse addresses the rest of this app already documents.
        assertEquals(
            "http://10.0.2.2:8788",
            VaultClient("http://10.0.2.2:8788/", RecordingTransport()).baseUrl,
        )
        assertEquals(
            "https://vault.example",
            VaultClient("https://vault.example/", RecordingTransport()).baseUrl,
        )
    }

    @Test
    fun `an id that is not a sha-256 hash is refused`() {
        val client = VaultClient("https://vault.example", RecordingTransport())
        val forged = VaultRecoveryCode.VaultKey(ByteArray(32), "not-a-hash")
        assertThrows(IllegalArgumentException::class.java) { client.vaultUrl(forged) }
    }

    @Test
    fun `a server rejection keeps its status for the screen to report`() {
        val transport = RecordingTransport(VaultResponse(413, ByteArray(0), "too_large"))
        val client = VaultClient("https://vault.example", transport)
        assertEquals(VaultUpload.Failed(413, "too_large"), client.upload(key, envelope()))
    }
}
