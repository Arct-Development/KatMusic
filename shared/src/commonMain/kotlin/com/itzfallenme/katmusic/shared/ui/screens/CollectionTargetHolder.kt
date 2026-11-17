package com.itzfallenme.katmusic.shared.ui.screens

import com.itzfallenme.katmusic.shared.models.Track

/**
 * A lightweight carrier so shared screens can open a generic track collection
 * (a playlist) through the existing collection route without leaking UI types.
 */
data class CollectionTargetHolder(
    val title: String,
    val subtitle: String,
    val artwork: String?,
    val tracks: List<Track>,
)
