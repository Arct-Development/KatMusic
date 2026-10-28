package com.itzfallenme.katmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class AccountState(
    val signedIn: Boolean = false,
    val name: String = "",
    val avatar: String = "",
    val cookie: String = ""
)

/**
 * Stores the YouTube Music web session captured by the in-app sign-in WebView and
 * derives the SAPISIDHASH authorisation header the InnerTube endpoints expect.
 * Pattern: https://developers.google.com/youtube/v3/guides/auth/installed-apps
 */
class AccountStore(context: Context) {

    private val prefs = context.getSharedPreferences("katmusic-account", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private fun load(): AccountState = AccountState(
        signedIn = prefs.getBoolean("signedIn", false),
        name = prefs.getString("name", "").orEmpty(),
        avatar = prefs.getString("avatar", "").orEmpty(),
        cookie = prefs.getString("cookie", "").orEmpty()
    )

    fun saveSession(cookie: String, name: String = "", avatar: String = "") {
        prefs.edit()
            .putBoolean("signedIn", cookie.isNotBlank())
            .putString("cookie", cookie)
            .putString("name", name)
            .putString("avatar", avatar)
            .apply()
        _state.value = load()
    }

    fun updateProfile(name: String, avatar: String) {
        prefs.edit().putString("name", name).putString("avatar", avatar).apply()
        _state.value = load()
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _state.value = AccountState()
    }

    /** All cookies for a domain, as the WebView's cookie jar sees them. */
    fun cookieHeaders(): Map<String, String> = buildHeaders(_state.value.cookie)

    companion object {
        fun buildHeaders(rawCookie: String): Map<String, String> {
            if (rawCookie.isBlank()) return emptyMap()
            val headers = mutableMapOf<String, String>()
            headers["Cookie"] = rawCookie
            headers["X-Origin"] = "https://music.youtube.com"
            val sapisid = rawCookie.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("SAPISID=") }
                ?.substringAfter("=")
                ?.takeIf { it.isNotBlank() }
            if (sapisid != null) {
                val timestamp = System.currentTimeMillis() / 1000
                val hash = sha1("$timestamp $sapisid https://music.youtube.com")
                headers["Authorization"] = "SAPISIDHASH ${timestamp}_$hash"
            }
            return headers
        }

        private fun sha1(value: String): String =
            MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
