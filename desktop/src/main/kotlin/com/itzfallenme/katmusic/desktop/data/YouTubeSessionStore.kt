package com.itzfallenme.katmusic.desktop.data

import dev.maxrave.pipepipe.extractor.ServiceList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

/**
 * The YouTube Music session handed over by the Android app through the QR
 * pairing flow. Cookies are the same `Cookie` header the phone uses; the
 * PipePipe extractor turns them into authenticated requests (and derives the
 * `Authorization: SAPISIDHASH …` header) when they are assigned to
 * `ServiceList.YouTube.tokens` — the hook SimpMusic's desktop build uses too.
 */
@Serializable
data class YouTubeSession(
    val cookies: String = "",
    val accountName: String = "",
    val channelHandle: String = "",
    val photoUrl: String = "",
    val pageId: String = "",
    val savedAtMillis: Long = 0L,
) {
    /** A session is only useful when it carries a SAPISID-family token. */
    val isUsable: Boolean
        get() = cookies.isNotBlank() && SAPISID_KEYS.any { cookies.contains("$it=") }

    companion object {
        private val SAPISID_KEYS = listOf("SAPISID", "__Secure-3PAPISID", "APISID")
    }
}

/**
 * Desktop YouTube account storage at `~/KatMusic/youtube-session.json`.
 *
 * The file is restricted to the current user where POSIX permissions exist,
 * and every load/save/clear immediately re-applies the cookie header to the
 * PipePipe extractor, so playback and downloads use the signed-in session
 * without a restart.
 */
class YouTubeSessionStore(
    baseDir: Path = Paths.get(System.getProperty("user.home"), "KatMusic"),
    private val applyToExtractors: (String?) -> Unit = { cookies ->
        ServiceList.YouTube.tokens = cookies.orEmpty()
    },
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = baseDir.resolve("youtube-session.json").toFile()

    private val _session = MutableStateFlow(load())
    val session: StateFlow<YouTubeSession?> = _session.asStateFlow()

    init {
        applySafely(_session.value?.cookies)
    }

    /** Cookie header the extractors should send; null when signed out. */
    fun cookieHeader(): String? = _session.value?.cookies?.takeIf { it.isNotBlank() }

    fun save(session: YouTubeSession) {
        val clean = session.copy(
            cookies = session.cookies.trim(),
            savedAtMillis = System.currentTimeMillis(),
        )
        require(clean.isUsable) { "The transferred session has no SAPISID cookie" }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(YouTubeSession.serializer(), clean))
        restrictToOwner(file.toPath())
        _session.value = clean
        applySafely(clean.cookies)
    }

    fun clear() {
        runCatching { Files.deleteIfExists(file.toPath()) }
        _session.value = null
        applySafely(null)
    }

    private fun load(): YouTubeSession? = runCatching {
        if (!file.isFile) return@runCatching null
        json.decodeFromString(YouTubeSession.serializer(), file.readText())
            .takeIf { it.isUsable }
    }.getOrNull()

    /**
     * Applying tokens touches extractor statics; a failure there must not
     * crash settings or startup. The resolver also applies the header on
     * every extraction, so a miss here is self-healing.
     */
    private fun applySafely(cookies: String?) {
        runCatching { applyToExtractors(cookies) }
    }

    private fun restrictToOwner(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )
        }
    }
}
