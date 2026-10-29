package com.itzfallenme.katmusic.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings tree, one level deep: the root lists these, and a tap opens just
 * the rows that belong to the chosen section. Keeping the ids stable lets the
 * back stack hold [com.itzfallenme.katmusic.ui.Screen.SettingsCategory] entries
 * and lets search jump straight to the right section.
 */
enum class SettingsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: String
) {
    ACCOUNT("account", "Account", "YouTube Music sign-in and sync", "login sign out profile google"),
    APPEARANCE("appearance", "Appearance", "Theme, colours, Material You", "dark light amoled accent wallpaper"),
    PLAYBACK("playback", "Playback", "Streaming quality, crossfade, equalizer", "audio stream sponsorblock silence speed"),
    AUDIO("audio", "Audio & safety", "Mono mix, volume limit, listening breaks", "mono downmix hearing guard volume safe"),
    LYRICS("lyrics", "Lyrics", "Sources, timing, appearance", "karaoke ttml lrclib offset size romanise"),
    DOWNLOADS("downloads", "Downloads", "Offline quality and storage", "offline save wifi cache"),
    AI("ai", "AI picks", "OpenAI-compatible endpoint", "openai groq openrouter model key suggestions"),
    DISCORD("discord", "Discord", "Rich Presence", "rpc presence status"),
    LASTFM("lastfm", "Last.fm", "Scrobbling and friends", "scrobble friends profile"),
    DATA("data", "Data & about", "Backups, history, device music", "backup restore clear import stats device version");

    companion object {
        fun fromId(id: String?): SettingsCategory? = entries.firstOrNull { it.id == id }
    }
}

val SettingsCategory.icon: ImageVector
    get() = when (this) {
        SettingsCategory.ACCOUNT -> Icons.Filled.Person
        SettingsCategory.APPEARANCE -> Icons.Filled.Palette
        SettingsCategory.PLAYBACK -> Icons.Filled.PlayCircle
        SettingsCategory.AUDIO -> Icons.Filled.VolumeUp
        SettingsCategory.LYRICS -> Icons.Filled.Lyrics
        SettingsCategory.DOWNLOADS -> Icons.Filled.Storage
        SettingsCategory.AI -> Icons.Filled.AutoAwesome
        SettingsCategory.DISCORD -> Icons.Filled.GraphicEq
        SettingsCategory.LASTFM -> Icons.Filled.BarChart
        SettingsCategory.DATA -> Icons.Filled.Equalizer
    }

/** One searchable row. [subtitle] doubles as extra search text ("bitrate", "AMOLED"…). */
data class SettingItem(
    val category: SettingsCategory,
    val title: String,
    val subtitle: String = ""
)

