package com.itzfallenme.katmusic.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Starts an external intent from whatever context a composable happens to have.
 *
 * `LocalContext.current` is not always the Activity: inside a popup, a dialog or a
 * preview it can be a `ContextThemeWrapper` with no Activity behind it, and
 * `startActivity` from one of those throws
 * "Calling startActivity() from outside of an Activity context requires the
 * FLAG_ACTIVITY_NEW_TASK flag" - an immediate crash on tapping Share or a link.
 * This checks the context, adds the flag when needed, and turns a missing handler
 * into a toast instead of an exception.
 */
fun Context.openExternal(intent: Intent, chooserTitle: String? = null): Boolean {
    val target = if (chooserTitle != null) Intent.createChooser(intent, chooserTitle) else intent
    if (this !is Activity) target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        startActivity(target)
        true
    }.getOrElse {
        runCatching {
            Toast.makeText(this, "No app can handle that link", Toast.LENGTH_SHORT).show()
        }
        false
    }
}

/** Share handler used by every row and player menu. */
fun Context.shareLink(url: String, chooserTitle: String = "Share track") {
    openExternal(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        },
        chooserTitle
    )
}
