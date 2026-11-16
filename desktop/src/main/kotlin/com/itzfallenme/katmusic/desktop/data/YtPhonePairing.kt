package com.itzfallenme.katmusic.desktop.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class YtPhonePairingPhase {
    WAITING,
    AWAITING_APPROVAL,
    APPROVED,
    RECEIVED,
    REJECTED,
    EXPIRED,
}

data class YtPhonePairingSnapshot(
    val phase: YtPhonePairingPhase,
    val deviceName: String? = null,
    val expiresAtMillis: Long,
)

/** The session payload decrypted from the phone's AES-GCM envelope. */
data class YtPhoneTransfer(
    val cookies: String,
    val accountName: String = "",
    val channelHandle: String = "",
    val photoUrl: String = "",
    val pageId: String = "",
)

/**
 * Desktop receiver for the Android → desktop YouTube session handoff.
 *
 * The server binds an ephemeral port and advertises private LAN addresses
 * only, the QR contains a one-use secret and a key, the phone must be
 * explicitly approved on the desktop, and the cookie header never leaves the
 * phone unencrypted (AES-256-GCM). This is the receiving half of the pairing
 * flow the Android app already ships; the wire protocol matches it exactly
 * (`katmusic-youtube-pair` v1).
 *
 * Lifecycle: [start] binds a random port and begins advertising; [close]
 * stops the server. A transfer is accepted only once, after [approve].
 */
