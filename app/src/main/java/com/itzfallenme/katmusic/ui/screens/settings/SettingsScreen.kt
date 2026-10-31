package com.itzfallenme.katmusic.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import com.itzfallenme.katmusic.ui.components.HeaderActionIcon
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import com.itzfallenme.katmusic.util.BatteryOptimizationHelper
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Waves
import com.itzfallenme.katmusic.data.local.LyricsProvider
import com.itzfallenme.katmusic.ui.screens.player.LyricsOffsetDialog
import com.itzfallenme.katmusic.ui.screens.player.LyricsSizeDialog
import androidx.compose.material.icons.filled.FormatSize
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.blur
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.itzfallenme.katmusic.data.local.AppLanguage
import com.itzfallenme.katmusic.data.local.nativeDisplayName
import com.itzfallenme.katmusic.data.lyrics.TRANSLATION_LANGUAGES
import com.itzfallenme.katmusic.data.lyrics.translationLanguageName
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Locale

import com.itzfallenme.katmusic.ui.screens.player.LocalMiniPlayerScrollClearance
import com.itzfallenme.katmusic.R
import com.itzfallenme.katmusic.data.local.AccentMode
import com.itzfallenme.katmusic.data.local.ThemeMode
import com.itzfallenme.katmusic.data.local.EQ_BAND_FREQS_HZ
import com.itzfallenme.katmusic.data.local.EQ_MAX_GAIN_DB
import com.itzfallenme.katmusic.data.local.EqualizerPresets
import com.itzfallenme.katmusic.data.local.EqualizerSettings
import com.itzfallenme.katmusic.playback.ClarityPresets
import com.itzfallenme.katmusic.playback.LoudnessMode
import com.itzfallenme.katmusic.data.local.eqBandLabel
import com.itzfallenme.katmusic.ui.components.ConnectedButtonGroup
import com.itzfallenme.katmusic.ui.components.ConnectedButtonItem
import com.itzfallenme.katmusic.ui.components.ScreenHeader
import com.itzfallenme.katmusic.ui.components.safeDrawingBottomPadding
import com.itzfallenme.katmusic.ui.components.safeHorizontalContentPadding
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import com.itzfallenme.katmusic.ui.theme.ExpressivePillShape
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private data class AccentPreset(val name: String, val hex: String)
private val ACCENT_PRESETS = listOf(
    AccentPreset("Crimson", "#E03030"),
    AccentPreset("Violet", "#7C4DFF"),
    AccentPreset("Ocean", "#2196C6"),
    AccentPreset("Sage", "#6B9E6B"),
    AccentPreset("Amber", "#E0A030"),
    AccentPreset("Rose", "#E0507A"),
)

data class ToggleDetailInfo(
    val title: String,
    val subtitle: String,
    val checked: Boolean,
)

val LocalToggleDetailViewer = staticCompositionLocalOf<((ToggleDetailInfo) -> Unit)?> { null }

// -- Expressive shape scale used only within this screen --
private val CardOuterShape = RoundedCornerShape(28.dp)
private val IconBadgeShape = RoundedCornerShape(14.dp)

/** Where a row sits within a visually-connected group of settings rows —
 *  drives per-row corner radii so a multi-row group reads as one premium
 *  surface split into rows, not a stack of separate cards (see groupShape
 *  below and the GROUP_GAP spacing used between rows in a group's Column). */
private enum class GroupPosition { SINGLE, TOP, MIDDLE, BOTTOM }

private val GROUP_OUTER_RADIUS = 28.dp
private val GROUP_INNER_RADIUS = 6.dp
private val GROUP_GAP = 3.dp

private fun groupShape(position: GroupPosition): RoundedCornerShape =
    RoundedCornerShape(22.dp)

/** Wraps a fixed list of settings rows and assigns each one its
 *  GroupPosition automatically — SINGLE for a lone row, TOP/BOTTOM for the
 *  ends of a longer group, MIDDLE for everything between. Rows are stacked
 *  with a tiny GROUP_GAP rather than normal item spacing, so the group
 *  reads as one connected surface with rows peeking through a hairline gap
 *  rather than a list of separate cards. */
@Composable
private fun SettingsGroup(rowCount: Int, content: @Composable (index: Int, position: GroupPosition) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
        for (i in 0 until rowCount) {
            val position = when {
                rowCount == 1 -> GroupPosition.SINGLE
                i == 0 -> GroupPosition.TOP
                i == rowCount - 1 -> GroupPosition.BOTTOM
                else -> GroupPosition.MIDDLE
            }
            content(i, position)
        }
    }
}

/**
 * Determines the target LazyColumn item index (section) for a searchable setting.
 */
private fun getTargetSectionIndex(settingId: String?): Int {
    if (settingId == null) return 0
    val entry = SettingsSearchIndex.allEntries.firstOrNull { it.id == settingId } ?: return 0
    return when (entry.parentTab) {
        SettingsTab.AUDIO -> if (entry.section == "Output & Loudness") 1 else 0
        SettingsTab.APPEARANCE -> when (entry.section) {
            "Accent Color" -> 1
            "Experimental & Features", "Experimental & Player" -> 2
            "Canvas" -> 3
            else -> 0
        }
        SettingsTab.YOUTUBE -> if (entry.section == "Liked Songs") 1 else 0
        SettingsTab.SPOTIFY -> 0
        SettingsTab.LIBRARY -> when (entry.section) {
            "Content" -> 1
            "Imports", "Imports & Addons", "Modules & Addons" -> 2
            else -> 0
        }
        SettingsTab.DATA_BACKUP -> if (entry.section == "Data Management") 1 else 0
        SettingsTab.ABOUT -> if (entry.section == "About & Community") 1 else 0
        else -> 0
    }
}

/**
 * Attaches a BringIntoViewRequester and renders an expressive pulsing highlight glow
 * with outer colored shadow halo, accent border, and auto-scroll when isHighlighted is true.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.settingHighlightGlow(
    isHighlighted: Boolean,
    shape: Shape = CardOuterShape,
): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            delay(160L)
            bringIntoViewRequester.bringIntoView()
        }
    }

    if (!isHighlighted) {
        return@composed this.bringIntoViewRequester(bringIntoViewRequester)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "settingHighlightGlow")
    val glowProgress by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowProgress",
    )

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .shadow(
            elevation = (7 * glowProgress).dp,
            shape = shape,
            ambientColor = primaryColor.copy(alpha = 0.40f * glowProgress),
            spotColor = primaryColor.copy(alpha = 0.55f * glowProgress),
        )
        .border(
            width = 2.5.dp,
            color = primaryColor.copy(alpha = glowProgress),
            shape = shape,
        )
}

/**
 * Faithful port of settings.js (par 8): Last.fm account management, appearance
 * (AMOLED / Dynamic Color / Monochrome / accent presets / custom color
 * wheel), iTunes/ListenBrainz artwork toggles, data management (clear
 * recommendation exclusions, clear all data), backup & restore, and app info.
 *
 * Visuals only: restyled into a Material 3 Expressive presentation
 * (larger touch targets, per-row cards, tonal icon badges, spring-based
 * press feedback). Every setting, callback, and piece of state below is
 * unchanged from the original implementation.
 */

