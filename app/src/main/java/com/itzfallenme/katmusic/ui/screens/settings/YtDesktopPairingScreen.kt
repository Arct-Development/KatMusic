package com.itzfallenme.katmusic.ui.screens.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.itzfallenme.katmusic.ui.components.ScreenHeader
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import java.util.EnumMap
import java.util.concurrent.Executors

@Composable
fun YouTubeDesktopPairingScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: YtDesktopPairingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCamera = granted
        viewModel.onCameraPermission(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.open(hasCamera)
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 760.dp),
        ) {
            ScreenHeader(
                title = "Sign in desktop from Android",
                subtitle = "Scan the QR code shown by KatMusic Desktop",
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (val current = state) {
                    YtDesktopPairingUiState.Loading -> ProgressStatus("Checking your YouTube session…")
                    YtDesktopPairingUiState.NothingSignedIn -> MessageStatus(
                        icon = Icons.Filled.Computer,
                        title = "Sign in on this phone first",
                        message = "Connect YouTube Music in KatMusic, then return here to send that session to your computer.",
                        action = { OutlinedButton(onClick = onBack) { Text("Back to YouTube login") } },
                    )
                    YtDesktopPairingUiState.NeedsCamera -> MessageStatus(
                        icon = Icons.Filled.CameraAlt,
                        title = "Camera permission needed",
                        message = "KatMusic uses the camera only to read the desktop pairing QR code.",
                        action = {
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Allow camera")
                            }
                        },
                    )
                    YtDesktopPairingUiState.Scanning -> {
                        Text(
                            "Point your camera at the QR code on the computer.",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .size(300.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black),
                        ) {
                            QrCameraPreview(
                                onResult = viewModel::onScanned,
                                onCameraUnavailable = viewModel::onCameraUnavailable,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(54.dp),
                            )
                        }
                    }
                    YtDesktopPairingUiState.Connecting -> ProgressStatus("Connecting to KatMusic Desktop…")
                    is YtDesktopPairingUiState.AwaitingApproval -> MessageStatus(
                        icon = Icons.Filled.Computer,
                        title = "Waiting for desktop approval",
                        message = "${current.desktopName} must approve this phone before the session can be sent.",
                    )
                    is YtDesktopPairingUiState.Ready -> MessageStatus(
                        icon = Icons.Filled.Send,
                        title = "Desktop approved",
                        message = "Send your active YouTube Music session to ${current.desktopName}?",
                        action = {
                            Button(onClick = viewModel::send) {
                                Icon(Icons.Filled.Send, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Send session")
                            }
                        },
                    )
                    YtDesktopPairingUiState.Sending -> ProgressStatus("Encrypting and sending your YouTube session…")
                    YtDesktopPairingUiState.Done -> MessageStatus(
                        icon = Icons.Filled.CheckCircle,
                        title = "Desktop signed in",
                        message = "Your YouTube Music session was transferred securely. You can close this screen.",
                        action = { Button(onClick = onFinished) { Text("Done") } },
                    )
                    is YtDesktopPairingUiState.Failed -> MessageStatus(
                        icon = Icons.Filled.Computer,
                        title = "Pairing failed",
                        message = current.message,
                        action = {
                            OutlinedButton(onClick = { viewModel.retry(hasCamera) }) {
                                Text("Scan again")
                            }
                        },
                    )
                }
            }
            Text(
                "The QR is one-use and expires after five minutes. Your YouTube password is never shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ProgressStatus(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MessageStatus(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}

@Composable
@SuppressLint("MissingPermission")
private fun QrCameraPreview(
    onResult: (String) -> Unit,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )

    DisposableEffect(previewView, lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val listener = Runnable {
            try {
                provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrAnalyzer(onResult))
                    }
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (_: Throwable) {
                onCameraUnavailable()
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }
}

private class QrAnalyzer(
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private var lastText: String? = null

    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val source = PlanarYUVLuminanceSource(
                bytes,
                plane.rowStride,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false,
            )
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
            }
            val result = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
            if (result.text != lastText) {
                lastText = result.text
                onResult(result.text)
            }
            reader.reset()
        } catch (_: ReaderException) {
            reader.reset()
        } catch (_: Throwable) {
            reader.reset()
        } finally {
            imageProxy.close()
        }
    }
}
