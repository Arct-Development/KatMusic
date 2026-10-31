package com.itzfallenme.katmusic.data.blend

import kotlinx.serialization.Serializable

@Serializable
data class BlendSharePayload(
    val format: Int = FORMAT_VERSION,
    val displayName: String,
    val profile: BlendTasteProfile,
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val PREFIX = "KATBLEND1:"
    }
}
