package com.itzfallenme.katmusic.data.ytmusic

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

data class YtDesktopPairingInvite(
    val hosts: List<String>,
    val port: Int,
    val path: String,
    val code: String,
    val key: ByteArray,
    val expiresAtMillis: Long,
) {
    companion object {
        fun parse(raw: String): YtDesktopPairingInvite {
            val json = runCatching { JSONObject(raw.trim()) }
                .getOrElse { throw YtPairingException("That is not a KatMusic pairing QR") }
            if (json.optString("type") != "katmusic-youtube-pair" || json.optInt("v") != 1) {
                throw YtPairingException("This QR is not a KatMusic YouTube login code")
            }
            val hosts = mutableListOf<String>()
            val hostArray = json.optJSONArray("hosts")
            if (hostArray != null) {
                for (index in 0 until hostArray.length()) {
                    hostArray.optString(index).trim().takeIf { it.isNotEmpty() }?.let(hosts::add)
                }
            }
            json.optString("host").trim().takeIf { it.isNotEmpty() }?.let(hosts::add)
            val validHosts = hosts.distinct().filter(::isPrivateIpv4)
            val port = json.optInt("port", -1)
            val path = json.optString("path", "/v1/yt-pair")
                .trim()
                .ifBlank { "/v1/yt-pair" }
            val code = json.optString("code").trim()
            val keyText = json.optString("key").trim()
            val expiresAt = json.optLong("expiresAt", 0L)
            val key = decodeBase64(keyText)
            if (validHosts.isEmpty() || port !in 1..65535 || code.isEmpty() || key.size != 32 ||
                expiresAt <= System.currentTimeMillis() - 60_000L) {
                throw YtPairingException("This pairing code is missing data or has expired")
            }
            return YtDesktopPairingInvite(
                hosts = validHosts,
                port = port,
                path = if (path.startsWith('/')) path else "/$path",
                code = code,
                key = key,
                expiresAtMillis = expiresAt,
            )
        }

        private fun decodeBase64(value: String): ByteArray {
            if (value.isBlank()) return byteArrayOf()
            val padded = value + "=".repeat((4 - value.length % 4) % 4)
            return runCatching {
                Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
            }.getOrDefault(byteArrayOf())
        }

        private fun isPrivateIpv4(host: String): Boolean {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            if (address !is Inet4Address || address.hostAddress != host) return false
            val bytes = address.address.map { it.toInt() and 0xFF }
            if (bytes.size != 4) return false
            val first = bytes[0]
            val second = bytes[1]
            return first == 10 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254) ||
                (first == 100 && second in 64..127)
        }
    }
}

data class YtDesktopPairingConnection(
    val invite: YtDesktopPairingInvite,
    val baseUrl: String,
    val requestId: String,
)

enum class YtDesktopPairingStatus {
    AwaitingApproval,
    Approved,
}

class YtPairingException(message: String) : IOException(message)

class YtDesktopPairingClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun request(
        invite: YtDesktopPairingInvite,
        deviceName: String,
    ): YtDesktopPairingConnection = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (host in invite.hosts) {
            val baseUrl = "http://$host:${invite.port}"
            try {
                val response = executeGet(
                    endpoint(invite, baseUrl, "request")
                        .newBuilder()
                        .addQueryParameter("code", invite.code)
                        .addQueryParameter("device", deviceName.take(48))
                        .addQueryParameter("client", UUID.randomUUID().toString())
                        .build(),
                )
                val requestId = response.optString("requestId").takeIf { it.isNotBlank() }
                    ?: throw YtPairingException("The desktop returned an invalid pairing request")
                return@withContext YtDesktopPairingConnection(invite, baseUrl, requestId)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw YtPairingException(
            lastError?.message ?: "Could not reach KatMusic Desktop. Check the Wi-Fi connection.",
        )
    }

    suspend fun status(connection: YtDesktopPairingConnection): YtDesktopPairingStatus =
        withContext(Dispatchers.IO) {
            val body = executeGet(
                endpoint(connection.invite, connection.baseUrl, "status")
                    .newBuilder()
                    .addQueryParameter("code", connection.invite.code)
                    .addQueryParameter("requestId", connection.requestId)
                    .build(),
            )
            when (body.optString("status")) {
                "approved" -> YtDesktopPairingStatus.Approved
                "awaiting_approval" -> YtDesktopPairingStatus.AwaitingApproval
                else -> throw YtPairingException("The desktop rejected this pairing request")
            }
        }

    suspend fun send(
        connection: YtDesktopPairingConnection,
        envelope: JSONObject,
    ) = withContext(Dispatchers.IO) {
        val url = endpoint(connection.invite, connection.baseUrl, "transfer")
            .newBuilder()
            .addQueryParameter("code", connection.invite.code)
            .addQueryParameter("requestId", connection.requestId)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(envelope.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw YtPairingException(
                    if (response.code == 410) "The desktop pairing code expired" else "The desktop did not accept the session",
                )
            }
        }
    }

    private fun executeGet(url: HttpUrl): JSONObject {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw YtPairingException(
                    when (response.code) {
                        410 -> "The desktop pairing code expired"
                        403 -> "The desktop declined this phone"
                        409 -> "The desktop is already pairing with another phone"
                        else -> "The desktop could not be reached"
                    },
                )
            }
            return JSONObject(text)
        }
    }

    private fun endpoint(
        invite: YtDesktopPairingInvite,
        baseUrl: String,
        endpoint: String,
    ): HttpUrl {
        val builder = baseUrl.toHttpUrl().newBuilder()
        invite.path.trim('/').split('/').filter { it.isNotBlank() }.forEach(builder::addPathSegment)
        return builder.addPathSegment(endpoint).build()
    }
}
