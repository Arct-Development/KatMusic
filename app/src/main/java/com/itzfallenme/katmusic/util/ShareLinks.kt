package com.itzfallenme.katmusic.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Cross-device track links.
 *
 * A shared track always points at the KatMusic page, never at a raw
 * `katmusic://` URL: the page opens the app on the receiving device and still
 * shows the track (plus a YouTube Music fallback) when KatMusic is not
 * installed. The query keys match [katmusic.pages.dev/share.html] and the
 * desktop share surface, so a link created on any platform is identical.
 */
object ShareLinks {

    const val SITE = "https://katmusic.pages.dev/share.html"

    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{6,24}$")

    /** Page link for [videoId], or null when the track has no stable identity. */
    fun trackUrl(
        videoId: String?,
        title: String,
        artist: String,
        album: String? = null,
        artworkUrl: String? = null,
    ): String? {
        val id = videoId?.trim().orEmpty()
        if (!videoIdPattern.matches(id)) return null
        val builder = Uri.parse(SITE).buildUpon().appendQueryParameter("v", id)
        title.trim().takeIf { it.isNotEmpty() }?.let { builder.appendQueryParameter("title", it) }
        artist.trim().takeIf { it.isNotEmpty() }?.let { builder.appendQueryParameter("artist", it) }
        album?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.appendQueryParameter("album", it) }
        artworkUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.appendQueryParameter("art", it) }
        return builder.build().toString()
    }

    /** Opens the system share sheet with [url] as plain text. */
    fun send(context: Context, url: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            subject?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        val chooser = Intent.createChooser(intent, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
