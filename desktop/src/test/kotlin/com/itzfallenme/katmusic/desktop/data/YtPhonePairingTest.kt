package com.itzfallenme.katmusic.desktop.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end pairing protocol tests against a real loopback HTTP server: the
 * exact wire shape the Android `YtDesktopPairingClient` sends.
 */
class YtPhonePairingTest {

    private data class Reply(val code: Int, val body: String) {
        val json: JsonObject get() = Json.parseToJsonElement(body).jsonObject
    }

    private data class Invite(val port: Int, val code: String, val key: ByteArray)

    private fun invite(pairing: YtPhonePairing): Invite {
        val qr = Json.parseToJsonElement(pairing.qrData).jsonObject
        val key = Base64.getUrlDecoder().decode(qr["key"]!!.jsonPrimitive.content)
        return Invite(
            port = qr["port"]!!.jsonPrimitive.int,
            code = qr["code"]!!.jsonPrimitive.content,
            key = key,
        )
    }

    private fun get(port: Int, path: String, query: Map<String, String> = emptyMap()): Reply =
        request(port, path, query, method = "GET", body = null)

    private fun post(port: Int, path: String, query: Map<String, String>, body: String): Reply =
        request(port, path, query, method = "POST", body = body)

    private fun request(
        port: Int,
        path: String,
        query: Map<String, String>,
        method: String,
        body: String?,
    ): Reply {
        val encoded = query.entries.joinToString("&") { (name, value) ->
            "${URLEncoder.encode(name, StandardCharsets.UTF_8)}=" +
                URLEncoder.encode(value, StandardCharsets.UTF_8)
        }
        val url = "http://127.0.0.1:$port/$path" + if (encoded.isEmpty()) "" else "?$encoded"
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
        connection.disconnect()
        return Reply(code, text)
    }

