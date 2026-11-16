package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.itzfallenme.katmusic.desktop.data.YtPhonePairing
import com.itzfallenme.katmusic.desktop.data.YtPhonePairingPhase
import com.itzfallenme.katmusic.desktop.data.YouTubeSession
import com.itzfallenme.katmusic.desktop.data.YouTubeSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop settings section for the phone → desktop YouTube login handoff:
 * shows the connected account, starts a five-minute pairing QR, asks the
 * desktop user to approve the requesting phone, and stores the received
 * AES-GCM-encrypted session for playback and downloads.
 */
@Composable
fun YouTubeLoginSection(store: YouTubeSessionStore) {
    val session by store.session.collectAsState()
    val scope = rememberCoroutineScope()
    var pairing by remember { mutableStateOf<YtPhonePairing?>(null) }
    var starting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }

    fun startPairing() {
        if (starting) return
        starting = true
        startError = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { YtPhonePairing.start() }
            }
            starting = false
            result.onSuccess { pairing = it }
                .onFailure {
                    startError = it.message
                        ?: "Could not start the desktop pairing server."
                }
        }
    }

    Text(
        text = "YouTube account",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )

    if (session == null) {
        Text(
            text = "Sign in on your Android phone and scan the code from this computer. " +
                "Cookies are sent encrypted over your local network and stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Button(onClick = ::startPairing, enabled = !starting) {
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Rounded.QrCode2, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (starting) "Starting…" else "Sign in from Android")
        }
    } else {
        val connected = session!!
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (connected.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = connected.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = connected.accountName.ifBlank { "YouTube Music account" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOfNotNull(
                        connected.channelHandle.takeIf { it.isNotBlank() },
                        "Signed in on this computer",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = ::startPairing, enabled = !starting) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (starting) "Starting…" else "Link another phone")
            }
            TextButton(onClick = store::clear) { Text("Sign out") }
        }
    }

    startError?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    pairing?.let { active ->
        // The server exposes the decrypted payload exactly once; store it and
        // dismiss as soon as it arrives.
        LaunchedEffect(active) {
            active.snapshot.collect { snapshot ->
                if (snapshot.phase == YtPhonePairingPhase.RECEIVED) {
                    active.transfer.value?.let { received ->
                        runCatching {
                            store.save(
                                YouTubeSession(
                                    cookies = received.cookies,
                                    accountName = received.accountName,
                                    channelHandle = received.channelHandle,
                                    photoUrl = received.photoUrl,
                                    pageId = received.pageId,
                                ),
                            )
                        }.onFailure {
                            startError = it.message ?: "The session could not be saved."
                        }
                        active.close()
                        pairing = null
                    }
                }
            }
        }
        YouTubePairingDialog(
            pairing = active,
            onDismiss = {
                active.close()
                pairing = null
            },
        )
    }
}

@Composable
private fun YouTubePairingDialog(
    pairing: YtPhonePairing,
    onDismiss: () -> Unit,
) {
    val snapshot by pairing.snapshot.collectAsState()
    val terminal = snapshot.phase == YtPhonePairingPhase.EXPIRED ||
        snapshot.phase == YtPhonePairingPhase.REJECTED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in from Android") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (snapshot.phase) {
                    YtPhonePairingPhase.WAITING,
                    YtPhonePairingPhase.AWAITING_APPROVAL,
                    YtPhonePairingPhase.APPROVED,
                    -> QrCode(pairing.qrData)

                    YtPhonePairingPhase.RECEIVED,
                    YtPhonePairingPhase.REJECTED,
                    YtPhonePairingPhase.EXPIRED,
                    -> Unit
                }

                Text(
                    text = when (snapshot.phase) {
                        YtPhonePairingPhase.WAITING ->
                            "Scan this code in KatMusic on your Android phone."
                        YtPhonePairingPhase.AWAITING_APPROVAL ->
                            "${snapshot.deviceName ?: "An Android phone"} is requesting access."
                        YtPhonePairingPhase.APPROVED ->
                            "Approved. Waiting for the phone to send the session…"
                        YtPhonePairingPhase.RECEIVED ->
                            "Session received. Signing in…"
                        YtPhonePairingPhase.REJECTED ->
                            "The phone request was declined."
                        YtPhonePairingPhase.EXPIRED ->
                            "This QR code has expired."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when (snapshot.phase) {
                        YtPhonePairingPhase.WAITING ->
                            "On your phone open Settings → YouTube account → the computer icon, " +
                                "then scan. Both devices must be on the same Wi-Fi."
                        YtPhonePairingPhase.AWAITING_APPROVAL ->
                            "Only approve a phone you recognise. The code expires in five minutes."
                        YtPhonePairingPhase.APPROVED ->
                            "Keep both devices connected to the same Wi-Fi."
                        YtPhonePairingPhase.REJECTED,
                        YtPhonePairingPhase.EXPIRED ->
                            "Close this window and create a new code to try again."
                        YtPhonePairingPhase.RECEIVED -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!terminal && snapshot.phase != YtPhonePairingPhase.RECEIVED) {
                    Text(
                        text = "Computer address: ${pairing.hostLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            if (snapshot.phase == YtPhonePairingPhase.AWAITING_APPROVAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = pairing::reject) { Text("Decline") }
                    Button(onClick = pairing::approve) { Text("Allow phone") }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(if (terminal) "Close" else "Cancel")
                }
            }
        },
    )
}

/**
 * The QR is drawn straight from ZXing's module matrix, so it stays crisp at
 * any scale and needs no image conversion.
 */
@Composable
private fun QrCode(data: String) {
    val matrix = remember(data) {
        runCatching {
            QRCodeWriter().encode(
                data,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 0,
                ),
            )
        }.getOrNull()
    }
    if (matrix == null) {
        Text(
            text = "The pairing code could not be rendered.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Canvas(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        val cell = size.minDimension / matrix.width
        val left = (size.width - cell * matrix.width) / 2f
        val top = (size.height - cell * matrix.height) / 2f
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = Color(0xFF111111),
                        topLeft = Offset(left + x * cell, top + y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