enum class SettingsTab(
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val subtitleRes: Int,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    AUDIO(
        titleRes = R.string.set_cat_audio,
        subtitleRes = R.string.set_cat_audio_sub,
        title = "Audio & Playback",
        subtitle = "Streaming quality, Audio engine, Equalizer, Output & Loudness",
        icon = Icons.Filled.GraphicEq,
    ),
    APPEARANCE(
        titleRes = R.string.set_cat_appear,
        subtitleRes = R.string.set_cat_appear_sub,
        title = "Appearance & Visuals",
        subtitle = "Themes, Colors, Artwork, Lyrics",
        icon = Icons.Filled.Palette,
    ),
    YOUTUBE(
        titleRes = R.string.set_cat_yt,
        subtitleRes = R.string.set_cat_yt_sub,
        title = "YouTube & Sync",
        subtitle = "Account connection, 24/7 Library sync, Channels, History",
        icon = Icons.Filled.CloudSync,
    ),
    SPOTIFY(
        titleRes = R.string.set_cat_spotify,
        subtitleRes = R.string.set_cat_spotify_sub,
        title = "Spotify",
        subtitle = "Account connection, Canvas, Playlist imports & Liked Songs sync",
        icon = Icons.Filled.PlayCircle,
    ),
    LAST_FM(
        titleRes = R.string.set_cat_lfm,
        subtitleRes = R.string.set_cat_lfm_sub,
        title = "Last.fm",
        subtitle = "Account connection, Scrobbling sync & API credentials",
        icon = Icons.Filled.Album,
    ),
    LIBRARY(
        titleRes = R.string.set_cat_lib,
        subtitleRes = R.string.set_cat_lib_sub,
        title = "Library & Content",
        subtitle = "Home layout, Playlist imports, Downloads, Exclusions",
        icon = Icons.Filled.QueueMusic,
    ),
    DATA_BACKUP(
        titleRes = R.string.set_cat_data,
        subtitleRes = R.string.set_cat_data_sub,
        title = "Data Management",
        subtitle = "Cache cleanup, Export & Import settings, Database tools",
        icon = Icons.Filled.Backup,
    ),
    ABOUT(
        titleRes = R.string.set_cat_about,
        subtitleRes = R.string.set_cat_about_sub,
        title = "About KatMusic",
        subtitle = "Version, diagnostics and source code",
        icon = Icons.Filled.Info,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenHomeSections: () -> Unit = {},
    onOpenExcludedSongs: () -> Unit = {},
    onOpenYouTubeImport: () -> Unit = {},
    onOpenYouTubeLogin: () -> Unit = {},
    onOpenExternalImport: () -> Unit = {},
    onOpenSpotifyLogin: () -> Unit = {},
    onOpenSpotifyTokenImport: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val misc by viewModel.misc.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle()
    val downloadTotalBytes by viewModel.downloadTotalBytes.collectAsStateWithLifecycle()
    val ytConnection by viewModel.ytConnection.collectAsStateWithLifecycle()
    val ytSyncEnabled by viewModel.ytSyncEnabled.collectAsStateWithLifecycle()
    val ytHistorySyncEnabled by viewModel.ytHistorySyncEnabled.collectAsStateWithLifecycle()
    val ytSyncState by viewModel.ytSyncState.collectAsStateWithLifecycle()
    val ytLastSyncAt by viewModel.ytLastSyncAt.collectAsStateWithLifecycle()
    val syncedPlaylistIds by viewModel.syncedPlaylistIds.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val ytAccountPlaylists by viewModel.ytAccountPlaylists.collectAsStateWithLifecycle()
    val hiddenYtLibraryPlaylistIds by viewModel.hiddenYtLibraryPlaylistIds.collectAsStateWithLifecycle()
    val ytChannels by viewModel.ytChannels.collectAsStateWithLifecycle()
    val ytChannelsLoading by viewModel.ytChannelsLoading.collectAsStateWithLifecycle()
    val spotifyConnected by viewModel.spotifyConnected.collectAsStateWithLifecycle()
    val eq by viewModel.equalizer.collectAsStateWithLifecycle()
    val usbExclusiveEnabled by viewModel.usbExclusiveEnabled.collectAsStateWithLifecycle()
    val internalDirectEnabled by viewModel.internalDirectEnabled.collectAsStateWithLifecycle()
    val forceSoftwareVolume by viewModel.forceSoftwareVolume.collectAsStateWithLifecycle()
    val internalDirectAvailability by viewModel.internalDirectAvailability.collectAsStateWithLifecycle()
    val loudness by viewModel.loudness.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val discordPresenceEnabled by viewModel.discordPresenceEnabled.collectAsStateWithLifecycle()
    val isLastFmConnected by viewModel.isLastFmConnected.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val lastFmAuthUrl by viewModel.lastFmAuthUrl.collectAsStateWithLifecycle()
    val lastFmConnecting by viewModel.lastFmConnecting.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Last.fm web auth (Settings → Integrations): open the auth URL in Custom
    // Tabs the moment SettingsViewModel produces one.
    androidx.compose.runtime.LaunchedEffect(lastFmAuthUrl) {
        lastFmAuthUrl?.let { url ->
            runCatching {
                androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                    .launchUrl(context, android.net.Uri.parse(url))
            }.onFailure {
                viewModel.showToast(context.getString(R.string.toast_no_browser_lfm))
                viewModel.cancelLastFmConnect()
            }
        }
    }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showAutoEq by remember { mutableStateOf(false) }
    var showLyricsProviderDialog by remember { mutableStateOf(false) }
    var showLyricsOffsetDialog by remember { mutableStateOf(false) }
    var showLyricsSizeDialog by remember { mutableStateOf(false) }
    var showLyricsAutoHideDialog by remember { mutableStateOf(false) }
    var showTranslationLanguageDialog by remember { mutableStateOf(false) }
    var showLoudnessDialog by remember { mutableStateOf(false) }
    var showClarityPresetDialog by remember { mutableStateOf(false) }
    var showSyncPlaylistsSheet by remember { mutableStateOf(false) }
    var showYtLibraryVisibilitySheet by remember { mutableStateOf(false) }
    var showYtChannelSheet by remember { mutableStateOf(false) }
    var showYtDisconnectConfirm by remember { mutableStateOf(false) }
    var showSpotifyDisconnectConfirm by remember { mutableStateOf(false) }
    var showCookieLoginDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var activeToggleDetail by remember { mutableStateOf<ToggleDetailInfo?>(null) }
    val currentLanguage = remember(misc.appLanguageTag) { AppLanguage.fromTag(misc.appLanguageTag) }

    // Sends the user to Android's own Notification Listener access screen
    // — the one permission this feature needs that the app can never grant

    // "*/*" rather than "application/json": many document providers (Drive,
    // Downloads, some file managers) report a .json file as
    // application/octet-stream or text/plain, and GetContent's mime filter
    // hides anything that doesn't match — the user's own backup file would
    // silently not show up. Real validation happens right after the file is
    // read (stagePendingRestore), so being permissive here is safe.
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.handleRestorePicked(uri)
        }
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.handleCsvPicked(uri)
        }
    }

    var showYouTubeImportSheet by remember { mutableStateOf(false) }

    // Lets the user pick exactly where the backup file is saved (SAF), so
    // it's guaranteed to be somewhere restoreLauncher's picker can browse
    // back to later — unlike a silent write into app-private storage.
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri, appVersionName(context))
    }

    CompositionLocalProvider(
        LocalToggleDetailViewer provides { activeToggleDetail = it },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 760.dp),
        ) {
        var activeTab by remember { mutableStateOf<SettingsTab?>(null) }
        var isSearchBarVisible by rememberSaveable { mutableStateOf(false) }
        var highlightedSettingId by remember { mutableStateOf<String?>(null) }
        val focusManager = LocalFocusManager.current

        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

        LaunchedEffect(highlightedSettingId) {
            if (highlightedSettingId != null) {
                delay(3200L)
                highlightedSettingId = null
            }
        }

        BackHandler(enabled = searchQuery.isNotBlank() || isSearchBarVisible || activeTab != null) {
            if (searchQuery.isNotBlank() || isSearchBarVisible) {
                viewModel.clearSearch()
                isSearchBarVisible = false
            } else if (activeTab != null) {
                activeTab = null
            }
        }

        ScreenHeader(
            title = if (searchQuery.isNotBlank()) stringResource(R.string.settings_search_title) else (activeTab?.title ?: stringResource(R.string.settings)),
            onBack = {
                if (searchQuery.isNotBlank() || isSearchBarVisible) {
                    viewModel.clearSearch()
                    isSearchBarVisible = false
                } else if (activeTab != null) {
                    activeTab = null
                } else {
                    onBack()
                }
            },
            actions = {
                HeaderActionIcon(
                    icon = if (isSearchBarVisible || searchQuery.isNotBlank()) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (isSearchBarVisible || searchQuery.isNotBlank()) stringResource(R.string.settings_search_clear) else stringResource(R.string.settings_search_title),
                    onClick = {
                        if (isSearchBarVisible || searchQuery.isNotBlank()) {
                            viewModel.clearSearch()
                            isSearchBarVisible = false
                        } else {
                            isSearchBarVisible = true
                        }
                    },
                )
            },
        )

        AnimatedVisibility(
            visible = isSearchBarVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SettingsSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = {
                    if (searchQuery.isNotBlank()) {
                        viewModel.clearSearch()
                    } else {
                        isSearchBarVisible = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (searchQuery.isNotBlank()) {
            SettingsSearchResultsList(
                query = searchQuery,
                results = searchResults,
                onResultClick = { entry ->
                    focusManager.clearFocus()
                    viewModel.clearSearch()
                    isSearchBarVisible = false
                    activeTab = entry.parentTab
                    highlightedSettingId = entry.id
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                if (targetState != null && initialState == null) {
                    (slideInHorizontally { width -> width / 4 } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width / 4 } + fadeOut(),
                    )
                } else if (targetState == null && initialState != null) {
                    (slideInHorizontally { width -> -width / 4 } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width / 4 } + fadeOut(),
                    )
                } else {
                    fadeIn().togetherWith(fadeOut())
                }
            },
            label = "SettingsTabTransition",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { tab ->
            val listState = remember(tab) { LazyListState() }

            LaunchedEffect(highlightedSettingId, tab) {
                if (highlightedSettingId != null && tab != null) {
                    val targetIndex = getTargetSectionIndex(highlightedSettingId)
                    if (targetIndex > 0) {
                        delay(80L)
                        listState.animateScrollToItem(targetIndex)
                    } else if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                        delay(80L)
                        listState.animateScrollToItem(0)
                    }
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 32.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize().safeHorizontalContentPadding(),
            ) {
                when (tab) {
                    null -> {

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionLabel("Playback & UI")
                                SettingsGroup(rowCount = 3) { index, position ->
                                    when (index) {
                                        1 -> SettingsActionCard(
                                            icon = Icons.Filled.Palette,
                                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            title = stringResource(R.string.set_cat_appear),
                                            subtitle = stringResource(R.string.set_cat_appear_sub),
                                            onClick = { activeTab = SettingsTab.APPEARANCE },
                                            position = position,
                                        )
                                        0 -> SettingsActionCard(
                                            icon = Icons.Filled.GraphicEq,
                                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            title = stringResource(R.string.set_cat_audio),
                                            subtitle = stringResource(R.string.set_cat_audio_sub),
                                            onClick = { activeTab = SettingsTab.AUDIO },
                                            position = position,
                                        )
                                        else -> SettingsActionCard(
                                            icon = Icons.Filled.QueueMusic,
                                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            title = stringResource(R.string.set_cat_lib),
                                            subtitle = run {
                                                val count = downloadCount
                                                if (count > 0) "$count songs downloaded • Imports, Home layout" else "Home layout, Playlist imports, Downloads, Exclusions"
                                            },
                                            onClick = { activeTab = SettingsTab.LIBRARY },
                                            position = position,
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionLabel("Your music services")
                                SettingsGroup(rowCount = 3) { index, position ->
                                    when (index) {
                                        0 -> SettingsActionCard(
                                            icon = Icons.Filled.CloudSync,
                                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            title = stringResource(R.string.set_cat_yt),
                                            subtitle = stringResource(R.string.set_cat_yt_sub),
                                            onClick = { activeTab = SettingsTab.YOUTUBE },
                                            position = position,
                                        )
                                        1 -> SettingsActionCard(
                                            icon = Icons.Filled.PlayCircle,
                                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            title = stringResource(R.string.set_cat_spotify),
                                            subtitle = stringResource(R.string.set_cat_spotify_sub),
                                            onClick = { activeTab = SettingsTab.SPOTIFY },
                                            position = position,
                                        )
                                        2 -> SettingsActionCard(
                                            icon = Icons.Filled.Album,
                                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            title = stringResource(R.string.set_cat_lfm),
                                            subtitle = stringResource(R.string.set_cat_lfm_sub),
                                            onClick = { activeTab = SettingsTab.LAST_FM },
                                            position = position,
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionLabel("System & About")
                                SettingsGroup(rowCount = 1) { _, position ->
                        SettingsActionCard(
                            icon = Icons.Filled.Code,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.settings_diagnostics),
                            subtitle = stringResource(R.string.settings_diagnostics_sub),
                            onClick = { viewModel.exportDiagnostics() },
                            position = position,
                            isHighlighted = (highlightedSettingId == "about.diagnostics"),
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    // Prominent Update Available Banner Card (if newer version detected)
                    if (updateInfo.isUpdateAvailable) {
                        Surface(
                            shape = CardOuterShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CardOuterShape)
                                .clickable { viewModel.openUpdate(context) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Update Available!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = "Version ${updateInfo.latestVersion} is ready to install",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        text = "Update",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    AboutCard(versionName = appVersionName(context))

                    SettingsActionCard(
                        icon = Icons.Filled.CloudDownload,
                        iconContainer = if (updateInfo.isUpdateAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = if (updateInfo.isUpdateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                        title = if (updateInfo.isUpdateAvailable) "Update Ready (${updateInfo.latestVersion})" else "Check for Updates",
                        subtitle = when {
                            updateInfo.isChecking -> "Checking GitHub releases..."
                            updateInfo.isUpdateAvailable -> {
                                val fileName = updateInfo.downloadFileName
                                if (!fileName.isNullOrBlank()) "Tap to download $fileName" else "Tap to download new version"
                            }
                            !updateInfo.message.isNullOrBlank() -> updateInfo.message.orEmpty()
                            else -> "Current version: ${appVersionName(context)}"
                        },
                        onClick = {
                            if (updateInfo.isUpdateAvailable) {
                                viewModel.openUpdate(context)
                            } else {
                                viewModel.checkForUpdates()
                            }
                        },
                        isHighlighted = (highlightedSettingId == "about.check_updates"),
                    )

                    SettingsActionCard(
                        icon = Icons.Filled.Person,
                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = "Built by ItzFallenMe",
                        subtitle = "github.com/ItzFallenMe",
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/ItzFallenMe"),
                            )
                            if (!startActivitySafely(context, intent)) {
                                viewModel.showToast(context.getString(R.string.toast_no_browser_plain))
                            }
                        },
                        isHighlighted = (highlightedSettingId == "about.creator"),
                    )

                    SettingsActionCard(
                        icon = Icons.Filled.Code,
                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.settings_source_code),
                        subtitle = "github.com/ItzFallenMe/KatMusic",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ItzFallenMe/KatMusic"))
                            if (!startActivitySafely(context, intent)) {
                                viewModel.showToast(context.getString(R.string.toast_no_browser_plain))
                            }
                        },
                        isHighlighted = (highlightedSettingId == "about.source_code"),
                    )

                }
            }
                    }
                }
            }
        }
        }
    }
    }

    // -- Custom color wheel dialog (par 8.4) --
    if (state.showColorWheel) {
        ColorWheelSheet(onDismiss = viewModel::dismissColorWheel, onApply = viewModel::applyCustomColor)
    }

    // -- Clear-all-data confirm --
    if (state.showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAllConfirm,
            title = { Text(stringResource(R.string.dialog_clear_all_title)) },
            text = { Text(stringResource(R.string.dialog_clear_all_text)) },
            confirmButton = { TextButton(onClick = { viewModel.confirmClearAllData(onLoggedOut) }) { Text(stringResource(R.string.dialog_clear_all_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAllConfirm) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // -- YouTube Music disconnect confirm --
    if (showYtDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showYtDisconnectConfirm = false },
            title = { Text(stringResource(R.string.dialog_disconnect_yt_title)) },
            text = { Text(stringResource(R.string.dialog_disconnect_yt_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showYtDisconnectConfirm = false
                    viewModel.disconnectYouTube()
                }) { Text(stringResource(R.string.common_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { showYtDisconnectConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    // -- Spotify disconnect confirm --
    if (showSpotifyDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showSpotifyDisconnectConfirm = false },
            title = { Text("Disconnect Spotify?") },
            text = { Text("This will remove your Spotify session and log you out. Spotify Canvas loops and synced playlists will be disabled until you reconnect.") },
            confirmButton = {
                TextButton(onClick = {
                    showSpotifyDisconnectConfirm = false
                    viewModel.disconnectSpotify()
                    runCatching {
                        val cm = android.webkit.CookieManager.getInstance()
                        for (domain in listOf("https://spotify.com", "https://accounts.spotify.com", "https://open.spotify.com")) {
                            val cookies = cm.getCookie(domain) ?: continue
                            for (cookie in cookies.split(";")) {
                                val name = cookie.substringBefore("=").trim()
                                if (name.isNotEmpty()) {
                                    cm.setCookie(domain, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/")
                                }
                            }
                        }
                        cm.flush()
                    }
                }) { Text(stringResource(R.string.common_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { showSpotifyDisconnectConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showCookieLoginDialog) {
        CookieLoginDialog(
            onDismiss = { showCookieLoginDialog = false },
            onConnect = { cookies ->
                viewModel.connectWithCookies(cookies) { success, _ ->
                    if (success) showCookieLoginDialog = false
                }
            },
        )
    }

    // -- Restore confirm --
    if (state.showRestoreConfirm) {        val isPlaylistMirror = state.pendingRestoreKind == PendingRestoreKind.PLAYLIST_MIRROR
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreConfirm,
            title = { Text(if (isPlaylistMirror) "Sync playlist JSON?" else "Restore backup?") },
            text = {
                Text(
                    if (isPlaylistMirror) {
                        "This will merge ${state.pendingRestorePlaylistCount ?: 0} playlist(s) from the local JSON file and reconnect automatic syncing."
                    } else {
                        "This will replace your current data with ${state.pendingRestorePlaylistCount ?: 0} playlist(s) and all settings from the backup file."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmRestore(onBack) }) { Text(if (isPlaylistMirror) stringResource(R.string.common_sync) else stringResource(R.string.common_restore)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissRestoreConfirm) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // -- App language picker (Settings -> Language) styled like reference screenshot --
    if (showLanguageDialog) {
        var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_language_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(18.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                        ) {
                            items(AppLanguage.SELECTABLE) { language ->
                                val isSelected = language == selectedLanguage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .clickable { selectedLanguage = language }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (language == AppLanguage.SYSTEM) {
                                        Icon(
                                            Icons.Filled.Android,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    } else {
                                        val flag = when (language) {
                                            AppLanguage.ENGLISH -> "🇺🇸"
                                            AppLanguage.TURKISH -> "🇹🇷"
                                            AppLanguage.CHINESE_SIMPLIFIED -> "🇨🇳"
                                            AppLanguage.RUSSIAN -> "🇷🇺"
                                            AppLanguage.PORTUGUESE_BRAZIL -> "🇧🇷"
                                            AppLanguage.SPANISH -> "🇪🇸"
                                            AppLanguage.INDONESIAN -> "🇮🇩"
                                            AppLanguage.HINDI -> "🇮🇳"
                                            AppLanguage.GERMAN -> "🇩🇪"
                                            AppLanguage.FRENCH -> "🇫🇷"
                                            AppLanguage.ITALIAN -> "🇮🇹"
                                            AppLanguage.JAPANESE -> "🇯🇵"
                                            AppLanguage.KOREAN -> "🇰🇷"
                                            AppLanguage.ARABIC -> "🇸🇦"
                                            else -> "🌐"
                                        }
                                        Text(
                                            flag,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        language.nativeDisplayName(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedLanguage = language },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { showLanguageDialog = false },
                            shape = CircleShape,
                        ) {
                            Text(
                                stringResource(R.string.common_cancel),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.setAppLanguage(selectedLanguage)
                                showLanguageDialog = false
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            Text(
                                stringResource(android.R.string.ok),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    // -- Enable Scrobbling password dialog --
    if (state.showSessionKeyDialog) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissSessionKeyDialog,
            title = { Text(stringResource(R.string.set_scrobble_title)) },
            text = {
                Column {
                    Text(
                        "Last.fm only allows scrobbling through a signed session, and the only way to get one without a browser is with your password. It's sent once, directly to Last.fm over HTTPS, and never stored.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.set_scrobble_pass)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = state.sessionKeyError != null,
                        supportingText = state.sessionKeyError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submitPassword(password) },
                    enabled = !state.sessionKeyLoading,
                ) {
                    if (state.sessionKeyLoading) {
                        com.itzfallenme.katmusic.ui.components.ExpressiveInlineLoadingIndicator(size = 18.dp)
                    } else {
                        Text(stringResource(R.string.set_scrobble_enable))
                    }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissSessionKeyDialog) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showAutoEq) {
        com.itzfallenme.katmusic.ui.screens.settings.autoeq.AutoEqDialog(
            onDismiss = { showAutoEq = false },
            onOpenGraphicEq = {
                showAutoEq = false
                showEqSheet = true
            },
        )
    }


    // -- Experimental 15-band equalizer --
    if (showEqSheet) {
        EqualizerSheet(
            eq = eq,
            onDismiss = { showEqSheet = false },
            onSetEnabled = viewModel::setEqualizerEnabled,
            onPickPreset = viewModel::applyEqPreset,
            onBandPreview = viewModel::previewEqBandGain,
            onBandChange = viewModel::setEqBandGain,
            onOpenAutoEq = {
                showEqSheet = false
                showAutoEq = true
            },
        )
    }



    // -- Lyrics provider picker: preferred source first, automatic
    // fallback to the rest when it returns nothing --
    if (showLyricsProviderDialog) {
        LyricsProviderDialog(
            current = misc.lyricsProvider,
            onSelect = {
                viewModel.setLyricsProvider(it)
                showLyricsProviderDialog = false
            },
            onDismiss = { showLyricsProviderDialog = false },
        )
    }

    // -- Lyrics sync offset stepper: shifts highlight/focus only --
    if (showLyricsOffsetDialog) {
        LyricsOffsetDialog(
            currentMs = misc.lyricsOffsetMs,
            onSelect = { viewModel.setLyricsOffsetMs(it) },
            onDismiss = { showLyricsOffsetDialog = false },
        )
    }

    // -- Lyrics text size dialog: scales font size with live preview --
    if (showLyricsSizeDialog) {
        LyricsSizeDialog(
            currentScale = misc.lyricsFontScale,
            onSelect = { viewModel.setLyricsFontScale(it) },
            onDismiss = { showLyricsSizeDialog = false },
        )
    }

    // -- Floating lyrics buttons: idle time before they fade out --
    if (showLyricsAutoHideDialog) {
        LyricsAutoHideDialog(
            currentSeconds = misc.lyricsButtonsAutoHideSeconds,
            onSelect = {
                viewModel.setLyricsButtonsAutoHideSeconds(it)
                showLyricsAutoHideDialog = false
            },
            onDismiss = { showLyricsAutoHideDialog = false },
        )
    }

    // -- Language the lyrics translate button turns songs into --
    if (showTranslationLanguageDialog) {
        TranslationLanguageDialog(
            current = misc.translationLanguage,
            onSelect = {
                viewModel.setTranslationLanguage(it)
                showTranslationLanguageDialog = false
            },
            onDismiss = { showTranslationLanguageDialog = false },
        )
    }

    if (showLoudnessDialog) {
        LoudnessModeDialog(
            current = loudness.mode,
            onSelect = {
                viewModel.setLoudnessMode(it)
                showLoudnessDialog = false
            },
            onDismiss = { showLoudnessDialog = false },
        )
    }

    if (showClarityPresetDialog) {
        ClarityPresetDialog(
            currentIndex = misc.clarityPreset,
            onSelect = {
                viewModel.setClarityPreset(it)
                showClarityPresetDialog = false
            },
            onDismiss = { showClarityPresetDialog = false },
        )
    }

    // -- Selective Playlist Sync sheet --
    if (showSyncPlaylistsSheet) {
        SyncPlaylistsSheet(
            playlists = allPlaylists,
            syncedIds = syncedPlaylistIds,
            onToggleSync = viewModel::togglePlaylistSync,
            onSelectAll = viewModel::selectAllPlaylistsForSync,
            onDismiss = { showSyncPlaylistsSheet = false },
        )
    }

    if (showYtLibraryVisibilitySheet) {
        YouTubeLibraryVisibilitySheet(
            playlists = ytAccountPlaylists,
            hiddenIds = hiddenYtLibraryPlaylistIds,
            onSetVisible = viewModel::setYtLibraryPlaylistVisible,
            onSetAllVisible = viewModel::setAllYtLibraryPlaylistsVisible,
            onDismiss = { showYtLibraryVisibilitySheet = false },
        )
    }

    if (showYtChannelSheet) {
        YouTubeChannelSheet(
            channels = ytChannels,
            isLoading = ytChannelsLoading,
            selectedChannelId = ytConnection.onBehalfOfUser,
            selectedAuthUser = ytConnection.authUserIndex,
            selectedPageId = ytConnection.pageId,
            onReload = viewModel::loadYtChannels,
            onSelect = {
                viewModel.selectYtChannel(it)
                showYtChannelSheet = false
            },
            onDismiss = { showYtChannelSheet = false },
        )
    }

    if (showQualityDialog) {
        val tiers = listOf(
            Triple(28, "Dolby Atmos", "Spatial Immersive Audio • Master Audio" to "ATMOS"),
            Triple(27, "Hi-Res Audio (Max)", "Up to 24-bit / 192 kHz • Lossless Studio FLAC" to "24-BIT / 192k"),
            Triple(7, "Hi-Res Audio", "24-bit / 96 kHz • Lossless Studio FLAC" to "24-BIT / 96k"),
            Triple(6, "CD Lossless", "16-bit / 44.1 kHz • Lossless CD FLAC" to "16-BIT / 44.1k"),
            Triple(5, "Standard Quality", "320 kbps • MP3 / AAC" to "320 kbps"),
            Triple(4, "Data Saver", "96 kbps • High Efficiency AAC" to "96 kbps"),
            Triple(-1, "YouTube Music", "128-256 kbps • YouTube Music AAC / Opus stream" to "YOUTUBE"),
        )
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        ModalBottomSheet(
            onDismissRequest = { showQualityDialog = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp),
                ) {}
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .adaptiveContentWidth(maxWidth = 640.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp + safeDrawingBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.HighQuality,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Streaming Quality",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Select preferred audio resolution & bit depth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "YouTube Music plays strictly as-is (never upgrades). Dolby Atmos plays only when selected — otherwise it never plays. Atmos falls back to Hi-Res → CD → 320 kbps → YouTube. Hi-Res steps down to lower lossless tiers, then YouTube last.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiers.forEach { (qualityId, title, meta) ->
                        val (subtitle, badge) = meta
                        val isSelected = misc.losslessQuality == qualityId
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                viewModel.setLosslessQuality(qualityId)
                                showQualityDialog = false
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            shadowElevation = if (isSelected) 3.dp else 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                                        ) {
                                            Text(
                                                badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.set_selected_cd),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadQualityDialog) {
        val downloadTiers = listOf(
            Triple(28, "Dolby Atmos", "Spatial Immersive Audio • Master Audio" to "ATMOS"),
            Triple(27, "Hi-Res Audio (Max)", "Up to 24-bit / 192 kHz • Studio Master FLAC" to "24-BIT / 192k"),
            Triple(7, "Hi-Res Audio", "24-bit / 96 kHz • Studio FLAC" to "24-BIT / 96k"),
            Triple(6, "CD Lossless", "16-bit / 44.1 kHz • Bit-Exact CD FLAC" to "16-BIT / 44.1k"),
            Triple(5, "Standard Quality", "320 kbps • High-Bitrate MP3 / AAC" to "320 kbps"),
            Triple(4, "Data Saver", "96 kbps • High Efficiency AAC" to "96 kbps"),
            Triple(-1, "YouTube Music", "128-256 kbps • YouTube Music AAC / Opus stream" to "YOUTUBE"),
        )
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        ModalBottomSheet(
            onDismissRequest = { showDownloadQualityDialog = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp),
                ) {}
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .adaptiveContentWidth(maxWidth = 640.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp + safeDrawingBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Download Quality",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Select offline audio resolution & bit depth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "YouTube Music downloads strictly as-is (never upgrades). Dolby Atmos downloads only when selected — otherwise it never downloads. Atmos falls back to Hi-Res → CD → 320 kbps → YouTube. Hi-Res steps down to lower tiers, then YouTube last.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    downloadTiers.forEach { (qualityId, title, meta) ->
                        val (subtitle, badge) = meta
                        val isSelected = misc.downloadQuality == qualityId
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                viewModel.setDownloadQuality(qualityId)
                                showDownloadQualityDialog = false
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            shadowElevation = if (isSelected) 3.dp else 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                                        ) {
                                            Text(
                                                badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.set_selected_cd),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showYouTubeImportSheet) {
        YouTubeImportSheet(
            onDismiss = { showYouTubeImportSheet = false },
            innerTube = viewModel.innerTube,
            importManager = viewModel.playlistImportManager,
            onImportSuccess = { saved ->
                viewModel.showToast(context.getString(R.string.toast_imported, saved.title, saved.tracks.size))
            },
        )
    }

    state.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.dismissToast()
        }
        Box(
            Modifier
                .fillMaxSize()
                .safeHorizontalContentPadding()
                .padding(
                    bottom = 24.dp +
                        LocalMiniPlayerScrollClearance.current +
                        safeDrawingBottomPadding(),
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(shape = ExpressivePillShape, color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 6.dp) {
                Text(msg, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }

    if (activeToggleDetail != null) {
        val detail = activeToggleDetail!!
        Dialog(
            onDismissRequest = { activeToggleDetail = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Scrim background (unblurred content on top)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { activeToggleDetail = null },
                        ),
                )
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .widthIn(max = 420.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (detail.checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = if (detail.checked) Icons.Filled.Check else Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = if (detail.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (detail.checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            ) {
                                Text(
                                    text = if (detail.checked) "Enabled" else "Disabled",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (detail.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = detail.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp,
                        )
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(
                            onClick = { activeToggleDetail = null },
                            modifier = Modifier.align(Alignment.End),
                            shape = CircleShape,
                        ) {
                            Text("Close", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun appVersionName(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "4.3.1"
} catch (error: Exception) {
    "4.3.1"
} catch (error: LinkageError) {
    "4.3.1"
}

/** Small tap-scale used across the row-style cards on this screen for a
 *  softer, springier press response than the plain ripple alone gives. */
@Composable
private fun rememberPressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return scale
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun IconBadge(icon: ImageVector, container: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun IconBadge(painter: Painter, container: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ThemeModeSelectorCard(
    currentThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
) {
    val shape = groupShape(position)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Filled.BrightnessAuto,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_theme_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_theme_mode_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val items = listOf(
                ConnectedButtonItem(stringResource(R.string.theme_mode_system), Icons.Filled.BrightnessAuto),
                ConnectedButtonItem(stringResource(R.string.theme_mode_light), Icons.Filled.LightMode),
                ConnectedButtonItem(stringResource(R.string.theme_mode_dark), Icons.Filled.DarkMode),
            )
            val selectedIdx = when (currentThemeMode) {
                ThemeMode.SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
            }
            ConnectedButtonGroup(
                items = items,
                selectedIndex = selectedIdx,
                onSelect = { idx ->
                    val mode = when (idx) {
                        0 -> ThemeMode.SYSTEM
                        1 -> ThemeMode.LIGHT
                        else -> ThemeMode.DARK
                    }
                    onSelectThemeMode(mode)
                },
            )
        }
    }
}

@Composable
private fun SettingsToggleCard(
    painter: Painter,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    enabled: Boolean = true,
    isHighlighted: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val shape = groupShape(position)
    val haptic = LocalHapticFeedback.current
    val detailViewer = LocalToggleDetailViewer.current

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (enabled) scale else 1f
                scaleX = s
                scaleY = s
            }
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    detailViewer?.invoke(ToggleDetailInfo(title, subtitle, checked))
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                painter,
                if (enabled) iconContainer else iconContainer.copy(alpha = 0.5f),
                if (enabled) iconTint else iconTint.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = if (enabled) onCheckedChange else null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun SettingsToggleCard(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    enabled: Boolean = true,
    isHighlighted: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val shape = groupShape(position)
    val haptic = LocalHapticFeedback.current
    val detailViewer = LocalToggleDetailViewer.current

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (enabled) scale else 1f
                scaleX = s
                scaleY = s
            }
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    detailViewer?.invoke(ToggleDetailInfo(title, subtitle, checked))
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon,
                if (enabled) iconContainer else iconContainer.copy(alpha = 0.5f),
                if (enabled) iconTint else iconTint.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = if (enabled) onCheckedChange else null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else null,
            )
        }
    }
}



@Composable
private fun AutomixBarsRow(
    bars: Int,
    onBarsChange: (Int) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = groupShape(position)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Filled.AutoAwesome,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Automix Transition Length", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        if (bars >= 16) "16 bars \u2022 Extended club blend" else "8 bars \u2022 Standard DJ transition",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = bars <= 8,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onBarsChange(8)
                    },
                    label = { Text("8 Bars (Standard)", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = bars >= 16,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onBarsChange(16)
                    },
                    label = { Text("16 Bars (Extended)", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CrossfadeDurationRow(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var sliderValue by remember { mutableStateOf(seconds.coerceIn(1, 12).toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(seconds) {
        if (!isDragging) sliderValue = seconds.coerceIn(1, 12).toFloat()
    }
    val shape = groupShape(position)

    val blendStyle = when (sliderValue.roundToInt()) {
        in 1..2 -> "Quick DJ overlap"
        in 3..5 -> "Standard smooth blend"
        in 6..8 -> "Long musical transition"
        else -> "Extended cinematic crossfade"
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Filled.Tune,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_xfade_dur), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "${sliderValue.roundToInt()}s \u2022 $blendStyle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "${sliderValue.roundToInt()}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    isDragging = true
                    val rounded = value.roundToInt().coerceIn(1, 12)
                    if (rounded.toFloat() != sliderValue) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        sliderValue = rounded.toFloat()
                        onSecondsChange(rounded)
                    }
                },
                onValueChangeFinished = {
                    isDragging = false
                },
                valueRange = 1f..12f,
                steps = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.set_xfade_1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.set_xfade_6), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.set_xfade_12), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val titleColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val shape = groupShape(position)

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, iconContainer, iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ==========================================
// SETTINGS SEARCH COMPOSABLES
// ==========================================

/**
 * Builds an [AnnotatedString] from [text], highlighting ranges from [ranges]
 * with primary bold styling for search query matches.
 */
private fun buildHighlightedText(
    text: String,
    ranges: List<IntRange>,
    highlightColor: Color,
): AnnotatedString {
    if (ranges.isEmpty() || text.isEmpty()) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var lastIndex = 0
        for (range in ranges) {
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(0, text.length)
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            if (end > start) {
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(text.substring(start, end))
                }
                lastIndex = end
            }
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        placeholder = {
            Text(
                stringResource(R.string.settings_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}

@Composable
private fun SettingsSearchResultsList(
    query: String,
    results: List<MatchResult>,
    onResultClick: (SettingsEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize().safeHorizontalContentPadding(),
    ) {
        if (results.isEmpty()) {
            item {
                SettingsSearchEmptyState(query = query)
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${results.size} setting${if (results.size == 1) "" else "s"} found",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(results, key = { it.entry.id }) { result ->
                SearchResultCard(
                    result = result,
                    onClick = { onResultClick(result.entry) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: MatchResult,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val highlightColor = MaterialTheme.colorScheme.primary

    val annotatedTitle = remember(result.entry.title, result.titleMatchedRanges, highlightColor) {
        buildHighlightedText(result.entry.title, result.titleMatchedRanges, highlightColor)
    }

    val annotatedSubtitle = remember(result.entry.subtitle, result.subtitleMatchedRanges, highlightColor) {
        buildHighlightedText(result.entry.subtitle, result.subtitleMatchedRanges, highlightColor)
    }

    Card(
        onClick = onClick,
        shape = CardOuterShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = "${result.entry.parentTab.title} › ${result.entry.section}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    result.entry.icon,
                    result.entry.iconContainer(),
                    result.entry.iconTint(),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = annotatedTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = annotatedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchEmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_search_no_results, query),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_search_no_results_sub),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YouTubeAccountRow(
    accountName: String,
    channelHandle: String?,
    onDisconnect: () -> Unit,
    position: GroupPosition,
    isHighlighted: Boolean = false,
) {
    val shape = groupShape(position)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                Icons.Filled.SmartDisplay,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "YouTube Music Account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    accountName.ifBlank { "Connected" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (!channelHandle.isNullOrBlank()) {
                    Text(
                        channelHandle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onDisconnect,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.set_disconnect_cd))
            }
        }
    }
}

@Composable
private fun SpotifyAccountRow(
    onDisconnect: () -> Unit,
    onClick: () -> Unit,
    position: GroupPosition,
    isHighlighted: Boolean = false,
) {
    val shape = groupShape(position)
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                Icons.Filled.PlayCircle,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Spotify Account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Connected (sp_dc)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "Tap to manage session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onDisconnect,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.set_disconnect_cd))
            }
        }
    }
}

private fun lastSyncSuffix(lastSyncAtMillis: Long): String {
    if (lastSyncAtMillis <= 0L) return ""
    return " \u2022 synced ${relativeTime(lastSyncAtMillis)}"
}

private fun relativeTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "never"
    val delta = System.currentTimeMillis() - timestampMillis
    val minutes = delta / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

private const val LAST_FM_CREATE_KEY_URL = "https://www.last.fm/api/account/create"

@Composable
private fun LastFmIntegrationCard(
    isConnected: Boolean,
    username: String,
    avatarUrl: String?,
    connecting: Boolean,
    awaitingApproval: Boolean,
    hasApiKey: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onSaveKeys: (String, String) -> Unit,
    onRemoveKey: () -> Unit,
    onOpenCreateKeyPage: () -> Unit,
    isHighlighted: Boolean = false,
) {
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    // No shared key exists, so the form starts open until a key is saved.
    var showKeyForm by remember(hasApiKey) { mutableStateOf(!hasApiKey) }
    var keyInput by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }

    Card(
        shape = CardOuterShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = CardOuterShape),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isConnected) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isConnected) {
                    if (avatarUrl != null) {
                        coil.compose.AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        IconBadge(
                            Icons.Filled.CloudSync,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isConnected && username.isNotBlank()) username.take(1).uppercase() else "L",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Last.fm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (isConnected && username.isNotBlank()) username else "Not connected",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (!isConnected) {
                        Text(
                            "Optional • Stats use local listening when disconnected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isConnected) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { showDisconnectConfirm = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.set_disconnect_cd))
                    }
                }
            }
            if (!isConnected) {
                when {
                    awaitingApproval || connecting -> {
                        com.itzfallenme.katmusic.ui.components.LoadingPulse(
                            message = if (connecting) "Connecting to Last.fm…" else "Waiting for approval in the browser…",
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                    }
                    else -> {
                        Button(
                            onClick = onConnect,
                            enabled = hasApiKey,
                            shape = ExpressivePillShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.set_lfm_connect)) }
                        Text(
                            if (hasApiKey) "Approve in your browser. You can disconnect anytime — Stats keep working locally."
                            else "Add your API key below first, then connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!isConnected) {
                // ── Bring-your-own-key (required, no shared key): Last.fm
                //    rate-limits per API key, so each person adds their own key
                //    here before connecting.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.set_lfm_key_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (hasApiKey) stringResource(R.string.set_lfm_key_saved)
                            else stringResource(R.string.set_lfm_key_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showKeyForm = !showKeyForm }) {
                        Text(if (showKeyForm) "Hide" else if (hasApiKey) "Change" else "Add key")
                    }
                }
                if (showKeyForm) {
                    androidx.compose.material3.OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        label = { Text(stringResource(R.string.set_lfm_apikey)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it.trim() },
                        label = { Text(stringResource(R.string.set_lfm_secret)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onSaveKeys(keyInput, secretInput)
                                keyInput = ""
                                secretInput = ""
                                showKeyForm = false
                            },
                            enabled = keyInput.length >= 16 && secretInput.length >= 16,
                            shape = ExpressivePillShape,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.set_lfm_save)) }
                        if (hasApiKey) {
                            OutlinedButton(
                                onClick = onRemoveKey,
                                shape = ExpressivePillShape,
                            ) { Text(stringResource(R.string.set_lfm_remove)) }
                        }
                    }
                    TextButton(onClick = onOpenCreateKeyPage) {
                        Text(stringResource(R.string.set_lfm_get))
                    }
                } else {
                    TextButton(onClick = onOpenCreateKeyPage) {
                        Text(stringResource(R.string.set_lfm_how))
                    }
                }
            }
            }
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text(stringResource(R.string.set_lfm_disc_title)) },
            text = { Text(stringResource(R.string.set_lfm_disc_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    onDisconnect()
                }) { Text(stringResource(R.string.set_lfm_disc_btn)) }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun AccentPresetGrid(
    currentMode: AccentMode,
    selectedHex: String?,
    onPickPreset: (String) -> Unit,
    onPickMono: () -> Unit,
    onPickCustom: () -> Unit,
) {
    // A preset only reads as "selected" while the user is actually in
    // manual mode — Dynamic/Monochrome shouldn't light up whichever preset
    // happens to hex-match by coincidence.
    fun isPresetSelected(hex: String) =
        currentMode == AccentMode.MANUAL && selectedHex?.equals(hex, ignoreCase = true) == true
    val customSelected = currentMode == AccentMode.MANUAL &&
        selectedHex != null &&
        ACCENT_PRESETS.none { it.hex.equals(selectedHex, ignoreCase = true) }
    val monoSelected = currentMode == AccentMode.MONOCHROME

    // One unified 4-column tile grid — six color presets plus Mono and
    // Custom as tiles of their own, not a separate row of pill buttons.
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        ACCENT_PRESETS.take(4).forEach { preset ->
            ColorTile(
                label = preset.name,
                selected = isPresetSelected(preset.hex),
                modifier = Modifier.weight(1f),
                onClick = { onPickPreset(preset.hex) },
            ) {
                PaletteTilePreview(preset.hex)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        ACCENT_PRESETS.drop(4).forEach { preset ->
            ColorTile(
                label = preset.name,
                selected = isPresetSelected(preset.hex),
                modifier = Modifier.weight(1f),
                onClick = { onPickPreset(preset.hex) },
            ) {
                PaletteTilePreview(preset.hex)
            }
        }
        ColorTile(
            label = "Mono",
            selected = monoSelected,
            modifier = Modifier.weight(1f),
            onClick = onPickMono,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Contrast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        ColorTile(
            label = stringResource(R.string.set_eq_custom),
            selected = customSelected,
            modifier = Modifier.weight(1f),
            onClick = onPickCustom,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            listOf(Color(0xFFE03030), Color(0xFFE0A030), Color(0xFF6B9E6B), Color(0xFF2196C6), Color(0xFF7C4DFF), Color(0xFFE03030)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Colorize,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Four coordinated shades derived from one preset hex via HSV — Dark,
 * Medium, Light, and a punchier Accent tone — used to render a real
 * multi-tone palette preview inside each Accent tile instead of one flat
 * swatch. Order returned: [dark, medium, light, accent].
 *
 * Tuned for a muted, Material You / Monet feel rather than raw HSV
 * vibrance: saturation is capped well below 100% even for the "accent"
 * shade (Monet's HCT-derived tonal palettes rarely reach full chroma —
 * that's what read as neon here), and the value range is narrower so
 * "dark" and "light" stay closer to the preset's own character instead of
 * swinging to near-black/near-white extremes.
 */
private fun accentShades(hex: String): List<Color> {
    val argb = android.graphics.Color.parseColor(hex)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    val (h, s, v) = hsv
    val mutedBase = s * 0.72f // the single biggest lever for "less neon"

    fun shade(saturation: Float, value: Float): Color {
        val arr = floatArrayOf(h, saturation.coerceIn(0f, 0.82f), value.coerceIn(0.2f, 0.92f))
        return Color(android.graphics.Color.HSVToColor(arr))
    }

    return listOf(
        shade(mutedBase.coerceAtLeast(0.42f), v * 0.62f), // dark
        shade(mutedBase, v * 0.80f), // medium — closest to the preset's own tone
        shade((mutedBase * 0.55f), (v + (1f - v) * 0.45f).coerceAtLeast(0.78f)), // light
        shade((mutedBase * 1.15f), (v * 0.95f)), // accent — a touch richer, never maxed out
    )
}

/** Renders a preset's four shades as a 2x2 block grid filling the tile,
 *  so selecting a preset previews its whole coordinated palette rather
 *  than one flat color. */
@Composable
private fun PaletteTilePreview(hex: String) {
    val shades = remember(hex) { accentShades(hex) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(shades[0]))
            Box(Modifier.weight(1f).fillMaxHeight().background(shades[1]))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(shades[2]))
            Box(Modifier.weight(1f).fillMaxHeight().background(shades[3]))
        }
    }
}


/**
 * One expressive accent tile: a real elevated square swatch (Modifier.shadow
 * — a true drop shadow, not Card's tonal-elevation color blend, which would
 * otherwise wash out the exact color a swatch is supposed to preview),
 * a spring scale/elevation lift on selection, a genuine ripple on tap, and
 * an animated check badge. [content] draws the tile's fill — a flat color
 * for presets, an icon-on-surface treatment for Mono/Custom.
 */
@Composable
private fun ColorTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 8.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tileElevation",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tileBorder",
    )
    val tileShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // Real shadow, not tonal elevation — keeps every tile's
                // color a true, undistorted preview of the accent it
                // represents (see GenerateScreen's ModeCard fix for why
                // Card's own elevation param is the wrong tool for this).
                .shadow(elevation = elevation, shape = tileShape, clip = false)
                .clip(tileShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                .border(2.5.dp, borderColor, tileShape),
            contentAlignment = Alignment.Center,
        ) {
            content(selected)
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutCard(versionName: String) {
    Card(
        shape = CardOuterShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    // The launcher icon is an <adaptive-icon> XML on API 26+
                    // (mipmap-anydpi-v26/ic_launcher_round.xml) — Compose's
                    // painterResource() can only parse plain bitmap/vector
                    // drawables, not that root element, and throws the
                    // instant this composable enters composition. Rebuilding
                    // the same mark from its two real layers (the lime
                    // background color + the bars vector, both plain
                    // resources) reproduces it exactly without touching the
                    // adaptive icon resource at all.
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_logo),
                    contentDescription = "KatMusic",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(72.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("KatMusic", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = ExpressivePillShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "Version $versionName",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Built by ItzFallenMe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** OEM Settings/browser components are optional and occasionally broken on
 * custom ROMs. Never let an external activity failure escape a click event. */
private fun startActivitySafely(context: android.content.Context, intent: Intent): Boolean {
    val safeIntent = Intent(intent).apply {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(safeIntent)
        true
    } catch (_: Exception) {
        false
    } catch (_: LinkageError) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorWheelSheet(onDismiss: () -> Unit, onApply: (Color) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var hue by remember { mutableStateOf(4f) }
    var saturation by remember { mutableStateOf(0.75f) }
    var lightness by remember { mutableStateOf(0.5f) }
    val previewColor = Color.hsl(hue, saturation, lightness)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 560.dp)
                .align(Alignment.CenterHorizontally)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.set_color_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(previewColor),
            )
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.set_hue), style = MaterialTheme.typography.labelLarge)
            Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
            Text(stringResource(R.string.set_sat), style = MaterialTheme.typography.labelLarge)
            Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
            Text(stringResource(R.string.set_light), style = MaterialTheme.typography.labelLarge)
            Slider(value = lightness, onValueChange = { lightness = it }, valueRange = 0.15f..0.85f)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, shape = ExpressivePillShape, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = { onApply(previewColor) }, shape = ExpressivePillShape, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.common_apply)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// -- Experimental 15-band equalizer (Settings → Experimental → Equalizer) --

private const val EQ_MAX_DB = EQ_MAX_GAIN_DB

private fun eqBandCategory(hz: Int): String = when {
    hz <= 40 -> "SUB"
    hz <= 100 -> "BASS"
    hz <= 250 -> "LOW-MID"
    hz <= 1000 -> "MID"
    hz <= 2500 -> "HIGH-MID"
    hz <= 6300 -> "PRES"
    else -> "AIR"
}

@Composable
private fun EqualizerCurveGraph(
    gains: FloatArray,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val paddingX = 14.dp.toPx()
            val availableW = w - paddingX * 2f
            val baselineY = h / 2f
            val maxDbPx = (h - 22.dp.toPx()) / 2f

            val topY = baselineY - maxDbPx
            val bottomY = baselineY + maxDbPx

            // Baseline (0 dB)
            drawLine(
                color = if (enabled) outlineVariant else outlineVariant.copy(alpha = 0.15f),
                start = Offset(paddingX, baselineY),
                end = Offset(w - paddingX, baselineY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            )
            // +8 dB line
            drawLine(
                color = gridColor,
                start = Offset(paddingX, topY),
                end = Offset(w - paddingX, topY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f),
            )
            // -8 dB line
            drawLine(
                color = gridColor,
                start = Offset(paddingX, bottomY),
                end = Offset(w - paddingX, bottomY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f),
            )

            if (gains.isEmpty()) return@Canvas

            val count = gains.size
            val stepX = availableW / (count - 1).coerceAtLeast(1)
            val points = List(count) { i ->
                val x = paddingX + i * stepX
                val gain = if (enabled) gains[i].coerceIn(-EQ_MAX_DB, EQ_MAX_DB) else 0f
                val y = baselineY - (gain / EQ_MAX_DB) * maxDbPx
                Offset(x, y)
            }

            val path = Path()
            val fillPath = Path()

            path.moveTo(points.first().x, points.first().y)
            fillPath.moveTo(points.first().x, baselineY)
            fillPath.lineTo(points.first().x, points.first().y)

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlX1 = p0.x + (p1.x - p0.x) / 2f
                val controlY1 = p0.y
                val controlX2 = p0.x + (p1.x - p0.x) / 2f
                val controlY2 = p1.y
                path.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
            }

            fillPath.lineTo(points.last().x, baselineY)
            fillPath.close()

            if (enabled) {
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.35f),
                            primaryColor.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        startY = topY,
                        endY = bottomY,
                    ),
                )
            }

            drawPath(
                path = path,
                color = if (enabled) primaryColor else onSurfaceVariant.copy(alpha = 0.4f),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            for (p in points) {
                val hasBoostOrCut = Math.abs(p.y - baselineY) > 2f && enabled
                drawCircle(
                    color = if (hasBoostOrCut) primaryColor else if (enabled) primaryColor.copy(alpha = 0.7f) else onSurfaceVariant.copy(alpha = 0.3f),
                    radius = if (hasBoostOrCut) 4.5.dp.toPx() else 3.dp.toPx(),
                    center = p,
                )
                if (hasBoostOrCut) {
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = p,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(end = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text("+12dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("0dB", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text("-12dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

/**
 * 100% natural, native Material 3 Equalizer:
 * - Edge-to-edge layout with status bar and navigation bar insets protection
 * - Live dynamic Bézier Spline frequency response visualizer
 * - Hardware acoustic fader board with real-time numeric dB readouts and 0 dB center detent haptics
 * - Standard ISO center frequencies
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EqualizerSheet(
    eq: EqualizerSettings,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onPickPreset: (String) -> Unit,
    onBandPreview: (Int, Float) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onOpenAutoEq: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var gains by remember(eq.gainsDb) {
        mutableStateOf(
            FloatArray(EQ_BAND_FREQS_HZ.size) { index ->
                eq.gainsDb.getOrNull(index)
                    ?.takeIf { it.isFinite() }
                    ?.coerceIn(-EQ_MAX_DB, EQ_MAX_DB)
                    ?: 0f
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 640.dp)
                .align(Alignment.CenterHorizontally)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp + safeDrawingBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Equalizer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "15-band hardware acoustic tuning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onPickPreset(EqualizerPresets.FLAT.name)
                    },
                    enabled = eq.enabled,
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.set_eq_reset), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Mode Switcher: 15-Band Graphic vs AutoEQ / Parametric EQ
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "15-Band Graphic",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onOpenAutoEq()
                            }),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AutoEQ / PEQ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Master On/Off Switch Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_eq_enable), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            if (eq.enabled) "Shaping your music in real-time" else "Off \u2014 original audio passes through",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Switch(
                        checked = eq.enabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSetEnabled(enabled)
                        },
                    )
                }
            }

            // Real-Time Frequency Response Visualizer
            EqualizerCurveGraph(
                gains = gains,
                enabled = eq.enabled,
            )

            // Presets Horizontal Flow
            SectionLabel("Presets")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EqualizerPresets.ALL.forEach { preset ->
                    FilterChip(
                        selected = eq.presetName.equals(preset.name, ignoreCase = true),
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onPickPreset(preset.name)
                        },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelMedium) },
                    )
                }
                if (eq.presetName == EqualizerPresets.CUSTOM_NAME) {
                    FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.set_eq_custom), style = MaterialTheme.typography.labelMedium) })
                }
            }

            // Native Equalizer Fader Board
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val curveAlpha by animateFloatAsState(
                    targetValue = if (eq.enabled) 1f else 0.38f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "eqCurveAlpha",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .alpha(curveAlpha),
                ) {
                    // Top Scale Label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "+8 dB (Boost)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "-8 dB (Cut)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Scrollable Horizontal Row of Native Equalizer Faders
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EQ_BAND_FREQS_HZ.forEachIndexed { index, hz ->
                            EqNativeSlider(
                                gainDb = gains[index],
                                hz = hz,
                                enabled = eq.enabled,
                                onGainChange = { value ->
                                    gains = gains.copyOf().also { it[index] = value }
                                    onBandPreview(index, value)
                                },
                                onChangeFinished = { onBandChange(index, gains[index]) },
                            )
                        }
                    }
                }
            }

            Text(
                text = when {
                    !eq.enabled -> "Turn on to apply equalization live"
                    eq.presetName == EqualizerPresets.CUSTOM_NAME -> "Custom profile \u2022 touch and drag any bar up/down"
                    else -> "${eq.presetName} preset active \u2022 touch any bar to customize"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

/**
 * Goated native hardware equalizer fader bar:
 * - Vertical capsule track with central 0 dB baseline notch and active level gradient beam
 * - Tactile hardware capsule thumb knob with double grip ridges
 * - Real-time continuous numeric dB badge on top with container coloring
 * - Standard frequency label + band category tag underneath
 */
@Composable
private fun EqNativeSlider(
    gainDb: Float,
    hz: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onGainChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val normalized = ((gainDb + EQ_MAX_DB) / (EQ_MAX_DB * 2f)).coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    val currentGain by rememberUpdatedState(gainDb)
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val currentOnChangeFinished by rememberUpdatedState(onChangeFinished)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Numeric Gain on top in a small pill container
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                !enabled -> MaterialTheme.colorScheme.surfaceContainer
                gainDb > 0f -> MaterialTheme.colorScheme.primaryContainer
                gainDb < 0f -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ) {
            Text(
                text = if (gainDb > 0f) "+${"%.1f".format(gainDb)}" else "${"%.1f".format(gainDb)}",
                fontSize = 11.sp,
                fontWeight = if (gainDb != 0f) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    gainDb > 0f -> MaterialTheme.colorScheme.onPrimaryContainer
                    gainDb < 0f -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        // Vertical Track Box
        val trackHeight = 152.dp
        val thumbHeight = 24.dp
        val thumbWidth = 38.dp

        Box(
            modifier = Modifier
                .width(44.dp)
                .height(trackHeight)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            currentOnChangeFinished()
                        },
                        onDragCancel = {
                            isDragging = false
                            currentOnChangeFinished()
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaFraction = -dragAmount / size.height.toFloat()
                        val currentFraction = ((currentGain + EQ_MAX_DB) / (EQ_MAX_DB * 2f))
                        val newFraction = (currentFraction + deltaFraction).coerceIn(0f, 1f)
                        val newGain = (newFraction * EQ_MAX_DB * 2f - EQ_MAX_DB).let {
                            if (it in -0.3f..0.3f) 0f else (Math.round(it * 2f) / 2f)
                        }
                        if (newGain != currentGain) {
                            if (newGain == 0f && currentGain != 0f) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }
                            currentOnGainChange(newGain)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Center Baseline Line (0 dB)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )

            // Active Level Fill (from baseline to thumb)
            val baselineFraction = 0.5f
            val topFraction = if (normalized >= baselineFraction) 1f - normalized else 1f - baselineFraction
            val heightFraction = Math.abs(normalized - baselineFraction)

            if (heightFraction > 0.01f && enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .fillMaxHeight(heightFraction)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = size.height * topFraction
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (gainDb > 0f) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                )
                            },
                        ),
                )
            }

            // Tactile Hardware Capsule Thumb Knob
            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val maxTravel = (trackHeight - thumbHeight).toPx()
                        translationY = maxTravel * (1f - normalized)
                    }
                    .shadow(if (isDragging) 8.dp else 3.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Double grip ridges
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Frequency Label
        Text(
            text = eqBandLabel(hz),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (gainDb != 0f && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = eqBandCategory(hz),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            maxLines = 1,
        )
    }
}

/** Controls which connected-account playlists appear in KatMusic. This is
 * intentionally independent from importing and two-way sync. */
/**
 * Lets the user pick which YouTube channel/profile answers inside the
 * current session. Cookies are identical for every channel (which is why a
 * cookie-only client always lands on the first one) — the choice is sent
 * per-request as the InnerTube delegation flag instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeChannelSheet(
    channels: List<com.itzfallenme.katmusic.data.music.YtChannelOption>,
    isLoading: Boolean,
    selectedChannelId: String?,
    selectedAuthUser: Int?,
    selectedPageId: String = "",
    onReload: () -> Unit,
    onSelect: (com.itzfallenme.katmusic.data.music.YtChannelOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 640.dp)
                .align(Alignment.CenterHorizontally)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp + safeDrawingBottomPadding()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_yt_channel), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.set_yt_channel_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.set_yt_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                channels.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(R.string.set_yt_empty_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.set_yt_empty_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(onClick = onReload) {
                                Text(stringResource(R.string.set_yt_retry))
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        "Each channel keeps its own sync mirrors — switching re-mirrors cleanly instead of mixing libraries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(channels.size, key = { index ->
                            val c = channels[index]
                            "${c.channelId}|${c.authUserIndex}|${c.pageId}|${c.accountName}"
                        }) { index ->
                            val channel = channels[index]
                            val isSelected = channel.channelId == selectedChannelId &&
                                channel.authUserIndex == selectedAuthUser &&
                                channel.pageId == selectedPageId
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    if (!isSelected) onSelect(channel)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { if (!isSelected) onSelect(channel) },
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            channel.accountName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                        val detail = channel.channelHandle
                                            ?: if (channel.channelId != null) stringResource(R.string.set_channel_generic) else stringResource(R.string.set_channel_default)
                                        Text(
                                            detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    if (isSelected) {
                                        Text(
                                            "Active",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.common_done), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeLibraryVisibilitySheet(
    playlists: List<com.itzfallenme.katmusic.data.music.YouTubePlaylistSummary>,
    hiddenIds: Set<String>,
    onSetVisible: (String, Boolean) -> Unit,
    onSetAllVisible: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val allShown = playlists.isNotEmpty() && playlists.all { it.id !in hiddenIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 640.dp)
                .align(Alignment.CenterHorizontally)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp + safeDrawingBottomPadding()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_yt_shown_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Choose which account playlists appear in KatMusic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSetAllVisible(!allShown)
                    },
                ) {
                    Text(if (allShown) "Hide All" else "Show All", fontWeight = FontWeight.Bold)
                }
            }

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.set_yt_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playlists.size, key = { playlists[it].id }) { index ->
                        val playlist = playlists[index]
                        val isShown = playlist.id !in hiddenIds
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSetVisible(playlist.id, !isShown)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isShown) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isShown) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = if (isShown) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        playlist.trackCountText ?: playlist.author ?: "YouTube Music playlist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Checkbox(
                                    checked = isShown,
                                    onCheckedChange = { checked -> onSetVisible(playlist.id, checked) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.common_done), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Bottom sheet allowing user to select which specific playlists to mirror to YouTube Music. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncPlaylistsSheet(
    playlists: List<com.itzfallenme.katmusic.data.playlist.SavedPlaylist>,
    syncedIds: Set<Long>?,
    onToggleSync: (Long, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val allSelected = playlists.isNotEmpty() && (syncedIds == null || (playlists.all { it.id in syncedIds } && syncedIds.isNotEmpty()))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 640.dp)
                .align(Alignment.CenterHorizontally)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp + safeDrawingBottomPadding()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Sync Playlists",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Choose which playlists mirror to YouTube Music",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSelectAll(!allSelected)
                    },
                ) {
                    Text(if (allSelected) "Deselect All" else "Select All", fontWeight = FontWeight.Bold)
                }
            }

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No playlists in your library yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playlists.size, key = { playlists[it].id }) { idx ->
                        val playlist = playlists[idx]
                        val isChecked = syncedIds == null || playlist.id in syncedIds
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onToggleSync(playlist.id, !isChecked)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        playlist.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${playlist.tracks.size} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        onToggleSync(playlist.id, checked)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.common_done), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Bottom sheet to pick from 8 experimental lyrics animation physics profiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsProviderDialog(
    current: LyricsProvider,
    onSelect: (LyricsProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_lyrics_provider)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.settings_lyrics_provider_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LyricsProvider.entries.forEach { provider ->
                    val selected = provider == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(provider) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(provider) })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                provider.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            Text(
                                provider.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}

@Composable
private fun LyricsAutoHideDialog(
    currentSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_lyrics_autohide_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.set_lyrics_autohide_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SettingsRadioOption(
                    label = stringResource(R.string.set_lyrics_autohide_never),
                    selected = currentSeconds == 0,
                    onClick = { onSelect(0) },
                )
                for (seconds in 2..10) {
                    SettingsRadioOption(
                        label = stringResource(R.string.set_lyrics_autohide_seconds, seconds),
                        selected = currentSeconds == seconds,
                        onClick = { onSelect(seconds) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}

@Composable
private fun SettingsRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TranslationLanguageDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = remember { Locale.getDefault() }
    val languages = remember { TRANSLATION_LANGUAGES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_translation_language_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.set_translation_language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SettingsRadioOption(
                    label = stringResource(R.string.set_translation_language_app),
                    selected = current.isBlank(),
                    onClick = { onSelect("") },
                )
                for (language in languages) {
                    SettingsRadioOption(
                        label = translationLanguageName(language.code, locale),
                        selected = current.equals(language.code, ignoreCase = true),
                        onClick = { onSelect(language.code) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}



private fun loudnessModeTitle(context: android.content.Context, mode: LoudnessMode): String = when (mode) {
    LoudnessMode.TRACK -> context.getString(R.string.set_loud_track)
    LoudnessMode.ALBUM -> context.getString(R.string.set_loud_album)
    else -> context.getString(R.string.set_loud_off)
}

private fun loudnessModeSubtitle(context: android.content.Context, mode: LoudnessMode): String = when (mode) {
    LoudnessMode.TRACK -> context.getString(R.string.set_loud_track_sub)
    LoudnessMode.ALBUM -> context.getString(R.string.set_loud_album_sub)
    else -> context.getString(R.string.set_loud_off_sub)
}

@Composable
private fun clarityPresetName(preset: com.itzfallenme.katmusic.playback.ClarityPreset): String = stringResource(
    when (preset.key) {
        "speaker" -> R.string.set_preset_speaker
        "headphone" -> R.string.set_preset_headphone
        "dac" -> R.string.set_preset_dac
        else -> R.string.set_preset_reference
    },
)

@Composable
private fun clarityPresetDescription(preset: com.itzfallenme.katmusic.playback.ClarityPreset): String = stringResource(
    when (preset.key) {
        "speaker" -> R.string.set_preset_speaker_desc
        "headphone" -> R.string.set_preset_headphone_desc
        "dac" -> R.string.set_preset_dac_desc
        else -> R.string.set_preset_reference_desc
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoudnessModeDialog(
    current: LoudnessMode,
    onSelect: (LoudnessMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_loud_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.set_loud_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LoudnessMode.entries.forEach { mode ->
                    val selected = mode == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                loudnessModeTitle(LocalContext.current, mode),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            Text(
                                loudnessModeSubtitle(LocalContext.current, mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClarityPresetDialog(
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_clarity_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Tunes the Studio Master Clarity chain to the output. Reference is the unmodified shipping curve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                ClarityPresets.ALL.forEach { preset ->
                    val selected = preset.index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(preset.index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(preset.index) })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                clarityPresetName(preset),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            Text(
                                clarityPresetDescription(preset),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}

