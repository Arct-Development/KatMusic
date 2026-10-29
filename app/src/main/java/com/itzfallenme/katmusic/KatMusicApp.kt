package com.itzfallenme.katmusic

import android.app.Application
import android.content.Context
import com.itzfallenme.katmusic.data.AccountStore
import com.itzfallenme.katmusic.data.BackupManager
import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.LocalMusicRepository
import com.itzfallenme.katmusic.data.LastFm
import com.itzfallenme.katmusic.data.LibraryRepository
import com.itzfallenme.katmusic.data.SettingsRepository
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.playback.DownloadManager
import com.itzfallenme.katmusic.playback.EqualizerManager
import com.itzfallenme.katmusic.playback.PlayerController

class KatMusicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.itzfallenme.katmusic.util.AppLog.init(this)
        com.itzfallenme.katmusic.data.CrashLog.install(this)
        ServiceLocator.init(this)
        com.itzfallenme.katmusic.util.AppLog.i("App", "application created, services ready")
    }

    companion object {
        lateinit var instance: KatMusicApp
            private set
    }
}

/** Tiny service locator; keeps the build free of annotation processors. */
object ServiceLocator {

    lateinit var settings: SettingsRepository
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var account: AccountStore
        private set
    lateinit var player: PlayerController
        private set
    lateinit var downloads: DownloadManager
        private set
    lateinit var backup: BackupManager
        private set
    lateinit var localMusic: LocalMusicRepository
        private set
    lateinit var discord: com.itzfallenme.katmusic.data.presence.DiscordPresenceManager
        private set
    lateinit var listenTogether: com.itzfallenme.katmusic.playback.ListenTogetherManager
        private set

    val eq = EqualizerManager()

    val music: YtMusic get() = YtMusic

    private val initLock = Any()
    @Volatile
    private var ready = false

    /**
     * Builds the services once. Called from the activity, the media service, the
     * widget's action callbacks and the sleep-timer receiver, which can arrive
     * on different threads; without the lock a second caller could slip past the
     * guard while the first was still assigning fields and then touch a lateinit
     * property that had not been set yet.
     */
    fun init(context: Context) {
        if (ready) return
        synchronized(initLock) {
            if (ready) return
            build(context)
            ready = true
        }
    }

    private fun build(context: Context) {
        settings = SettingsRepository(context)
        library = LibraryRepository(context)
        YtMusic.attach(context)
        account = AccountStore(context)
        InnerTube.authHeaders = { account.cookieHeaders() }
        player = PlayerController(context, settings, library)
        downloads = DownloadManager(context, library, settings)
        downloads.verifyDownloads()
        backup = BackupManager(library, settings)
        localMusic = LocalMusicRepository(context)
        player.localFileResolver = { trackId -> downloads.existingFile(trackId) }
        listenTogether = com.itzfallenme.katmusic.playback.ListenTogetherManager(
            context.applicationContext,
            player
        )
        discord = com.itzfallenme.katmusic.data.presence.DiscordPresenceManager(
            context.applicationContext,
            player,
            settings
        )
        runCatching { discord.start() }
    }

    /** Writes any queued library/settings changes to disk right away. */
    fun flush() {
        if (!::settings.isInitialized) return
        settings.flush()
        library.flush()
    }

    fun lastFm(): LastFm? {
        val config = settings.state.value
        if (config.lastFmApiKey.isBlank() || config.lastFmApiSecret.isBlank()) return null
        return LastFm(config.lastFmApiKey, config.lastFmApiSecret)
    }

    val innerTube: InnerTube get() = InnerTube
}
