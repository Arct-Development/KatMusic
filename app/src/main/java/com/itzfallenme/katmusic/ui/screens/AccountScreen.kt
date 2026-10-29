package com.itzfallenme.katmusic.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.SavedAccount
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import kotlinx.coroutines.launch

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AccountScreen(nav: Navigator) {
    var status by remember { mutableStateOf("Sign in with your Google account to sync your library.") }
    var manual by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    val account = ServiceLocator.account
    val accountState by account.state.collectAsState()
    // A signed-in user lands on the account list, not on the sign-in form.
    var adding by remember { mutableStateOf(!accountState.signedIn) }
    val scope = rememberCoroutineScope()

    fun finish(cookie: String) {
        if (cookie.isBlank()) {
            status = "No session cookie found - try signing in again."
            return
        }
        account.saveSession(cookie)
        scope.launch {
            val info = InnerTube.accountInfo()
            if (info != null) {
                account.updateProfile(info.first, info.second)
                status = "Signed in as ${info.first}"
            } else {
                status = "Signed in"
            }
            adding = false
            manual = false
            pasted = ""
        }
    }

    fun grabCookies() {
        val music = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
        val youtube = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
        val merged = (music + "; " + youtube)
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .distinctBy { it.substringBefore("=") }
            .joinToString("; ")
        if (merged.contains("SAPISID=") || merged.contains("__Secure-3PAPISID=")) {
            finish(merged)
        } else {
            status = "Waiting for Google sign-in to finish…"
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(
            title = "YouTube Music account",
            onBack = { nav.back() },
            actions = {
                if (adding) {
                    TextButton(onClick = { grabCookies() }) { Text("Finish") }
                }
            }
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        if (!adding && accountState.signedIn) {
            AccountManager(
                accounts = accountState.accounts,
                activeId = accountState.activeId,
                onSwitch = { id ->
                    account.switchTo(id)
                    scope.launch {
                        val info = runCatching { InnerTube.accountInfo() }.getOrNull()
                        if (info != null) account.updateProfile(info.first, info.second)
                    }
                    status = "Switched account"
                },
                onRemove = { id ->
                    account.remove(id)
                    status = "Account removed"
                },
                onAdd = {
                    adding = true
                    manual = false
                    status = "Sign in to add another account."
                },
                onSignOutAll = {
                    account.signOutAll()
                    adding = true
                    status = "Signed out of every account"
                }
            )
            return@Column
        }

        if (manual) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Paste the cookie header from a logged-in browser session " +
                        "(music.youtube.com → DevTools → Network → any request → Cookie). " +
                        "The header must include SAPISID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("Cookie header") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { finish(pasted) }) { Text("Use this session") }
                if (accountState.signedIn) {
                    TextButton(onClick = { manual = false; adding = false }) {
                        Text("Back to accounts")
                    }
                }
            }
            return@Column
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/126.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url?.contains("music.youtube.com") == true) grabCookies()
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            }
        )
        TextButton(
            onClick = { manual = true },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("Trouble signing in? Paste a cookie instead")
        }
    }
}

/** Saved sessions with a switch action, plus the ways to add or leave one. */
@Composable
private fun AccountManager(
    accounts: List<SavedAccount>,
    activeId: String,
    onSwitch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    onSignOutAll: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            if (accounts.size > 1) {
                "Switch between saved accounts. The active one supplies your library and recommendations."
            } else {
                "Your library and recommendations come from this account."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        accounts.forEach { entry ->
            val active = entry.id == activeId
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { if (!active) onSwitch(entry.id) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.avatar.isNotBlank()) {
                            AsyncImage(
                                model = entry.avatar,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.name.ifBlank { "YouTube Music account" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            if (active) "Active" else "Tap to switch",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (active) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Active account",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    TextButton(onClick = { onRemove(entry.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove account",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add account")
            }
            TextButton(onClick = onSignOutAll) { Text("Sign out all") }
        }
    }
}
