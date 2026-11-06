package com.itzfallenme.katmusic.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.itzfallenme.katmusic.R

/**
 * Art-first companion to the full now-playing widgets. It deliberately keeps
 * only the things that still read at a glance on a 1x1 home-screen tile:
 * cover, track, artist and one play/pause target.
 */
internal object CoverWidgetViews {

    fun build(
        context: Context,
        appWidgetId: Int,
        memoryArt: Bitmap? = null,
    ): RemoteViews {
        val resolved = WidgetViews.resolve(context)
        val artBitmap = memoryArt?.takeUnless(Bitmap::isRecycled)
            ?: WidgetViews.resolveArtBitmap(resolved.snapshot.artPath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizeMap = mapOf(
                SizeF(110f, 110f) to buildLayout(
                    context, R.layout.widget_cover_tile, resolved, artBitmap,
                ),
                SizeF(220f, 100f) to buildLayout(
                    context, R.layout.widget_cover_wide, resolved, artBitmap,
                ),
            )
            return RemoteViews(sizeMap)
        }

        val width = minWidthDp(context, appWidgetId)
        val height = minHeightDp(context, appWidgetId)
        val layoutId = if (width > 0 && width.toFloat() > height * 1.45f) {
            R.layout.widget_cover_wide
        } else {
            R.layout.widget_cover_tile
        }
        return buildLayout(context, layoutId, resolved, artBitmap)
    }

    private fun buildLayout(
        context: Context,
        layoutId: Int,
        resolved: WidgetViews.Resolved,
        artBitmap: Bitmap?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        bind(context, views, resolved, artBitmap)
        return views
    }

    private fun bind(
        context: Context,
        views: RemoteViews,
        resolved: WidgetViews.Resolved,
        artBitmap: Bitmap?,
    ) {
        val snapshot = resolved.snapshot
        if (!resolved.usableSession) {
            views.setViewVisibility(R.id.widget_content_group, View.GONE)
            views.setViewVisibility(R.id.widget_play_pause, View.GONE)
            views.setViewVisibility(R.id.widget_empty_group, View.VISIBLE)
            views.setImageViewResource(R.id.widget_empty_icon, R.drawable.widget_art_placeholder)
            if (resolved.hasAccess) {
                views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_cover_name))
                views.setTextViewText(R.id.widget_empty_sub, "Start a song in any media app")
                views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAppPending(context))
            } else {
                views.setTextViewText(R.id.widget_empty_title, "Allow music access")
                views.setTextViewText(R.id.widget_empty_sub, "Tap to detect every media app")
                views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAccessPending(context))
            }
            return
        }

        views.setViewVisibility(R.id.widget_empty_group, View.GONE)
        views.setViewVisibility(R.id.widget_content_group, View.VISIBLE)
        views.setViewVisibility(R.id.widget_play_pause, View.VISIBLE)

        views.setTextViewText(
            R.id.widget_title,
            snapshot.title.ifBlank { "Unknown track" },
        )
        val subtitle = buildList {
            snapshot.artist.takeIf { it.isNotBlank() }?.let(::add)
            snapshot.album.takeIf { it.isNotBlank() && !it.equals(snapshot.artist, ignoreCase = true) }?.let(::add)
        }.joinToString(" · ").ifBlank { "Unknown artist" }
        views.setTextViewText(R.id.widget_subtitle, subtitle)
        views.setContentDescription(
            R.id.widget_art,
            listOf(snapshot.title, snapshot.artist, snapshot.album)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        )

        val playing = snapshot.isPlaying
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setContentDescription(
            R.id.widget_play_pause,
            if (playing) "Pause playback" else "Resume playback",
        )

        if (artBitmap != null && !artBitmap.isRecycled) {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        }

        views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAppPending(context))
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            WidgetActions.togglePending(context, CoverWidgetReceiver::class.java),
        )
    }

    private fun minWidthDp(context: Context, appWidgetId: Int): Int = runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    }.getOrDefault(0)

    private fun minHeightDp(context: Context, appWidgetId: Int): Int = runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    }.getOrDefault(0)
}
