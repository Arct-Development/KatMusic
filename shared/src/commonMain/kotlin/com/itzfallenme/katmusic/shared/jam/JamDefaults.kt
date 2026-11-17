package com.itzfallenme.katmusic.shared.jam

/** Shared connection and invite defaults so Android and Desktop enter the same Jam rooms. */
object JamDefaults {
    const val SERVER_ENDPOINT = "wss://katmusic.onrender.com/ws"
    private const val INVITE_PREFIX = "https://katmusic.onrender.com/?code="

    fun inviteUrl(roomCode: String): String? {
        val code = roomCode.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
        return code.takeIf { it.length in 3..12 }?.let { "$INVITE_PREFIX$it" }
    }
}
