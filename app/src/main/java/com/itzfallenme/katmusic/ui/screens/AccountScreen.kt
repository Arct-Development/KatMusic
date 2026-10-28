package com.itzfallenme.katmusic.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.InnerTube
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
    val scope = rememberCoroutineScope()
    val account = ServiceLocator.account

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
            nav.back()
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
                TextButton(onClick = { grabCookies() }) { Text("Finish") }
            }
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
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
