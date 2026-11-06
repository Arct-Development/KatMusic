package com.itzfallenme.katmusic.ui.screens.settings

import android.os.Build
import android.util.Base64
import com.itzfallenme.katmusic.data.ytmusic.YtDesktopPairingClient
import com.itzfallenme.katmusic.data.ytmusic.YtDesktopPairingConnection
import com.itzfallenme.katmusic.data.ytmusic.YtDesktopPairingStatus
import com.itzfallenme.katmusic.data.ytmusic.YtMusicAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

sealed interface YtDesktopPairingUiState {
    data object Loading : YtDesktopPairingUiState
    data object NothingSignedIn : YtDesktopPairingUiState
    data object NeedsCamera : YtDesktopPairingUiState
    data object Scanning : YtDesktopPairingUiState
    data object Connecting : YtDesktopPairingUiState
    data class AwaitingApproval(val desktopName: String) : YtDesktopPairingUiState
    data class Ready(val desktopName: String) : YtDesktopPairingUiState
    data object Sending : YtDesktopPairingUiState
    data object Done : YtDesktopPairingUiState
    data class Failed(val message: String) : YtDesktopPairingUiState
}

@HiltViewModel
class YtDesktopPairingViewModel @Inject constructor(
    private val ytAuthManager: YtMusicAuthManager,
) : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow<YtDesktopPairingUiState>(YtDesktopPairingUiState.Loading)
    val uiState: StateFlow<YtDesktopPairingUiState> = _uiState.asStateFlow()

    private val client = YtDesktopPairingClient()
    private var connection: YtDesktopPairingConnection? = null
    private var operation: Job? = null

    fun open(hasCamera: Boolean) {
        operation?.cancel()
        _uiState.value = YtDesktopPairingUiState.Loading
        operation = viewModelScope.launch {
            val current = runCatching { ytAuthManager.awaitLoadedConnection() }.getOrNull()
            if (current?.cookies.isNullOrEmpty()) {
                _uiState.value = YtDesktopPairingUiState.NothingSignedIn
            } else if (hasCamera) {
                _uiState.value = YtDesktopPairingUiState.Scanning
            } else {
                _uiState.value = YtDesktopPairingUiState.NeedsCamera
            }
        }
    }

    fun onCameraPermission(granted: Boolean) {
        if (granted && _uiState.value is YtDesktopPairingUiState.NeedsCamera) {
            _uiState.value = YtDesktopPairingUiState.Scanning
        }
    }

    fun onCameraUnavailable() {
        _uiState.value = YtDesktopPairingUiState.Failed("No usable camera is available on this phone.")
    }

    fun onScanned(raw: String) {
        if (_uiState.value !is YtDesktopPairingUiState.Scanning) return
        operation?.cancel()
        operation = viewModelScope.launch {
            _uiState.value = YtDesktopPairingUiState.Connecting
            try {
                val parsed = YtDesktopPairingInvite.parse(raw)
                val paired = client.request(parsed, deviceName())
                connection = paired
                _uiState.value = YtDesktopPairingUiState.AwaitingApproval("KatMusic Desktop")
                waitForApproval(paired)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = YtDesktopPairingUiState.Failed(
                    error.message ?: "Could not connect to KatMusic Desktop.",
                )
            }
        }
    }

    private suspend fun waitForApproval(paired: YtDesktopPairingConnection) {
        while (System.currentTimeMillis() < paired.invite.expiresAtMillis) {
            when (client.status(paired)) {
                YtDesktopPairingStatus.Approved -> {
                    _uiState.value = YtDesktopPairingUiState.Ready("KatMusic Desktop")
                    return
                }
                YtDesktopPairingStatus.AwaitingApproval -> delay(700L)
            }
        }
        throw IllegalStateException("The desktop pairing code expired.")
    }

    fun send() {
        val paired = connection ?: return
        if (_uiState.value !is YtDesktopPairingUiState.Ready) return
        operation?.cancel()
        operation = viewModelScope.launch {
            _uiState.value = YtDesktopPairingUiState.Sending
            try {
                // Google rotates session cookies in the WebView. Capture the
                // current jar immediately before encryption, then wait for the
                // DataStore collector to expose that exact jar.
                runCatching { ytAuthManager.refreshCookiesFromCookieManager() }
                val account = ytAuthManager.awaitLoadedConnection()
                val header = ytAuthManager.cookieHeaderValue(account).orEmpty()
                if (!hasSapisid(header)) {
                    throw IllegalStateException("No active YouTube Music session was found on this phone.")
                }
                val encrypted = encrypt(
                    key = paired.invite.key,
                    payload = JSONObject()
                        .put("cookies", header)
                        .put("accountName", account.accountName)
                        .put("channelHandle", account.channelHandle ?: "")
                        .put("photoUrl", account.photoUrl ?: "")
                        .put("pageId", account.pageId)
                        .toString(),
                )
                client.send(paired, encrypted)
                _uiState.value = YtDesktopPairingUiState.Done
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = YtDesktopPairingUiState.Failed(
                    error.message ?: "The YouTube session could not be sent.",
                )
            }
        }
    }

    fun retry(hasCamera: Boolean) {
        connection = null
        open(hasCamera)
    }

    fun close() {
        operation?.cancel()
        operation = null
        connection = null
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private fun encrypt(key: ByteArray, payload: String): JSONObject {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce),
        )
        val ciphertext = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return JSONObject()
            .put("nonce", encodeBase64(nonce))
            .put("ciphertext", encodeBase64(ciphertext))
    }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android phone" }
            .take(48)

    private fun hasSapisid(header: String): Boolean =
        header.contains("SAPISID=") ||
            header.contains("__Secure-3PAPISID=") ||
            header.contains("APISID=")

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
