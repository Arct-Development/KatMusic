package com.itzfallenme.katmusic.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itzfallenme.katmusic.data.model.AuthState
import com.itzfallenme.katmusic.ui.screens.auth.AuthViewModel
import com.itzfallenme.katmusic.ui.screens.auth.LoginScreen
import com.itzfallenme.katmusic.ui.screens.home.HomeScreen
import com.itzfallenme.katmusic.ui.screens.settings.SettingsScreen
import com.itzfallenme.katmusic.ui.screens.jam.ListenTogetherScreen
import com.itzfallenme.katmusic.ui.screens.blend.GroupBlendScreen
import com.itzfallenme.katmusic.ui.screens.search.SearchScreen
import com.itzfallenme.katmusic.ui.screens.discover.DiscoverScreen
import com.itzfallenme.katmusic.ui.screens.genres.GenresScreen
import com.itzfallenme.katmusic.ui.screens.shell.MainShell
import com.itzfallenme.katmusic.ui.components.BackAwareScreen
import com.itzfallenme.katmusic.ui.components.LoadingPulse
import com.itzfallenme.katmusic.ui.components.MotionSpec
import com.itzfallenme.katmusic.ui.screens.genres.GenreExplorer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.stateIn

/** Thin bridge so KatMusicNavHost (a plain composable, no direct Hilt
 *  singleton access) can observe GenreExplorer's pending genre and
 *  navigate — same reasoning as MainShellViewModel's bridge to
 *  MixLauncher. */
@HiltViewModel
class GenreExplorerNavBridge @Inject constructor(genreExplorer: GenreExplorer) : ViewModel() {
    val pendingGenre = genreExplorer.pendingGenre
}

@HiltViewModel
class MixLauncherNavBridge @Inject constructor(val mixLauncher: com.itzfallenme.katmusic.ui.screens.generate.MixLauncher) : ViewModel()


@HiltViewModel
class AppUpdateNavBridge @Inject constructor(
    private val appUpdateManager: com.itzfallenme.katmusic.data.update.AppUpdateManager,
) : ViewModel() {
    val updateInfo = appUpdateManager.updateInfo
    fun dismiss(version: String) = appUpdateManager.dismissUpdate(version)
    fun open(context: android.content.Context) = appUpdateManager.openUpdate(context)
}

@HiltViewModel
class ArtistAlbumNavBridge @Inject constructor(val navigator: ArtistAlbumNavigator) : ViewModel()

@HiltViewModel
class AppRouteNavBridge @Inject constructor(val routeNavigator: AppRouteNavigator) : ViewModel()

/**
 * Splash / onboarding gate for the YouTube Music-first flow.
 *
 * Routes to MainShell when ANY onboarding signal is present:
 *  - a Last.fm session (legacy [AuthState.SignedIn]), OR
 *  - a YouTube Music connection (cookies captured via YtMusicAuthManager), OR
 *  - a previously-selected guest mode (account-free).
 * Otherwise routes to Login, which now shows only "Login with YouTube Music"
 * and "Continue as Guest".
 */
@HiltViewModel
class LaunchGateViewModel @Inject constructor(
    authRepository: com.itzfallenme.katmusic.data.repository.AuthRepository,
    sessionPreferences: com.itzfallenme.katmusic.data.local.SessionPreferences,
    ytAuthManager: com.itzfallenme.katmusic.data.ytmusic.YtMusicAuthManager,
) : ViewModel() {
    sealed interface GateTarget {
        data object Loading : GateTarget
        data object Login : GateTarget
        data object MainShell : GateTarget
    }

    val gateTarget: kotlinx.coroutines.flow.StateFlow<GateTarget> =
        kotlinx.coroutines.flow.combine(
            authRepository.authState,
            sessionPreferences.session,
            sessionPreferences.guestMode,
            ytAuthManager.connection,
        ) { authState, session, guestMode, ytConnection ->
            // Wait for DataStore to load before deciding; otherwise every
            // cold start would flash Login.
            if (!session.isLoaded) {
                GateTarget.Loading
            } else if (authState is AuthState.Unknown) {
                GateTarget.Loading
            } else if (authState is AuthState.SignedIn) {
                GateTarget.MainShell
            } else if (guestMode) {
                GateTarget.MainShell
            } else if (ytConnection.isConnected) {
                GateTarget.MainShell
            } else {
                GateTarget.Login
            }
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            initialValue = GateTarget.Loading,
        )
}

