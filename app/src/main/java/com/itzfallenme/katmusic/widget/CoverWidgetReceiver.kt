package com.itzfallenme.katmusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen cover tile with one-tap play/pause. */
class CoverWidgetReceiver : AppWidgetProvider() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (appWidgetId in ids) {
            runCatching {
                manager.updateAppWidget(
                    appWidgetId,
                    CoverWidgetViews.build(context, appWidgetId, WidgetUpdater.memoryArtBitmap),
                )
            }
        }
        ioScope.launch {
            runCatching {
                val snapshot = WidgetSnapshot.read(context)
                if (snapshot.hasSession && snapshot.isPlaying) {
                    WidgetUpdater.startWaveAnimation(context.applicationContext)
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        runCatching {
            manager.updateAppWidget(
                appWidgetId,
                CoverWidgetViews.build(context, appWidgetId, WidgetUpdater.memoryArtBitmap),
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetActions.ACTION_TOGGLE,
            WidgetActions.ACTION_NEXT,
            WidgetActions.ACTION_PREV,
            -> {
                val action = intent.action ?: return
                val pending = goAsync()
                ioScope.launch {
                    try {
                        when (action) {
                            WidgetActions.ACTION_TOGGLE -> WidgetActions.performToggle(context)
                            WidgetActions.ACTION_NEXT -> WidgetActions.performSkip(context, next = true)
                            WidgetActions.ACTION_PREV -> WidgetActions.performSkip(context, next = false)
                        }
                    } finally {
                        runCatching { pending.finish() }
                    }
                }
                return
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ioScope.launch { runCatching { WidgetUpdater.sync(context) } }
    }
}
