package com.itzfallenme.katmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class SavedAccount(
    val id: String,
    val cookie: String,
    val name: String = "",
    val avatar: String = "",
    val addedAt: Long = 0L
)

data class AccountState(
    val signedIn: Boolean = false,
    val name: String = "",
    val avatar: String = "",
    val cookie: String = "",
    val accounts: List<SavedAccount> = emptyList(),
    val activeId: String = ""
)

/**
 * Stores the YouTube Music web sessions captured by the in-app sign-in WebView and
 * derives the SAPISIDHASH authorisation header the InnerTube endpoints expect.
 * Pattern: https://developers.google.com/youtube/v3/guides/auth/installed-apps
 *
 * Several sessions can be kept at once so a household or a work/personal pair can be
 * switched without signing in again; the active one supplies the request headers.
 */
class AccountStore(context: Context) {

    private val prefs = context.getSharedPreferences("katmusic-account", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private fun loadAccounts(): List<SavedAccount> {
        val stored = prefs.getString("accounts", null)
        if (!stored.isNullOrBlank()) {
            runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(SavedAccount.serializer()),
                    stored
                )
            }
                .getOrNull()
                ?.let { return it.filter { account -> account.cookie.isNotBlank() } }
        }
        // Migrate the single-session layout used before multi-account support.
        val legacyCookie = prefs.getString("cookie", "").orEmpty()
        if (legacyCookie.isBlank()) return emptyList()
        return listOf(
            SavedAccount(
                id = idFor(legacyCookie),
                cookie = legacyCookie,
                name = prefs.getString("name", "").orEmpty(),
                avatar = prefs.getString("avatar", "").orEmpty(),
                addedAt = System.currentTimeMillis()
            )
        )
    }

    private fun load(): AccountState {
        val accounts = loadAccounts()
        val storedActive = prefs.getString("activeId", "").orEmpty()
        val active = accounts.firstOrNull { it.id == storedActive } ?: accounts.firstOrNull()
        return AccountState(
            signedIn = active != null,
            name = active?.name.orEmpty(),
            avatar = active?.avatar.orEmpty(),
            cookie = active?.cookie.orEmpty(),
            accounts = accounts,
            activeId = active?.id.orEmpty()
        )
    }

    private fun persist(accounts: List<SavedAccount>, activeId: String) {
        prefs.edit()
            .putString(
                "accounts",
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedAccount.serializer()), accounts)
            )
            .putString("activeId", activeId)
            .putBoolean("signedIn", accounts.isNotEmpty())
            .apply()
        _state.value = load()
    }

    /** Adds a session, or refreshes the stored one when the same account signs in again. */
    fun saveSession(cookie: String, name: String = "", avatar: String = "") {
        if (cookie.isBlank()) return
        val id = idFor(cookie)
        val existing = loadAccounts()
        val previous = existing.firstOrNull { it.id == id }
        val account = SavedAccount(
            id = id,
            cookie = cookie,
            name = name.ifBlank { previous?.name.orEmpty() },
            avatar = avatar.ifBlank { previous?.avatar.orEmpty() },
            addedAt = previous?.addedAt ?: System.currentTimeMillis()
        )
        val updated = existing.filterNot { it.id == id } + account
        persist(updated, id)
    }

    fun updateProfile(name: String, avatar: String) {
        val activeId = _state.value.activeId
        if (activeId.isBlank()) return
        val updated = loadAccounts().map { account ->
            if (account.id == activeId) account.copy(name = name, avatar = avatar) else account
        }
        persist(updated, activeId)
    }

    /** Switches the active session, keeping every other account intact. */
    fun switchTo(accountId: String) {
        val accounts = loadAccounts()
        if (accounts.none { it.id == accountId }) return
        persist(accounts, accountId)
    }

    /** Removes one saved account; signing out of the last one clears the store. */
    fun remove(accountId: String) {
        val remaining = loadAccounts().filterNot { it.id == accountId }
        val nextActive = if (_state.value.activeId == accountId) {
            remaining.firstOrNull()?.id.orEmpty()
        } else {
            _state.value.activeId
        }
        if (remaining.isEmpty()) {
            prefs.edit().clear().apply()
            _state.value = AccountState()
            return
        }
        persist(remaining, nextActive)
    }

    /** Signs out of the active account only. */
    fun signOut() = remove(_state.value.activeId)

    /** Signs out of every account. */
    fun signOutAll() {
        prefs.edit().clear().apply()
        _state.value = AccountState()
    }

    /** Cookie and authorisation headers for the active account. */
    fun cookieHeaders(): Map<String, String> = buildHeaders(_state.value.cookie)

    companion object {
        /** Stable per-account id: the Google account id when present, else the SAPISID. */
        fun idFor(cookie: String): String {
            val parts = cookie.split(";").map { it.trim() }
            val googleId = parts.firstOrNull { it.startsWith("SID=") }
                ?.substringAfter("=")
                ?.substringBefore(".")
            val marker = parts.firstOrNull {
                it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=")
            }
            val raw = (marker ?: parts.firstOrNull { it.startsWith("SID=") })
                ?: return sha1(cookie).take(16)
            return sha1(raw).take(16)
        }

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