    private fun encrypt(key: ByteArray, payload: String): String {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce),
        )
        val ciphertext = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return buildJsonObject {
            put("nonce", encoder.encodeToString(nonce))
            put("ciphertext", encoder.encodeToString(ciphertext))
        }.toString()
    }

    @Test
    fun `qr payload matches the Android invite shape`() {
        YtPhonePairing.start(hosts = listOf("127.0.0.1"), ttlMillis = 60_000).use { pairing ->
            val qr = Json.parseToJsonElement(pairing.qrData).jsonObject
            assertEquals("katmusic-youtube-pair", qr["type"]!!.jsonPrimitive.content)
            assertEquals(1, qr["v"]!!.jsonPrimitive.int)
            assertEquals(
                listOf("127.0.0.1"),
                qr["hosts"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
            assertEquals("v1/yt-pair", qr["path"]!!.jsonPrimitive.content)
            assertTrue(qr["expiresAt"]!!.jsonPrimitive.long > System.currentTimeMillis())
            assertEquals(32, invite(pairing).key.size)
            assertTrue(pairing.port in 1..65535)
        }
    }

    @Test
    fun `request approval and encrypted transfer completes once`() {
        YtPhonePairing.start(hosts = listOf("127.0.0.1"), ttlMillis = 60_000).use { pairing ->
            val invite = invite(pairing)

            val request = get(
                invite.port,
                "${YtPhonePairing.PATH}/request",
                mapOf("code" to invite.code, "device" to "Pixel 8", "client" to "client-a"),
            )
            assertEquals(200, request.code)
            assertEquals("awaiting_approval", request.json["status"]!!.jsonPrimitive.content)
            val requestId = request.json["requestId"]!!.jsonPrimitive.content
            assertEquals(YtPhonePairingPhase.AWAITING_APPROVAL, pairing.snapshot.value.phase)
            assertEquals("Pixel 8", pairing.snapshot.value.deviceName)

            val statusQuery = mapOf("code" to invite.code, "requestId" to requestId)
            assertEquals(
                "awaiting_approval",
                get(invite.port, "${YtPhonePairing.PATH}/status", statusQuery)
                    .json["status"]!!.jsonPrimitive.content,
            )

            val payload = buildJsonObject {
                put("cookies", "SAPISID=abc123; __Secure-3PAPISID=abc123; SID=session")
                put("accountName", "Test Account")
                put("channelHandle", "@test")
                put("photoUrl", "https://example.com/avatar.jpg")
                put("pageId", "page-1")
            }.toString()

            // Not approved yet: the encrypted envelope must be refused.
            val early = post(
                invite.port,
                "${YtPhonePairing.PATH}/transfer",
                statusQuery,
                encrypt(invite.key, payload),
            )
            assertEquals(403, early.code)
            assertNull(pairing.transfer.value)

            pairing.approve()
            assertEquals(YtPhonePairingPhase.APPROVED, pairing.snapshot.value.phase)
            assertEquals(
                "approved",
                get(invite.port, "${YtPhonePairing.PATH}/status", statusQuery)
                    .json["status"]!!.jsonPrimitive.content,
            )

            val transfer = post(
                invite.port,
                "${YtPhonePairing.PATH}/transfer",
                statusQuery,
                encrypt(invite.key, payload),
            )
            assertEquals(200, transfer.code)
            assertEquals("received", transfer.json["status"]!!.jsonPrimitive.content)
            assertEquals(YtPhonePairingPhase.RECEIVED, pairing.snapshot.value.phase)

            val received = assertNotNull(pairing.transfer.value)
            assertTrue(received.cookies.startsWith("SAPISID=abc123"))
            assertEquals("Test Account", received.accountName)
            assertEquals("@test", received.channelHandle)
            assertEquals("https://example.com/avatar.jpg", received.photoUrl)
            assertEquals("page-1", received.pageId)

            // Replay of the same envelope is refused.
            val replay = post(
                invite.port,
                "${YtPhonePairing.PATH}/transfer",
                statusQuery,
                encrypt(invite.key, payload),
            )
            assertEquals(403, replay.code)
        }
    }

    @Test
    fun `a second phone is refused while a request is pending`() {
        YtPhonePairing.start(hosts = listOf("127.0.0.1"), ttlMillis = 60_000).use { pairing ->
            val invite = invite(pairing)
            val first = get(
                invite.port,
                "${YtPhonePairing.PATH}/request",
                mapOf("code" to invite.code, "device" to "Phone A", "client" to "client-a"),
            )
            assertEquals(200, first.code)
            val second = get(
                invite.port,
                "${YtPhonePairing.PATH}/request",
                mapOf("code" to invite.code, "device" to "Phone B", "client" to "client-b"),
            )
            assertEquals(409, second.code)
            assertEquals("busy", second.json["error"]!!.jsonPrimitive.content)

            pairing.reject()
            assertEquals(YtPhonePairingPhase.REJECTED, pairing.snapshot.value.phase)
            val status = get(
                invite.port,
                "${YtPhonePairing.PATH}/status",
                mapOf(
                    "code" to invite.code,
                    "requestId" to first.json["requestId"]!!.jsonPrimitive.content,
                ),
            )
            assertEquals(403, status.code)
        }
    }

    @Test
    fun `wrong code and expiry are rejected`() {
        YtPhonePairing.start(hosts = listOf("127.0.0.1"), ttlMillis = 150).use { pairing ->
            val invite = invite(pairing)
            val wrong = get(
                invite.port,
                "${YtPhonePairing.PATH}/request",
                mapOf("code" to "not-the-code", "device" to "Phone", "client" to "client-a"),
            )
            assertEquals(410, wrong.code)

            Thread.sleep(300)
            val expired = get(
                invite.port,
                "${YtPhonePairing.PATH}/request",
                mapOf("code" to invite.code, "device" to "Phone", "client" to "client-a"),
            )
            assertEquals(410, expired.code)
            assertEquals("expired", expired.json["error"]!!.jsonPrimitive.content)
            assertEquals(YtPhonePairingPhase.EXPIRED, pairing.snapshot.value.phase)
        }
    }
}