val settingsIndex: List<SettingItem> = listOf(
    SettingItem(SettingsCategory.ACCOUNT, "Sign in with YouTube Music", "Library, likes and history sync"),
    SettingItem(SettingsCategory.ACCOUNT, "Switch account"),
    SettingItem(SettingsCategory.ACCOUNT, "Sign out"),
    SettingItem(SettingsCategory.APPEARANCE, "Theme", "System, light or dark"),
    SettingItem(SettingsCategory.APPEARANCE, "Wallpaper colours", "Material You palette"),
    SettingItem(SettingsCategory.APPEARANCE, "Artwork colours", "Recolour the app from album art"),
    SettingItem(SettingsCategory.APPEARANCE, "Pure black", "AMOLED friendly backgrounds"),
    SettingItem(SettingsCategory.APPEARANCE, "Accent colour", "Seed palette swatches"),
    SettingItem(SettingsCategory.PLAYBACK, "High quality audio", "Bitrate, Opus"),
    SettingItem(SettingsCategory.PLAYBACK, "Autoplay radio", "Keep the music going"),
    SettingItem(SettingsCategory.PLAYBACK, "SponsorBlock", "Skip sponsor and intro segments"),
    SettingItem(SettingsCategory.PLAYBACK, "Skip silence", "Jump over silent intros"),
    SettingItem(SettingsCategory.PLAYBACK, "Crossfade", "Seconds between tracks"),
    SettingItem(SettingsCategory.PLAYBACK, "Equalizer", "Bands, presets, AutoEQ"),
    SettingItem(SettingsCategory.AUDIO, "Mono audio", "One earbud downmix"),
    SettingItem(SettingsCategory.AUDIO, "Volume limit", "Cap the music stream"),
    SettingItem(SettingsCategory.AUDIO, "Volume limit level", "Percentage of device maximum"),
    SettingItem(SettingsCategory.AUDIO, "Listening break reminder", "Hearing safety"),
    SettingItem(SettingsCategory.LYRICS, "Fetch lyrics automatically", "LRCLIB"),
    SettingItem(SettingsCategory.LYRICS, "Word-by-word lyrics", "TTML timing, karaoke highlight"),
    SettingItem(SettingsCategory.LYRICS, "Lyric glow", "Halo around the sung word"),
    SettingItem(SettingsCategory.LYRICS, "Romanise lyrics", "Latin-script reading"),
    SettingItem(SettingsCategory.LYRICS, "Timing offset", "Shift lyrics earlier or later"),
    SettingItem(SettingsCategory.LYRICS, "Text size", "Lyrics scale"),
    SettingItem(SettingsCategory.DOWNLOADS, "Download in high quality", "Bigger files, better sound"),
    SettingItem(SettingsCategory.DOWNLOADS, "Manage downloads", "Storage and offline files"),
    SettingItem(SettingsCategory.AI, "Endpoint", "OpenAI-compatible base URL"),
    SettingItem(SettingsCategory.AI, "API key", "Stored on this device"),
    SettingItem(SettingsCategory.AI, "Model", "Chat-completions model"),
    SettingItem(SettingsCategory.DISCORD, "Discord Rich Presence", "Show the playing track"),
    SettingItem(SettingsCategory.DISCORD, "Application ID", "Discord developer app"),
    SettingItem(SettingsCategory.LASTFM, "Scrobble plays", "Send tracks to Last.fm"),
    SettingItem(SettingsCategory.LASTFM, "Pending scrobbles", "Queue waiting to send"),
    SettingItem(SettingsCategory.LASTFM, "Friends' listening", "Follow Last.fm profiles"),
    SettingItem(SettingsCategory.LASTFM, "API key", "Last.fm application"),
    SettingItem(SettingsCategory.LASTFM, "API secret", "Last.fm application"),
    SettingItem(SettingsCategory.DATA, "Backup library", "Likes, playlists, history, settings"),
    SettingItem(SettingsCategory.DATA, "Restore backup", "Replace this library"),
    SettingItem(SettingsCategory.DATA, "Music on this device", "Play local audio files"),
    SettingItem(SettingsCategory.DATA, "Import a playlist", "Copy a YouTube Music playlist"),
    SettingItem(SettingsCategory.DATA, "Listening stats", "Plays recorded"),
    SettingItem(SettingsCategory.DATA, "Clear history"),
    SettingItem(SettingsCategory.DATA, "Clear recent searches"),
    SettingItem(SettingsCategory.DATA, "Debug log", "Events, warnings and errors")
)

/**
 * Filters [settingsIndex] for a search box. Every whitespace-separated token has
 * to match somewhere, so "dark theme" finds Theme and "volume limit" finds the
 * safety rows. Title matches rank above matches that only hit the description.
 */
fun searchSettings(query: String, index: List<SettingItem> = settingsIndex): List<SettingItem> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    return index
        .mapNotNull { item ->
            val title = item.title.lowercase()
            val haystack = buildString {
                append(title).append(' ')
                append(item.subtitle.lowercase()).append(' ')
                append(item.category.title.lowercase()).append(' ')
                append(item.category.keywords)
            }
            if (!tokens.all { haystack.contains(it) }) return@mapNotNull null
            val score = when {
                title == tokens.joinToString(" ") -> 0
                tokens.all { title.contains(it) } -> 1
                else -> 2
            }
            score to item
        }
        .sortedWith(compareBy({ it.first }, { it.second.title }))
        .map { it.second }
}
