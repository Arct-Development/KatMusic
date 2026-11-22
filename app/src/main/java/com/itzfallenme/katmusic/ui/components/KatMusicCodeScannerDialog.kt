package com.itzfallenme.katmusic.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import android.util.Size
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.itzfallenme.katmusic.shared.share.KatMusicCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** In-app camera for KatMusic's Spotify-style Code 128 sound tags. */
@Composable
fun KatMusicCodeScannerDialog(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    var cameraUnavailable by remember { mutableStateOf(false) }
    val diagnostics = remember { ScannerDiagnostics() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(horizontal = 12.dp, vertical = 18.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF21C4FF),
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        "Scan KatMusic Code",
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close scanner")
                    }
                }
                Text(
                    "Point your camera at the horizontal KatMusic sound tag.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        KatMusicCodeCameraPreview(
                            onCodeScanned = onCodeScanned,
                            onCameraUnavailable = { cameraUnavailable = true },
                            diagnostics = diagnostics,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp)
                                .height(112.dp)
                                .border(2.dp, Color(0xFF21C4FF), RoundedCornerShape(18.dp)),
                        )
                    }
                    if (cameraUnavailable) {
                        Text(
                            "Camera unavailable. Close other apps using the camera and try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Keep the full code inside the frame. KatMusic Codes are scanned in this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ScannerDiagnosticsPanel(diagnostics, Modifier.fillMaxWidth())
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Camera access is needed to scan KatMusic Codes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Allow camera")
                        }
                    }
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

/** Polls the analyzer's counters so its recomposition stays off the camera subtree. */
@Composable
private fun ScannerDiagnosticsPanel(
    diagnostics: ScannerDiagnostics,
    modifier: Modifier = Modifier,
) {
    var line by remember(diagnostics) { mutableStateOf(diagnostics.describe()) }
    LaunchedEffect(diagnostics) {
        while (isActive) {
            line = diagnostics.describe()
            delay(250)
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun KatMusicCodeCameraPreview(
    onCodeScanned: (String) -> Unit,
    onCameraUnavailable: () -> Unit,
    diagnostics: ScannerDiagnostics,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(factory = { previewView }, modifier = modifier)

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
                    .setTargetRotation(previewView.display?.rotation ?: 0)
                    .setTargetResolution(Size(1280, 720))
                    .build()
                    .also { it.setAnalyzer(executor, KatMusicCodeAnalyzer(onCodeScanned, diagnostics)) }
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (_: Throwable) {
                Handler(Looper.getMainLooper()).post { onCameraUnavailable() }
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }
}

/**
 * Live counters for diagnosing a scanner that never fires. The analyzer thread
 * only writes these atomics; the dialog polls them for display.
 */
private class ScannerDiagnostics {
    val frames = AtomicInteger(0)
    val frameWidth = AtomicInteger(0)
    val frameHeight = AtomicInteger(0)
    val rotationDegrees = AtomicInteger(-1)
    val lastDecodedText = AtomicReference<String?>(null)

    fun describe(): String {
        val raw = lastDecodedText.get()?.take(40)?.replace("\n", " ") ?: "—"
        val width = frameWidth.get()
        val height = frameHeight.get()
        val rotation = rotationDegrees.get()
        val camera = if (width > 0) "${width}x$height rot $rotation°" else "camera starting"
        val turns = if (rotation < 0) "—" else quarterTurns(rotation).joinToString() { "${it * 90}°" }
        return "frames ${frames.get()} · $camera · turns $turns\nraw: $raw"
    }
}

/**
 * Quarter turns (counter-clockwise) to try for a frame CameraX reports with
 * [rotationDegrees]. The second turn covers a tag that is vertical in the
 * first; ZXing also reads each row reversed, which covers the other two.
 */
private fun quarterTurns(rotationDegrees: Int): IntArray {
    val upright = (4 - rotationDegrees / 90) % 4
    return intArrayOf(upright, (upright + 1) % 4)
}

private class KatMusicCodeAnalyzer(
    private val onCodeScanned: (String) -> Unit,
    private val diagnostics: ScannerDiagnostics,
) : ImageAnalysis.Analyzer {
    private val handled = AtomicBoolean(false)
    private val reader = MultiFormatReader()
    private val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.CODE_128))
        put(DecodeHintType.TRY_HARDER, true)
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (handled.get()) {
            imageProxy.close()
            return
        }
        try {
            diagnostics.frames.incrementAndGet()
            diagnostics.frameWidth.set(imageProxy.width)
            diagnostics.frameHeight.set(imageProxy.height)
            diagnostics.rotationDegrees.set(imageProxy.imageInfo.rotationDegrees)
            val frame = imageProxy.luminanceSource() ?: return
            // ZXing's 1D readers only scan horizontal rows, and CameraX can
            // report rotation 0 (leaving a horizontal tag vertical in the
            // frame), so try the upright frame plus one quarter turn.
            var code: String? = null
            for (turn in quarterTurns(imageProxy.imageInfo.rotationDegrees)) {
                val result = runCatching {
                    var rotated = frame
                    repeat(turn) { rotated = rotated.rotateCounterClockwise() }
                    reader.decode(BinaryBitmap(HybridBinarizer(rotated)), hints)
                }.getOrNull() ?: continue
                diagnostics.lastDecodedText.set(result.text)
                code = KatMusicCode.normalize(result.text)
                if (code != null) break
            }
            if (code != null && handled.compareAndSet(false, true)) {
                Handler(Looper.getMainLooper()).post { onCodeScanned(code) }
            }
        } catch (_: Throwable) {
            // Bad camera frames should not stop the scanner.
        } finally {
            imageProxy.close()
        }
    }
}

/**
 * Copies the camera's luma plane into a tightly packed buffer so each rotated
 * copy has clean rows regardless of the source plane's row padding.
 */
private fun ImageProxy.luminanceSource(): LuminanceSource? {
    val frame = image ?: return null
    val plane = frame.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val width = frame.width
    val height = frame.height
    if (width <= 0 || height <= 0) return null
    val data = ByteArray(width * height)
    if (pixelStride == 1) {
        buffer.rewind()
        for (row in 0 until height) {
            val start = row * rowStride
            if (start + width > buffer.limit()) break
            buffer.position(start)
            buffer.get(data, row * width, width)
        }
    } else {
        for (row in 0 until height) {
            val start = row * rowStride
            for (col in 0 until width) {
                val index = start + col * pixelStride
                if (index < buffer.limit()) data[row * width + col] = buffer.get(index)
            }
        }
    }
    return PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
}
