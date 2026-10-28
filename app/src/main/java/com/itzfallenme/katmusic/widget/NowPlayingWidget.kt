package com.itzfallenme.katmusic.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.glance.unit.ColorProvider
import com.itzfallenme.katmusic.MainActivity
import com.itzfallenme.katmusic.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

internal object WidgetKeys {
    val title = stringPreferencesKey("title")
    val artist = stringPreferencesKey("artist")
    val artwork = stringPreferencesKey("artwork")
    val playing = booleanPreferencesKey("playing")
    val empty = booleanPreferencesKey("empty")
}

/** Home-screen now playing card: artwork, title, transport controls. */
class NowPlayingWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val empty = prefs[WidgetKeys.empty] ?: true
        val title = prefs[WidgetKeys.title].orEmpty()
        val artist = prefs[WidgetKeys.artist].orEmpty()
        val artwork = prefs[WidgetKeys.artwork].orEmpty()
        val playing = prefs[WidgetKeys.playing] ?: false
        val onSurface = ColorProvider(0xFFFFFFFF.toInt())
        val muted = ColorProvider(0xFFB9B9C3.toInt())
        val buttonColor = ColorProvider(0xFF2A2A34.toInt())
        val artworkBitmap = remember(artwork) {
            artwork.takeIf { it.isNotBlank() }
                ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        }

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(0xFF14141A.toInt()))
                .cornerRadius(22)
                .padding(12)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artworkBitmap != null) {
                Image(
                    provider = ImageProvider(artworkBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(56).cornerRadius(14)
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(56)
                        .cornerRadius(14)
                        .background(ColorProvider(0xFF2A2A34.toInt())),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♪",
                        style = TextStyle(color = onSurface, fontSize = 22.sp)
                    )
                }
            }
            Spacer(GlanceModifier.width(12))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = if (empty) "KatMusic" else title,
                    style = TextStyle(color = onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(2))
                Text(
                    text = if (empty) "Tap to start listening" else artist,
                    style = TextStyle(color = muted, fontSize = 12.sp),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(8))
            Text(
                text = if (playing) "⏸" else "▶",
                modifier = GlanceModifier
                    .background(buttonColor)
                    .cornerRadius(20)
                    .padding(horizontal = 12, vertical = 6)
                    .clickable(actionRunCallback<TogglePlaybackAction>()),
                style = TextStyle(color = onSurface, fontSize = 16.sp)
            )
            Spacer(GlanceModifier.width(6))
            Text(
                text = "⏭",
                modifier = GlanceModifier
                    .background(buttonColor)
                    .cornerRadius(20)
                    .padding(horizontal = 12, vertical = 6)
                    .clickable(actionRunCallback<NextTrackAction>()),
                style = TextStyle(color = onSurface, fontSize = 16.sp)
            )
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        ServiceLocator.init(context)
        ServiceLocator.player.togglePlayPause()
    }
}

class NextTrackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        ServiceLocator.init(context)
        ServiceLocator.player.next()
    }
}

/** Pushes the player state into the widget and refreshes every placed instance. */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSignature: String? = null

    fun refresh(context: Context, state: com.itzfallenme.katmusic.playback.PlayerUiState) {
        val signature = "${state.track?.id}|${state.playing}"
        if (signature == lastSignature) return
        lastSignature = signature
        val track = state.track
        scope.launch {
            runCatching { push(context, track, state.playing) }
        }
    }

    private suspend fun push(
        context: Context,
        track: com.itzfallenme.katmusic.data.Track?,
        playing: Boolean
    ) {
        val ids = runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(NowPlayingWidget::class.java)
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return
        val widget = NowPlayingWidget()
        val artworkFile = track?.artwork
            ?.takeIf { it.isNotBlank() }
            ?.let { cacheArtwork(context, track.id, it) }
        ids.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.empty] = track == null
                    this[WidgetKeys.title] = track?.title.orEmpty()
                    this[WidgetKeys.artist] = track?.artist.orEmpty()
                    this[WidgetKeys.playing] = playing
                    this[WidgetKeys.artwork] = artworkFile?.absolutePath.orEmpty()
                }
            }
            widget.update(context, id)
        }
    }

    private fun cacheArtwork(context: Context, trackId: String, url: String): File? {
        val folder = File(context.cacheDir, "widget").apply { mkdirs() }
        val file = File(folder, trackId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".img")
        if (file.exists() && file.length() > 0) return file
        folder.listFiles()?.forEach { it.delete() }
        val request = Request.Builder().url(url).build()
        return runCatching {
            com.itzfallenme.katmusic.data.InnerTube.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                file.outputStream().use { output -> body.byteStream().copyTo(output) }
            }
            file.takeIf { it.length() > 0 }
        }.getOrNull()
    }
}
