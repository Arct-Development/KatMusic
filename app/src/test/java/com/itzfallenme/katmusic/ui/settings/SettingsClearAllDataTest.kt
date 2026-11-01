package com.itzfallenme.katmusic.ui.screens.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.itzfallenme.katmusic.data.download.TrackDownloadManager
import com.itzfallenme.katmusic.data.local.EqualizerSettings
import com.itzfallenme.katmusic.data.local.MiscSettings
import com.itzfallenme.katmusic.data.local.ScrobblerSettings
import com.itzfallenme.katmusic.data.local.SessionData
import com.itzfallenme.katmusic.data.local.SessionPreferences
import com.itzfallenme.katmusic.data.local.db.RecommendationExclusionEntity
import com.itzfallenme.katmusic.data.model.AuthState
import com.itzfallenme.katmusic.data.repository.ThemeUiState
import com.itzfallenme.katmusic.data.update.UpdateInfo
import com.itzfallenme.katmusic.playback.LoudnessSettings
import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.MusicPlayerState
import com.itzfallenme.katmusic.playback.PlayableTrack
import com.itzfallenme.katmusic.data.ytmusic.YtConnection
import com.itzfallenme.katmusic.data.ytmusic.YtSyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Clear-all-data must stop the live player and finish download deletion before
 * session preferences are wiped. Signing out first used to cancel this work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class SettingsClearAllDataTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val session = MutableStateFlow(SessionData(apiKey = "secret-key", apiSecret = "secret-secret"))
    private val playerState = MutableStateFlow(MusicPlayerState())

    private val authRepository = mockk<com.itzfallenme.katmusic.data.repository.AuthRepository>(relaxed = true)
    private val authCallback = mockk<com.itzfallenme.katmusic.data.repository.LastFmAuthCallbackCoordinator>(relaxed = true)
    private val homeRepository = mockk<com.itzfallenme.katmusic.data.repository.HomeRepository>(relaxed = true)
    private val sessionPreferences = mockk<SessionPreferences>(relaxed = true)
    private val themeRepository = mockk<com.itzfallenme.katmusic.data.repository.ThemeRepository>(relaxed = true)
    private val settingsPreferences = mockk<com.itzfallenme.katmusic.data.local.SettingsPreferences>(relaxed = true)
    private val audioEngine = mockk<dagger.Lazy<com.itzfallenme.katmusic.playback.NativeAudioEngine>>(relaxed = true)
    private val generateRepository = mockk<com.itzfallenme.katmusic.data.generate.GenerateRepository>(relaxed = true)
    private val discoverRepository = mockk<com.itzfallenme.katmusic.data.discover.DiscoverRepository>(relaxed = true)
    private val backupRepository = mockk<com.itzfallenme.katmusic.data.backup.BackupRepository>(relaxed = true)
    private val playlistRepository = mockk<com.itzfallenme.katmusic.data.playlist.PlaylistRepository>(relaxed = true)
    private val musicPlayer = mockk<MusicPlayer>(relaxed = true)
    private val downloadManager = mockk<TrackDownloadManager>(relaxed = true)
    private val fileExportHelper = mockk<com.itzfallenme.katmusic.util.FileExportHelper>(relaxed = true)
    private val scrobblerPreferences = mockk<com.itzfallenme.katmusic.data.local.ScrobblerPreferences>(relaxed = true)
    private val discordPresencePreferences = mockk<com.itzfallenme.katmusic.data.presence.DiscordPresencePreferences>(relaxed = true)
    private val equalizerPreferences = mockk<com.itzfallenme.katmusic.data.local.EqualizerPreferences>(relaxed = true)
    private val loudnessPrefs = mockk<com.itzfallenme.katmusic.playback.LoudnessPrefs>(relaxed = true)
    private val autoEqController = mockk<com.itzfallenme.katmusic.data.autoeq.AutoEqController>(relaxed = true)
    private val searchHistoryRepository = mockk<com.itzfallenme.katmusic.data.search.SearchHistoryRepository>(relaxed = true)
    private val localMusicLibrary = mockk<com.itzfallenme.katmusic.data.localmusic.LocalMusicLibrary>(relaxed = true)
    private val appDatabase = mockk<com.itzfallenme.katmusic.data.local.db.AppDatabase>(relaxed = true)
    private val ytAuthManager = mockk<com.itzfallenme.katmusic.data.ytmusic.YtMusicAuthManager>(relaxed = true)
    private val ytMusicSyncManager = mockk<com.itzfallenme.katmusic.data.ytmusic.YtMusicSyncManager>(relaxed = true)
    private val ytMusicPreferences = mockk<com.itzfallenme.katmusic.data.ytmusic.YtMusicPreferences>(relaxed = true)
    private val ytMusicLibraryManager = mockk<com.itzfallenme.katmusic.data.ytmusic.YtMusicLibraryManager>(relaxed = true)
    private val downloadedTrackDao = mockk<com.itzfallenme.katmusic.data.local.db.DownloadedTrackDao>(relaxed = true)
    private val podcastProgressStore = mockk<com.itzfallenme.katmusic.data.podcast.PodcastProgressStore>(relaxed = true)
    private val appLocaleManager = mockk<com.itzfallenme.katmusic.util.AppLocaleManager>(relaxed = true)
    private val playlistImportManager = mockk<com.itzfallenme.katmusic.data.playlist.PlaylistImportManager>(relaxed = true)
    private val innerTube = mockk<com.itzfallenme.katmusic.data.music.StreamApi>(relaxed = true)
    private val appUpdateManager = mockk<com.itzfallenme.katmusic.data.update.AppUpdateManager>(relaxed = true)
    private val spotifyPreferences = mockk<com.itzfallenme.katmusic.data.spotify.SpotifyPreferences>(relaxed = true)

    private lateinit var viewModel: SettingsViewModel
    private val order = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { authRepository.authState } returns MutableStateFlow(AuthState.SignedOut)
        every { authCallback.pendingToken } returns MutableStateFlow<String?>(null)
        every { sessionPreferences.session } returns session
        every { themeRepository.uiState } returns MutableStateFlow(mockk<ThemeUiState>(relaxed = true))
        every { settingsPreferences.settings } returns MutableStateFlow(MiscSettings())
        every { playlistRepository.playlists } returns MutableStateFlow(emptyList())
        every { scrobblerPreferences.settings } returns MutableStateFlow(ScrobblerSettings())
        every { discordPresencePreferences.enabled } returns MutableStateFlow(true)
        every { equalizerPreferences.settings } returns MutableStateFlow(EqualizerSettings())
        every { loudnessPrefs.settings } returns MutableStateFlow(LoudnessSettings())
        every { ytAuthManager.connection } returns MutableStateFlow(YtConnection.DISCONNECTED)
        every { ytMusicSyncManager.state } returns MutableStateFlow(YtSyncState.Idle)
        every { ytMusicPreferences.syncEnabled } returns MutableStateFlow(false)
        every { ytMusicPreferences.historySyncEnabled } returns MutableStateFlow(true)
        every { ytMusicPreferences.lastSyncAt } returns MutableStateFlow(0L)
        every { ytMusicPreferences.syncedPlaylistIds } returns MutableStateFlow<Set<Long>?>(null)
        every { ytMusicPreferences.hiddenLibraryPlaylistIds } returns MutableStateFlow(emptySet())
        every { ytMusicLibraryManager.accountPlaylists } returns MutableStateFlow(emptyList())
        every { downloadedTrackDao.count() } returns MutableStateFlow(0)
        every { downloadedTrackDao.totalBytes() } returns MutableStateFlow<Long?>(0L)
        every { generateRepository.observeRecommendationExclusions() } returns
            MutableStateFlow(emptyList<RecommendationExclusionEntity>())
        every { appUpdateManager.updateInfo } returns MutableStateFlow(UpdateInfo())
        every { musicPlayer.state } returns playerState
        every { musicPlayer.stopAndClear(clearSession = true) } answers { order += "player" }
        coEvery { downloadManager.clearAllDownloads() } coAnswers { order += "downloads" }
        coEvery { localMusicLibrary.clearAll() } coAnswers { order += "local-music" }
        every { searchHistoryRepository.clear() } answers { order += "search-history" }
        coEvery { appDatabase.clearListeningHistoryAndCaches() } coAnswers { order += "db-caches" }
        coEvery { autoEqController.clearAll() } coAnswers { order += "autoeq" }
        coEvery { loudnessPrefs.clearAll() } coAnswers { order += "loudness" }
        coEvery { spotifyPreferences.clearConnection() } coAnswers { order += "spotify" }
        coEvery { ytAuthManager.signOut() } coAnswers { order += "youtube" }
        coEvery { discoverRepository.clearRecommendationExclusions() } coAnswers { order += "exclusions" }
        coEvery { podcastProgressStore.clear() } coAnswers { order += "podcast-progress" }
        coEvery { playlistRepository.clearAll() } coAnswers { order += "playlists" }
        coEvery { sessionPreferences.clearAll() } coAnswers { order += "session" }
        viewModel = SettingsViewModel(
            authRepository = authRepository,
            authCallback = authCallback,
            homeRepository = homeRepository,
            sessionPreferences = sessionPreferences,
            themeRepository = themeRepository,
            settingsPreferences = settingsPreferences,
            audioEngine = audioEngine,
            generateRepository = generateRepository,
            discoverRepository = discoverRepository,
            backupRepository = backupRepository,
            playlistRepository = playlistRepository,
            musicPlayer = musicPlayer,
            downloadManager = downloadManager,
            fileExportHelper = fileExportHelper,
            scrobblerPreferences = scrobblerPreferences,
            discordPresencePreferences = discordPresencePreferences,
            equalizerPreferences = equalizerPreferences,
            loudnessPrefs = loudnessPrefs,
            autoEqController = autoEqController,
            searchHistoryRepository = searchHistoryRepository,
            localMusicLibrary = localMusicLibrary,
            appDatabase = appDatabase,
            ytAuthManager = ytAuthManager,
            ytMusicSyncManager = ytMusicSyncManager,
            ytMusicPreferences = ytMusicPreferences,
            ytMusicLibraryManager = ytMusicLibraryManager,
            downloadedTrackDao = downloadedTrackDao,
            podcastProgressStore = podcastProgressStore,
            appLocaleManager = appLocaleManager,
            playlistImportManager = playlistImportManager,
            innerTube = innerTube,
            appUpdateManager = appUpdateManager,
            spotifyPreferences = spotifyPreferences,
            context = RuntimeEnvironment.getApplication(),
        )
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun confirmClearAllData_fromPlayingPausedAndEmpty_stopsBeforeCleanupAndClearsSessionLast() {
        val states = listOf(
            "playing" to MusicPlayerState(
                isPlaying = true,
                current = PlayableTrack("Song", "Artist"),
                queue = listOf(PlayableTrack("Song", "Artist")),
            ),
            "paused" to MusicPlayerState(
                isPlaying = false,
                current = PlayableTrack("Song", "Artist"),
                queue = listOf(PlayableTrack("Song", "Artist")),
            ),
            "empty" to MusicPlayerState(),
        )
        states.forEach { (label, state) ->
            order.clear()
            playerState.value = state
            var completed = 0
            viewModel.confirmClearAllData { completed++ }
            assertThat(order).containsExactly(
                "player",
                "downloads",
                "local-music",
                "search-history",
                "db-caches",
                "autoeq",
                "loudness",
                "exclusions",
                "podcast-progress",
                "playlists",
                "spotify",
                "youtube",
                "session",
            ).inOrder()
            assertThat(completed).isEqualTo(1)
            assertThat(playerState.value).isEqualTo(state)
            assertThat(label).isNotEmpty()
        }
        verify(exactly = 3) { musicPlayer.stopAndClear(clearSession = true) }
        verify(exactly = 0) { musicPlayer.stopAndClear(clearSession = false) }
        coVerify(exactly = 3) { downloadManager.clearAllDownloads() }
        coVerify(exactly = 3) { sessionPreferences.clearAll() }
        assertThat(viewModel.uiState.value.showClearAllConfirm).isFalse()
    }

    @Test
    fun confirmClearAllData_waitsForDownloadCleanupBeforeClearingSessionOrCompleting() {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        order.clear()
        coEvery { downloadManager.clearAllDownloads() } coAnswers {
            order += "downloads-start"
            started.complete(Unit)
            gate.await()
            order += "downloads-end"
        }
        var completed = 0
        viewModel.confirmClearAllData { completed++ }

        assertThat(started.isCompleted).isTrue()
        assertThat(order).containsExactly("player", "downloads-start").inOrder()
        assertThat(completed).isEqualTo(0)
        assertThat(session.value.apiKey).isEqualTo("secret-key")
        coVerify(exactly = 0) { sessionPreferences.clearAll() }
        coVerify(exactly = 0) { playlistRepository.clearAll() }
        coVerify(exactly = 0) { discoverRepository.clearRecommendationExclusions() }

        gate.complete(Unit)

        assertThat(order).containsExactly(
            "player",
            "downloads-start",
            "downloads-end",
            "local-music",
            "search-history",
            "db-caches",
            "autoeq",
            "loudness",
            "exclusions",
            "podcast-progress",
            "playlists",
            "spotify",
            "youtube",
            "session",
        ).inOrder()
        assertThat(completed).isEqualTo(1)
        coVerify(exactly = 1) { sessionPreferences.clearAll() }
        assertThat(viewModel.uiState.value.showClearAllConfirm).isFalse()
    }

    @Test
    fun dismissClearAllConfirm_doesNotMutatePlaybackDownloadsCredentialsOrPlaylists() {
        viewModel.requestClearAllData()
        assertThat(viewModel.uiState.value.showClearAllConfirm).isTrue()
        viewModel.dismissClearAllConfirm()
        assertThat(viewModel.uiState.value.showClearAllConfirm).isFalse()
        assertThat(order).isEmpty()
        assertThat(session.value.apiKey).isEqualTo("secret-key")
        assertThat(session.value.apiSecret).isEqualTo("secret-secret")
        verify(exactly = 0) { musicPlayer.stopAndClear(any()) }
        coVerify(exactly = 0) { downloadManager.clearAllDownloads() }
        coVerify(exactly = 0) { sessionPreferences.clearAll() }
        coVerify(exactly = 0) { playlistRepository.clearAll() }
        coVerify(exactly = 0) { discoverRepository.clearRecommendationExclusions() }
    }

    @Test
    fun confirmClearAllData_whenDownloadDeleteFails_keepsCredentialsAndSurfacesTheExistingToast() {
        coEvery { downloadManager.clearAllDownloads() } throws IOException("permission denied")
        viewModel.requestClearAllData()
        var completed = 0
        viewModel.confirmClearAllData { completed++ }

        assertThat(completed).isEqualTo(0)
        assertThat(order).containsExactly("player").inOrder()
        assertThat(session.value.apiKey).isEqualTo("secret-key")
        assertThat(session.value.apiSecret).isEqualTo("secret-secret")
        coVerify(exactly = 0) { sessionPreferences.clearAll() }
        coVerify(exactly = 0) { playlistRepository.clearAll() }
        coVerify(exactly = 0) { discoverRepository.clearRecommendationExclusions() }
        assertThat(viewModel.uiState.value.toastMessage).isEqualTo("Couldn't clear saved data. Please try again.")
        assertThat(viewModel.uiState.value.showClearAllConfirm).isTrue()
        verify(exactly = 1) { musicPlayer.stopAndClear(clearSession = true) }

        order.clear()
        coEvery { downloadManager.clearAllDownloads() } coAnswers { order += "downloads" }
        viewModel.confirmClearAllData { completed++ }
        assertThat(completed).isEqualTo(1)
        assertThat(order).containsExactly(
            "player",
            "downloads",
            "local-music",
            "search-history",
            "db-caches",
            "autoeq",
            "loudness",
            "exclusions",
            "podcast-progress",
            "playlists",
            "spotify",
            "youtube",
            "session",
        ).inOrder()
        assertThat(completed).isEqualTo(1)
        coVerify(exactly = 1) { sessionPreferences.clearAll() }
        assertThat(viewModel.uiState.value.showClearAllConfirm).isFalse()
    }

    @Test
    fun logOutAndClearSession_leavePlaybackAndDownloadsUntouched() {
        var loggedOut = 0
        var completed = 0
        coEvery { sessionPreferences.logOutApiCredentials() } coAnswers { loggedOut++ }
        viewModel.logOut { completed++ }
        assertThat(loggedOut).isEqualTo(1)
        assertThat(completed).isEqualTo(1)

        var cleared = 0
        coEvery { sessionPreferences.clearAll() } coAnswers { cleared++ }
        viewModel.clearSession { completed++ }
        assertThat(cleared).isEqualTo(1)
        assertThat(completed).isEqualTo(2)
        verify(exactly = 0) { musicPlayer.stopAndClear(any()) }
        coVerify(exactly = 0) { downloadManager.clearAllDownloads() }
        coVerify(exactly = 0) { playlistRepository.clearAll() }
        coVerify(exactly = 0) { discoverRepository.clearRecommendationExclusions() }
    }
}