class YtPhonePairing private constructor(
    private val key: ByteArray,
    private val code: String,
    private val expiresAtMillis: Long,
    private val ttlMillis: Long,
    private val clock: () -> Long,
    hosts: List<String>,
    private val server: HttpServer,
) : AutoCloseable {

    val hostLabel: String = hosts.joinToString(", ")
    val port: Int = server.address.port

    /**
     * JSON scanned from the QR. Field-for-field the payload
     * `YtDesktopPairingInvite.parse` accepts on Android.
     */
    val qrData: String = buildJsonObject {
        put("type", "katmusic-youtube-pair")
        put("v", PROTOCOL_VERSION)
        putJsonArray("hosts") { hosts.forEach { add(it) } }
        put("port", port)
        put("path", PATH)
        put("code", code)
        put("key", base64Url(key))
        put("expiresAt", expiresAtMillis)
    }.toString()

    private val lock = Any()
    private val _snapshot = MutableStateFlow(
        YtPhonePairingSnapshot(YtPhonePairingPhase.WAITING, null, expiresAtMillis),
    )
    val snapshot: StateFlow<YtPhonePairingSnapshot> = _snapshot.asStateFlow()

    private val _transfer = MutableStateFlow<YtPhoneTransfer?>(null)

    /** Set exactly once, after an approved phone sends its session. */
    val transfer: StateFlow<YtPhoneTransfer?> = _transfer.asStateFlow()

    private var pending: PendingRequest? = null

    @Volatile
    private var closed = false

    private val timer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "katmusic-pairing-expiry").apply { isDaemon = true }
    }

    init {
        server.createContext("/$PATH", ::handle)
        timer.schedule(
            {
                if (closed) return@schedule
                synchronized(lock) {
                    if (isExpired()) markExpiredLocked()
                }
            },
            ttlMillis.coerceAtLeast(1L),
            TimeUnit.MILLISECONDS,
        )
    }

    /** The desktop user confirms the phone shown in the dialog. */
    fun approve() {
        synchronized(lock) {
            if (closed) return
            if (isExpired()) {
                markExpiredLocked()
                return
            }
            val request = pending ?: return
            if (request.rejected || request.consumed) return
            request.approved = true
            _snapshot.value = snapshot(YtPhonePairingPhase.APPROVED, request.deviceName)
        }
    }

    /**
     * Declining is terminal for this QR. A fresh code is required for another
     * attempt, so an old phone request can never be silently reused.
     */
    fun reject() {
        synchronized(lock) {
            if (closed) return
            val request = pending ?: return
            if (request.consumed) return
            request.rejected = true
            _snapshot.value = snapshot(YtPhonePairingPhase.REJECTED, request.deviceName)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        timer.shutdownNow()
        runCatching { server.stop(0) }
        (server.executor as? ExecutorService)?.shutdownNow()
    }

    // ------------------------------------------------------------------
    // HTTP protocol
    // ------------------------------------------------------------------

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.sendResponseHeaders(204, -1)
                return
            }
            val path = exchange.requestURI.path.orEmpty()
            val prefix = "/$PATH"
            if (!path.startsWith(prefix)) {
                reply(exchange, 404, error("not_found"))
                return
            }
            val endpoint = path.removePrefix(prefix).trim('/')
            val query = queryParameters(exchange)
            when {
                endpoint == "request" && exchange.requestMethod == "GET" ->
                    requestPairing(exchange, query)
                endpoint == "status" && exchange.requestMethod == "GET" ->
                    pairingStatus(exchange, query)
                endpoint == "transfer" && exchange.requestMethod == "POST" ->
                    receiveTransfer(exchange, query)
                else -> reply(exchange, 404, error("not_found"))
            }
        } catch (_: Throwable) {
            runCatching { reply(exchange, 400, error("invalid_request")) }
        } finally {
            runCatching { exchange.close() }
        }
    }

    private fun requestPairing(exchange: HttpExchange, query: Map<String, String>) {
        synchronized(lock) {
            if (!validCode(query["code"])) {
                return reply(exchange, 410, error("expired"))
            }
            if (isExpired()) {
                markExpiredLocked()
                return reply(exchange, 410, error("expired"))
            }
            val device = safeLabel(query["device"], "Android")
            val clientId = safeLabel(query["client"], "phone")
            val existing = pending
            if (existing != null && !existing.rejected && existing.clientId != clientId) {
                return reply(exchange, 409, error("busy"))
            }
            val request = existing ?: PendingRequest(
                requestId = token(REQUEST_ID_BYTES),
                clientId = clientId,
                deviceName = device,
            ).also { pending = it }
            _snapshot.value = snapshot(
                if (request.approved) YtPhonePairingPhase.APPROVED
                else YtPhonePairingPhase.AWAITING_APPROVAL,
                request.deviceName,
            )
            reply(
                exchange,
                200,
                buildJsonObject {
                    put("v", PROTOCOL_VERSION)
                    put("status", "awaiting_approval")
                    put("requestId", request.requestId)
                    put("expiresAt", expiresAtMillis)
                },
            )
        }
    }

    private fun pairingStatus(exchange: HttpExchange, query: Map<String, String>) {
        synchronized(lock) {
            val request = pending
            if (!validCode(query["code"]) || request == null) {
                return reply(exchange, 404, error("not_found"))
            }
            if (isExpired()) {
                markExpiredLocked()
                return reply(exchange, 410, error("expired"))
            }
            if (query["requestId"] != request.requestId) {
                return reply(exchange, 404, error("not_found"))
            }
            if (request.rejected) {
                return reply(exchange, 403, error("rejected"))
            }
            reply(
                exchange,
                200,
                buildJsonObject {
                    put("status", if (request.approved) "approved" else "awaiting_approval")
                    put("desktop", "KatMusic Desktop")
                },
            )
        }
    }

    private fun receiveTransfer(exchange: HttpExchange, query: Map<String, String>) {
        synchronized(lock) {
            val request = pending
            if (!validCode(query["code"]) || request == null ||
                query["requestId"] != request.requestId
            ) {
                return reply(exchange, 404, error("not_found"))
            }
            if (isExpired()) {
                markExpiredLocked()
                return reply(exchange, 410, error("expired"))
            }
            if (request.rejected || !request.approved || request.consumed) {
                return reply(exchange, 403, error("not_approved"))
            }
            val raw = readBody(exchange)
            val body = Json.parseToJsonElement(raw) as? JsonObject
                ?: throw IllegalArgumentException("Invalid transfer body")
            val nonce = decodeBase64(body.string("nonce"))
            val ciphertext = decodeBase64(body.string("ciphertext"))
            if (nonce.size != GCM_NONCE_BYTES || ciphertext.size < GCM_TAG_BYTES + 1) {
                throw IllegalArgumentException("Invalid encrypted transfer")
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            val plain = cipher.doFinal(ciphertext)
            val payload = Json.parseToJsonElement(
                plain.toString(StandardCharsets.UTF_8),
            ) as? JsonObject ?: throw IllegalArgumentException("Invalid session payload")
            val cookies = payload.string("cookies").trim()
            if (cookies.isEmpty() || !hasSapisid(cookies)) {
                throw IllegalArgumentException("The Android session has no SAPISID cookie")
            }
            val received = YtPhoneTransfer(
                cookies = cookies,
                accountName = payload.string("accountName"),
                channelHandle = payload.string("channelHandle"),
                photoUrl = payload.string("photoUrl"),
                pageId = payload.string("pageId"),
            )
            request.consumed = true
            _transfer.value = received
            _snapshot.value = snapshot(YtPhonePairingPhase.RECEIVED, request.deviceName)
            reply(
                exchange,
                200,
                buildJsonObject { put("status", "received") },
            )
        }
    }

    private fun readBody(exchange: HttpExchange): String {
        val bytes = exchange.requestBody.use { it.readNBytes(MAX_BODY_BYTES + 1) }
        if (bytes.size > MAX_BODY_BYTES) {
            throw IllegalArgumentException("Body too large")
        }
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun queryParameters(exchange: HttpExchange): Map<String, String> {
        val raw = exchange.requestURI.rawQuery ?: return emptyMap()
        return raw.split('&')
            .filter { it.isNotBlank() }
            .associate { part ->
                val separator = part.indexOf('=')
                val name = if (separator >= 0) part.substring(0, separator) else part
                val value = if (separator >= 0) part.substring(separator + 1) else ""
                URLDecoder.decode(name, StandardCharsets.UTF_8) to
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            }
    }

    private fun reply(exchange: HttpExchange, status: Int, body: JsonObject) {
        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.responseHeaders.add("Pragma", "no-cache")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    // ------------------------------------------------------------------
    // State helpers
    // ------------------------------------------------------------------

    private fun isExpired(): Boolean = clock() >= expiresAtMillis

    private fun markExpiredLocked() {
        val current = _snapshot.value
        if (current.phase == YtPhonePairingPhase.RECEIVED ||
            current.phase == YtPhonePairingPhase.REJECTED ||
            current.phase == YtPhonePairingPhase.EXPIRED
        ) {
            return
        }
        _snapshot.value = snapshot(YtPhonePairingPhase.EXPIRED, pending?.deviceName)
    }

    private fun snapshot(phase: YtPhonePairingPhase, deviceName: String?): YtPhonePairingSnapshot =
        YtPhonePairingSnapshot(phase, deviceName, expiresAtMillis)

    private fun validCode(candidate: String?): Boolean {
        if (candidate == null || candidate.length != code.length) return false
        var difference = 0
        for (index in code.indices) {
            difference = difference or (candidate[index].code xor code[index].code)
        }
        return difference == 0
    }

    private fun hasSapisid(header: String): Boolean =
        header.contains("SAPISID=") ||
            header.contains("__Secure-3PAPISID=") ||
            header.contains("APISID=")

    private fun safeLabel(raw: String?, fallback: String): String {
        val value = (raw ?: "").replace(Regex("[^\\w .()\\-]"), "").trim()
        return (value.ifEmpty { fallback }).take(48)
    }

    private fun JsonObject.string(name: String): String =
        runCatching { this[name]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")

    private fun error(code: String): JsonObject = buildJsonObject { put("error", code) }

    private fun decodeBase64(value: String): ByteArray {
        if (value.isBlank()) return ByteArray(0)
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        return runCatching { Base64.getUrlDecoder().decode(padded) }.getOrDefault(ByteArray(0))
    }

    private class PendingRequest(
        val requestId: String,
        val clientId: String,
        val deviceName: String,
    ) {
        var approved: Boolean = false
        var rejected: Boolean = false
        var consumed: Boolean = false
    }

    companion object {
        /** Service path consumed by `YtDesktopPairingInvite.path` on Android. */
        const val PATH: String = "v1/yt-pair"
        const val PROTOCOL_VERSION: Int = 1
        const val DEFAULT_TTL_MILLIS: Long = 5 * 60_000L

        private const val KEY_BYTES = 32
        private const val CODE_BYTES = 9
        private const val REQUEST_ID_BYTES = 12
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val MAX_BODY_BYTES = 256 * 1024

        private val random = SecureRandom()

        /**
         * Binds a random port and starts advertising. [hosts] and [ttlMillis]
         * are injectable for tests; production callers use the defaults.
         */
        fun start(
            hosts: List<String>? = null,
            ttlMillis: Long = DEFAULT_TTL_MILLIS,
            clock: () -> Long = System::currentTimeMillis,
        ): YtPhonePairing {
            require(ttlMillis > 0) { "Pairing TTL must be positive" }
            val lanHosts = (hosts ?: lanHosts()).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            check(lanHosts.isNotEmpty()) {
                "No private IPv4 network found. Connect the computer to the same Wi-Fi as the phone."
            }
            val server = HttpServer.create(InetSocketAddress(0), 0)
            server.executor = Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "katmusic-pairing").apply { isDaemon = true }
            }
            val pairing = YtPhonePairing(
                key = ByteArray(KEY_BYTES).also(random::nextBytes),
                code = token(CODE_BYTES),
                expiresAtMillis = clock() + ttlMillis,
                ttlMillis = ttlMillis,
                clock = clock,
                hosts = lanHosts,
                server = server,
            )
            server.start()
            return pairing
        }

        private fun token(bytes: Int): String = base64Url(ByteArray(bytes).also(random::nextBytes))

        private fun base64Url(value: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value)

        private fun lanHosts(): List<String> {
            val addresses = sortedSetOf<String>()
            val interfaces = runCatching {
                Collections.list(NetworkInterface.getNetworkInterfaces())
            }.getOrDefault(emptyList())
            interfaces.forEach { networkInterface ->
                runCatching {
                    if (!networkInterface.isUp || networkInterface.isLoopback) return@runCatching
                    Collections.list(networkInterface.inetAddresses).forEach { address ->
                        val ipv4 = address as? Inet4Address ?: return@forEach
                        val host = ipv4.hostAddress ?: return@forEach
                        if (!ipv4.isLoopbackAddress && isPrivateIpv4(host)) addresses += host
                    }
                }
            }
            return addresses.toList()
        }

        private fun isPrivateIpv4(value: String): Boolean {
            val octets = value.split('.').map { it.toIntOrNull() }
            if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return false
            val first = octets[0]!!
            val second = octets[1]!!
            return first == 10 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254) ||
                (first == 100 && second in 64..127)
        }
    }
}
