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
        ServiceLocator.init(this)
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

    val eq = EqualizerManager()

    val music: YtMusic get() = YtMusic

    fun init(context: Context) {
        if (::settings.isInitialized) return
        settings = SettingsRepository(context)
        library = LibraryRepository(context)
        account = AccountStore(context)
        InnerTube.authHeaders = { account.cookieHeaders() }
        player = PlayerController(context, settings, library)
        downloads = DownloadManager(context, library, settings)
        backup = BackupManager(library, settings)
        localMusic = LocalMusicRepository(context)
        player.localFileResolver = { trackId -> downloads.existingFile(trackId) }
    }

    fun lastFm(): LastFm? {
        val config = settings.state.value
        if (config.lastFmApiKey.isBlank() || config.lastFmApiSecret.isBlank()) return null
        return LastFm(config.lastFmApiKey, config.lastFmApiSecret)
    }

    val innerTube: InnerTube get() = InnerTube
}
