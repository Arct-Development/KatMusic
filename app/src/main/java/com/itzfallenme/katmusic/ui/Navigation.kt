package com.itzfallenme.katmusic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.itzfallenme.katmusic.data.CardKind

sealed interface Screen {
    data object Home : Screen
    data object Explore : Screen
    data object Discover : Screen
    data object Search : Screen
    data object Library : Screen
    data object Settings : Screen
    data object Downloads : Screen
    data object Stats : Screen
    data object Equalizer : Screen
    data object Import : Screen
    data object Account : Screen
    data object Generator : Screen
    data object LocalMusic : Screen
    data object Friends : Screen
    data class Detail(
        val browseId: String,
        val title: String,
        val kind: CardKind,
        val artwork: String = "",
        val params: String? = null
    ) : Screen
}

/** Simple, explicit back stack - no route parsing, no hidden state. */
class Navigator(start: Screen = Screen.Home) {
    val stack = mutableStateListOf(start)
    var playerOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(screen: Screen) {
        stack.add(screen)
    }

    fun switchTab(screen: Screen) {
        if (stack.lastOrNull() == screen) return
        stack.clear()
        stack.add(screen)
    }

    fun back(): Boolean {
        if (playerOpen) {
            playerOpen = false
            return true
        }
        if (queueOpen) {
            queueOpen = false
            return true
        }
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }

    fun openPlayer() {
        playerOpen = true
    }

    fun openDetail(browseId: String, title: String, kind: CardKind, artwork: String = "", params: String? = null) {
        go(Screen.Detail(browseId, title, kind, artwork, params))
    }
}