@Composable
fun KatMusicNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val appRouteBridge: AppRouteNavBridge = hiltViewModel()
    val pendingAppRoute by appRouteBridge.routeNavigator.pendingRoute.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val updateBridge: AppUpdateNavBridge = hiltViewModel()
    val updateInfo by updateBridge.updateInfo.collectAsStateWithLifecycle()
    val updateContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(pendingAppRoute, currentRoute) {
        val route = pendingAppRoute
        if (route != null && currentRoute != null &&
            currentRoute != Screen.Splash.route && currentRoute != Screen.Login.route
        ) {
            if (currentRoute != route) {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
            appRouteBridge.routeNavigator.consumeRoute()
        }
    }
    // "Explore this genre" from any track's context menu, anywhere in the
    // app — Home, Discover, Playlist, Search — routes here since Genres
    // is a pushed destination on THIS nav controller and none of those
    // screens hold a reference to it. See GenreExplorer's doc comment.
    val genreExplorerBridge: GenreExplorerNavBridge = hiltViewModel()
    val pendingGenre by genreExplorerBridge.pendingGenre.collectAsStateWithLifecycle()
    LaunchedEffect(pendingGenre) {
        if (pendingGenre != null) {
            navController.navigate(Screen.Genres.route) {
                launchSingleTop = true
            }
        }
    }

    val navBridge: ArtistAlbumNavBridge = hiltViewModel()
    LaunchedEffect(Unit) {
        navBridge.navigator.events.collect { target ->
            // Drill-down destinations MUST push a fresh back-stack entry every
            // time: launchSingleTop would collapse Artist A -> Artist B (or
            // Album X -> Album Y) into the top entry since they share one
            // destination node, leaving taps on similar artists / more-by
            // albums visibly dead and the detail ViewModel showing stale data.
            // Rapid double-tap dedup already lives in ArtistAlbumNavigator.
            when (target) {
                is ArtistAlbumNavTarget.Artist -> {
                    navController.navigate(Screen.ArtistDetail.createRoute(target.name, target.browseId))
                }
                is ArtistAlbumNavTarget.Album -> {
                    navController.navigate(Screen.AlbumDetail.createRoute(target.title, target.artist, target.browseId))
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = { MotionSpec.forwardEnter() },
        exitTransition = { MotionSpec.forwardExit() },
        popEnterTransition = { MotionSpec.backEnter() },
        popExitTransition = { MotionSpec.backExit() },
    ) {

        // Resolves the persisted session BEFORE showing any interactive UI.
        // YouTube Music-first gate: MainShell when Last.fm signed in OR
        // YouTube Music connected OR guest mode was previously selected.
        // This keeps login persistent without flashing Login on cold start.
        composable(Screen.Splash.route) {
            val gateViewModel: LaunchGateViewModel = hiltViewModel()
            val gateTarget by gateViewModel.gateTarget.collectAsStateWithLifecycle()

            var openingAnimationFinished by remember { mutableStateOf(false) }
            LaunchedEffect(gateTarget, openingAnimationFinished) {
                if (openingAnimationFinished) {
                    when (gateTarget) {
                        LaunchGateViewModel.GateTarget.MainShell -> navController.navigate(Screen.MainShell.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                        LaunchGateViewModel.GateTarget.Login -> navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                        LaunchGateViewModel.GateTarget.Loading -> Unit
                    }
                }
            }
            LaunchGate { openingAnimationFinished = true }
        }

        composable(Screen.Login.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            val themeViewModel: com.itzfallenme.katmusic.ui.theme.ThemeViewModel = hiltViewModel()
            val appearanceState by themeViewModel.uiState.collectAsStateWithLifecycle()
            val gateViewModel: LaunchGateViewModel = hiltViewModel()
            val gateTarget by gateViewModel.gateTarget.collectAsStateWithLifecycle()
            val webAuthState by authViewModel.webAuthState.collectAsStateWithLifecycle()

            // YouTube Music-first onboarding: any onboarded signal (Last.fm
            // session, YT connection, or guest flag) skips straight to
            // MainShell. YT login returns here via YouTubeLogin's onConnected
            // pop, then this effect immediately forwards to MainShell.
            LaunchedEffect(gateTarget, webAuthState) {
                if (gateTarget == LaunchGateViewModel.GateTarget.MainShell &&
                    webAuthState == com.itzfallenme.katmusic.ui.screens.auth.WebAuthState.Idle
                ) {
                    navController.navigate(Screen.MainShell.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }

            val restoreError = (webAuthState as? com.itzfallenme.katmusic.ui.screens.auth.WebAuthState.Error)?.message
            val restoring = webAuthState == com.itzfallenme.katmusic.ui.screens.auth.WebAuthState.RestoringBackup
            LoginScreen(
                onLoginWithYouTube = {
                    navController.navigate(Screen.YouTubeLogin.route)
                },
                onContinueAsGuest = {
                    authViewModel.continueAsGuest()
                },
                onRestoreBackupAndSignIn = authViewModel::restoreBackupOnly,
                onDismissError = authViewModel::dismissError,
                errorMessage = restoreError,
                isBusy = restoring,
                onLegacyFriendlyModeChange = themeViewModel::setLegacyFriendlyMode,
                isLegacyFriendlyMode = !appearanceState.liquidGlass && appearanceState.md3NavBar,
                onOpenDownloads = {
                    navController.navigate(Screen.Downloads.route)
                },
            )
        }

        // Post-login container: Material3 bottom nav with Home/Generate/
        // Playlists as swipeable tabs. Settings, Search, and Discover are
        // NOT tabs — they're pushed screens reached from Home's top app bar
        // (profile icon / search icon / discover icon), on this same root
        // nav controller, so they can pop back cleanly and (on log out /
        // clear session) return all the way to Login.
        composable(Screen.MainShell.route) {
            MainShell(
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                },
                onOpenSearch = { /* Search is part of the main navigation dock. */ },
                onOpenDiscover = { navController.navigate(Screen.Discover.route) },
                onOpenGenres = { navController.navigate(Screen.Genres.route) },
                onOpenFriends = { navController.navigate(Screen.Friends.route) },
                onOpenFriendProfile = { username, displayName, avatarUrl ->
                    navController.navigate(Screen.FriendProfile.createRoute(username, displayName, avatarUrl))
                },
                onOpenPlaylist = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                },
                onOpenFeedPlaylist = { playlistId ->
                    navController.navigate(Screen.FeedPlaylistDetail.createRoute(playlistId))
                },
                onOpenGenerator = {
                    navController.navigate(Screen.Create.route) { launchSingleTop = true }
                },
                onOpenNewReleases = {
                    navController.navigate(Screen.NewReleases.route)
                },
                onOpenDownloads = {
                    navController.navigate(Screen.Downloads.route)
                },
                onOpenListenTogether = {
                    navController.navigate(Screen.ListenTogether.route)
                },
                onOpenSearchResultArtist = { name, browseId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(name, browseId))
                },
                onOpenSearchResultAlbum = { title, artist, browseId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(title, artist, browseId))
                },
                onOpenSearchResultPlaylist = { playlistId ->
                    navController.navigate(Screen.FeedPlaylistDetail.createRoute(playlistId))
                },
                onOpenPodcasts = {
                    navController.navigate(Screen.Podcasts.route)
                },
                onOpenPodcastShow = { showId ->
                    navController.navigate(Screen.PodcastShow.createRoute(showId))
                },
                onOpenYouTubeLogin = {
                    navController.navigate(Screen.YouTubeLogin.route)
                },
                onReturnToHome = {
                    if (navController.currentDestination?.route != Screen.MainShell.route) {
                        navController.popBackStack(Screen.MainShell.route, inclusive = false)
                    } else {
                        false
                    }
                },
            )
        }

        composable(Screen.Stats.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                HomeScreen(
                    onOpenSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                    onOpenSearch = { navController.navigate(Screen.Search.route) },
                    onOpenDiscover = { navController.navigate(Screen.Discover.route) },
                    onOpenGenres = { navController.navigate(Screen.Genres.route) },
                    onOpenFriends = { navController.navigate(Screen.Friends.route) },
                    onOpenDownloads = { navController.navigate(Screen.Downloads.route) },
                )
            }
        }

        // Settings is a pushed destination on the root NavController.
        // It must be registered here before MainShell's Settings action can navigate to it.
        composable(Screen.Settings.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.MainShell.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenDownloads = { navController.navigate(Screen.Downloads.route) },
                    onOpenListenTogether = { navController.navigate(Screen.ListenTogether.route) },
                    onOpenGroupBlend = { navController.navigate(Screen.GroupBlend.route) },
                    onOpenHomeSections = { navController.navigate(Screen.HomeSections.route) },
                    onOpenExcludedSongs = { navController.navigate(Screen.ExcludedSongs.route) },
                    onOpenYouTubeImport = { navController.navigate(Screen.YouTubeImport.route) },
                    onOpenYouTubeLogin = { navController.navigate(Screen.YouTubeLogin.route) },
                    onOpenExternalImport = { navController.navigate(Screen.ExternalPlaylistImport.route) },
                    onOpenSpotifyLogin = { navController.navigate(Screen.SpotifyLogin.route) },
                    onOpenSpotifyTokenImport = { navController.navigate(Screen.SpotifyTokenImport.route) },
                )
            }
        }

        composable(Screen.ListenTogether.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                ListenTogetherScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.GroupBlend.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                GroupBlendScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.Create.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.generate.GenerateScreen(
                    onNavigateToPlaylist = { playlistId ->
                        navController.popBackStack()
                        if (playlistId != null) {
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // Friends is a real pushed screen (not a Dialog/ModalBottomSheet) —
        // both of those were tried first and both had the same underlying
        // problem: getting a Compose overlay to genuinely claim the full
        // real window (not just "as tall as its own content measured
        // itself to be", which is all either API reliably guarantees
        // without extra low-level workarounds) turned out to be fragile
        // enough, even after direct fixes, that it kept regressing. A
        // normal NavHost destination gets full-screen sizing for free —
        // every other pushed screen here (Settings, Search, Discover,
        // Genres) already renders correctly edge-to-edge —
        // so Friends now works exactly like those instead of being a
        // special case. It shares Home's own HomeViewModel (scoped to
        // MainShell's back stack entry) rather than getting a fresh one,
        // since the friends list / switch-profile state already lives
        // there and switching profile needs to affect the Home tab
        // underneath once you pop back.
        composable(Screen.Friends.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.MainShell.route)
            }
            val homeViewModel: com.itzfallenme.katmusic.ui.screens.home.HomeViewModel = hiltViewModel(parentEntry)
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.home.FriendsScreen(
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenFriendProfile = { username, displayName, avatarUrl ->
                        navController.navigate(Screen.FriendProfile.createRoute(username, displayName, avatarUrl))
                    },
                )
            }
        }

        composable(
            route = Screen.FriendProfile.route,
            arguments = listOf(
                androidx.navigation.navArgument("username") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("displayName") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("avatarUrl") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username").orEmpty()
            val displayName = backStackEntry.arguments?.getString("displayName")?.takeIf(String::isNotBlank)
            val avatarUrl = backStackEntry.arguments?.getString("avatarUrl")?.takeIf(String::isNotBlank)
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.home.FriendProfileScreen(
                    username = username,
                    initialDisplayName = displayName,
                    initialAvatarUrl = avatarUrl,
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { artistName ->
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    },
                    onOpenAlbum = { albumTitle, artistName ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumTitle, artistName))
                    },
                )
            }
        }

        // Every pushed-on-top destination below is wrapped in
        // BackAwareScreen so Android's predictive back gesture (drag
        // in from the edge) scales/rounds/dims the screen in real time
        // instead of using a canned pop transition — see its doc comment
        // for exactly what it does and does not animate. The screen's own
        // onBack (its toolbar back button) still pops directly; the wrap
        // only adds the gesture-driven path, it doesn't replace the
        // button's.
        composable(Screen.Genres.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                GenresScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPlaylist = {
                        navController.popBackStack()
                        // Playlist tab already re-reads on resume (PlaylistViewModel's
                        // LifecycleResumeEffect), so popping back to MainShell is
                        // sufficient — no separate "switch tab" signal is needed here
                        // since MainShell always shows Playlists as one of its tabs
                        // and the user lands back wherever they left MainShell.
                    },
                )
            }
        }


      composable(Screen.SpotifyLogin.route) {
      BackAwareScreen(onBack = { navController.popBackStack() }) {
      com.itzfallenme.katmusic.ui.screens.settings.SpotifyLoginScreen(
      onBack = { navController.popBackStack() },
      onConnected = { navController.popBackStack() },
      )
      }
      }

      composable(Screen.SpotifyTokenImport.route) {
      BackAwareScreen(onBack = { navController.popBackStack() }) {
      com.itzfallenme.katmusic.ui.screens.settings.SpotifyLoginScreen(
      onBack = { navController.popBackStack() },
      onConnected = { navController.popBackStack() },
      openManualEntryInitially = true,
      )
      }
      }


        composable(Screen.YouTubeImport.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.settings.YouTubePlaylistImportScreen(
                    onBack = { navController.popBackStack() },
                    onImportSuccess = {
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(Screen.YouTubeLogin.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.settings.YouTubeLoginScreen(
                    onBack = { navController.popBackStack() },
                    onConnected = { navController.popBackStack() },
                )
            }
        }

        composable(Screen.ExternalPlaylistImport.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.settings.ExternalPlaylistImportScreen(
                    onBack = { navController.popBackStack() },
                    onImportSuccess = {
                        navController.popBackStack()
                    },
                    onOpenSpotifyLogin = {
                        navController.navigate(Screen.SpotifyLogin.route)
                    },
                )
            }
        }

        composable(Screen.Downloads.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.settings.DownloadsScreen(onBack = { navController.popBackStack() })
            }
        }


        composable(Screen.HomeSections.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.settings.HomeSectionsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.ExcludedSongs.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.settings.ExcludedSongsScreen(onBack = { navController.popBackStack() })
            }
        }


        composable(Screen.Search.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { name, browseId ->
                        navController.navigate(Screen.ArtistDetail.createRoute(name, browseId))
                    },
                    onOpenAlbum = { title, artist, browseId ->
                        navController.navigate(Screen.AlbumDetail.createRoute(title, artist, browseId))
                    },
                    onOpenPlaylist = { playlistId ->
                        navController.navigate(Screen.FeedPlaylistDetail.createRoute(playlistId))
                    },
                )
            }
        }

        composable(Screen.Discover.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                DiscoverScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.NewReleases.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.newreleases.NewReleasesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Screen.Podcasts.route) {
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.podcast.PodcastsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenShow = { showId ->
                        navController.navigate(Screen.PodcastShow.createRoute(showId))
                    },
                )
            }
        }

        composable(
            route = Screen.PodcastShow.route,
            arguments = listOf(
                androidx.navigation.navArgument("showId") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val showId = backStackEntry.arguments?.getString("showId").orEmpty()
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.podcast.PodcastShowScreen(
                    showId = showId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Screen.FeedPlaylistDetail.route,
            arguments = listOf(
                androidx.navigation.navArgument("playlistId") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.feed.FeedPlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(
                androidx.navigation.navArgument("playlistId") {
                    type = androidx.navigation.NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.playlist.PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                    onOpenPlaylist = { newId ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(newId))
                    },
                )
            }
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(
                androidx.navigation.navArgument("artistName") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("browseId") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName").orEmpty()
            val browseId = backStackEntry.arguments?.getString("browseId")?.takeIf(String::isNotBlank)
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.artist.ArtistDetailScreen(
                    artistName = artistName,
                    browseId = browseId,
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { title, artist, id ->
                        navController.navigate(Screen.AlbumDetail.createRoute(title, artist, id))
                    },
                    onOpenArtist = { name, id ->
                        navController.navigate(Screen.ArtistDetail.createRoute(name, id))
                    },
                )
            }
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(
                androidx.navigation.navArgument("albumTitle") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("artistName") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("browseId") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val albumTitle = backStackEntry.arguments?.getString("albumTitle").orEmpty()
            val artistName = backStackEntry.arguments?.getString("artistName").orEmpty()
            val browseId = backStackEntry.arguments?.getString("browseId")?.takeIf(String::isNotBlank)
            BackAwareScreen(onBack = { navController.popBackStack() }) {
                com.itzfallenme.katmusic.ui.screens.album.AlbumDetailScreen(
                    albumTitle = albumTitle,
                    artistName = artistName,
                    browseId = browseId,
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { name, id ->
                        navController.navigate(Screen.ArtistDetail.createRoute(name, id))
                    },
                    onOpenAlbum = { title, artist, id ->
                        navController.navigate(Screen.AlbumDetail.createRoute(title, artist, id))
                    },
                )
            }
        }
    }
    // Check for releases in the app-wide manager and show the same prompt
    // after the black startup animation, regardless of login or guest state.
    if (
        currentRoute != null &&
        currentRoute != Screen.Splash.route &&
        updateInfo.isUpdateAvailable &&
        !updateInfo.isDismissed
    ) {
        AlertDialog(
            onDismissRequest = { updateBridge.dismiss(updateInfo.latestVersion) },
            shape = RoundedCornerShape(14.dp),
            title = { Text("KatMusic update available", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text("Version ${updateInfo.latestVersion} is ready.")
                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            updateInfo.releaseNotes.take(420).let {
                                if (updateInfo.releaseNotes.length > 420) "${it}…" else it
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { updateBridge.open(updateContext) }) {
                    Text("View update")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateBridge.dismiss(updateInfo.latestVersion) }) {
                    Text("Later")
                }
            },
        )
    }
}

@Composable
private fun LaunchGate(onAnimationFinished: () -> Unit) {
    var videoFinished by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    val logoOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (showName) (-34).dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(560),
        label = "startupLogoSlide",
    )

    // Never skip the animation because auth/session loading finishes early.
    // The timeout is only a decoder-failure safety net; normal navigation is
    // driven by the actual video completion callback.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(18_000L)
        if (!videoFinished) videoFinished = true
    }
    LaunchedEffect(videoFinished) {
        if (videoFinished) {
            kotlinx.coroutines.delay(240L)
            showName = true
        }
    }
    LaunchedEffect(showName) {
        if (showName) {
            // Let the mark settle and the wordmark complete its entrance
            // before removing the splash destination from the back stack.
            kotlinx.coroutines.delay(760L)
            onAnimationFinished()
        }
    }

    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (!videoFinished) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    android.widget.VideoView(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setVideoURI(
                            android.net.Uri.parse(
                                "android.resource://${context.packageName}/${com.itzfallenme.katmusic.R.raw.startup_logo}",
                            ),
                        )
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            start()
                        }
                        setOnCompletionListener {
                            videoFinished = true
                        }
                        setOnErrorListener { _, what, extra ->
                            android.util.Log.w(
                                "KatMusicSplash",
                                "Logo animation playback failed (what=$what, extra=$extra)",
                            )
                            videoFinished = true
                            true
                        }
                    }
                },
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = videoFinished,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(360)),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(com.itzfallenme.katmusic.R.drawable.ic_launcher_logo),
                    contentDescription = "KatMusic logo",
                    modifier = Modifier.size(76.dp).offset(x = logoOffset),
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = showName,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(420)) +
                        androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { it / 2 },
                            animationSpec = androidx.compose.animation.core.tween(520),
                        ),
                ) {
                    Text(
                        "Kat Music",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }
        }
    }
}
