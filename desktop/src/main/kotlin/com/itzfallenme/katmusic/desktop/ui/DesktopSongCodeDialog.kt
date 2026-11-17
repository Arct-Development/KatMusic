package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.share.KatMusicCode
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val TagBlue = Color(0xFF0877F9)
private val TagCyan = Color(0xFF21C4FF)
private val TagNavy = Color(0xFF061321)
private val CardBackground = Color(0xFF070C13)
private val CardSurface = Color(0xFF0E1722)
private val CardOutline = Color(0xFF203349)
private const val SHARE_PAGE = "https://katmusic.pages.dev/share.html"

/** Desktop share surface for the same KatMusic Code Android scans as Code 128. */
@Composable
fun DesktopSongCodeDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    val videoId = track.videoId ?: track.id.takeUnless { track.isLocal } ?: return
    val code = remember(videoId) { KatMusicCode.encode(videoId) } ?: return
    val clipboard = LocalClipboardManager.current
    val logo = remember { loadKatMusicLogo() }
    var feedback by remember(code) { mutableStateOf<String?>(null) }
    val tag = remember(code) { createWaveform(code, TAG_MATRIX_WIDTH, TAG_MATRIX_HEIGHT) }
    val shareLink = "$SHARE_PAGE?code=$code"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 480.dp, max = 600.dp),
            shape = RoundedCornerShape(28.dp),
            color = CardSurface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    logo?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "KatMusic logo",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    }
                    Column {
                        Text(
                            "KAT MUSIC",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            "SONG CODE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TagCyan,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        track.title.ifBlank { "Shared song" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · ")
                            .ifBlank { "KatMusic" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    "SCAN WITH KATMUSIC",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TagCyan,
                )
                Code128Waveform(tag, Modifier.fillMaxWidth().height(124.dp))

                Surface(
                    color = Color(0xFF132337),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = code,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = TagCyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Scan the horizontal tag in Android, or enter the code from Search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                feedback?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            feedback = "KatMusic Code copied"
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy code") }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(shareLink))
                            feedback = "Share link copied"
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy link") }
                    Button(
                        onClick = {
                            exportSongCodeCard(track, code).fold(
                                onSuccess = { file ->
                                    feedback = file?.let { "Card saved to ${it.absolutePath}" }
                                        ?: "Export cancelled"
                                },
                                onFailure = { error ->
                                    feedback = error.message ?: "Couldn't save the card"
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save card") }
                }
            }
        }
    }
}

private const val TAG_MATRIX_WIDTH = 920
private const val TAG_MATRIX_HEIGHT = 280

private data class WaveBar(
    val start: Int,
    val end: Int,
    val heightRatio: Float,
)

private data class Waveform(
    val matrixWidth: Int,
    val bars: List<WaveBar>,
)

private fun createWaveform(code: String, width: Int, height: Int): Waveform {
    val normalized = KatMusicCode.normalize(code) ?: error("Invalid KatMusic Code")
    val payload = normalized.filter(Char::isLetterOrDigit).removePrefix("KM")
    val matrix = Code128Writer().encode(
        payload,
        BarcodeFormat.CODE_128,
        width,
        height,
        mapOf(EncodeHintType.MARGIN to 10),
    )
    val row = matrix.height / 2
    val bars = buildList {
        var x = 0
        var barIndex = 0
        while (x < matrix.width) {
            if (!matrix.get(x, row)) {
                x++
                continue
            }
            val start = x
            while (x < matrix.width && matrix.get(x, row)) x++
            val first = normalized[barIndex % normalized.length].code
            val second = normalized[(barIndex * 7) % normalized.length].code
            val ratio = 0.55f + ((first * 31 + second * 17 + barIndex * 47) % 45) / 100f
            add(WaveBar(start, x, ratio))
            barIndex++
        }
    }
    return Waveform(matrix.width, bars)
}

@Composable
private fun Code128Waveform(
    waveform: Waveform,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        drawRoundRect(
            color = TagBlue,
            size = size,
            cornerRadius = CornerRadius(20.dp.toPx()),
        )
        val horizontalInset = 12.dp.toPx()
        val verticalInset = 10.dp.toPx()
        val innerWidth = size.width - horizontalInset * 2
        val innerHeight = size.height - verticalInset * 2
        val centerY = size.height / 2
        waveform.bars.forEach { bar ->
            val left = horizontalInset + innerWidth * bar.start / waveform.matrixWidth
            val right = horizontalInset + innerWidth * bar.end / waveform.matrixWidth
            val barHeight = innerHeight * bar.heightRatio
            drawRoundRect(
                color = TagNavy,
                topLeft = Offset(left, centerY - barHeight / 2),
                size = Size(right - left, barHeight),
                cornerRadius = CornerRadius(minOf(2.dp.toPx(), (right - left) / 2)),
            )
        }
    }
}

private fun exportSongCodeCard(track: Track, code: String): Result<File?> = runCatching {
    val safeTitle = track.title
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .take(48)
        .ifBlank { "song" }
    val chooser = JFileChooser().apply {
        dialogTitle = "Save KatMusic Code card"
        fileFilter = FileNameExtensionFilter("PNG image", "png")
        selectedFile = File("${safeTitle}_katmusic_code.png")
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@runCatching null
    val selected = chooser.selectedFile ?: return@runCatching null
    val target = if (selected.extension.equals("png", ignoreCase = true)) {
        selected
    } else {
        File(selected.parentFile, "${selected.name}.png")
    }
    val image = renderDesktopSongCodeCard(track, code)
    check(ImageIO.write(image, "png", target)) { "PNG encoder unavailable" }
    target
}

internal fun renderDesktopSongCodeCard(track: Track, code: String): BufferedImage {
    val image = BufferedImage(1080, 1100, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    val normalized = KatMusicCode.normalize(code) ?: error("Invalid KatMusic Code")
    val waveform = createWaveform(normalized, TAG_MATRIX_WIDTH, TAG_MATRIX_HEIGHT)
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.color = awt(CardBackground)
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = awt(CardSurface)
        graphics.fillRoundRect(34, 34, 1012, 1032, 42, 42)
        graphics.color = awt(CardOutline)
        graphics.stroke = BasicStroke(2f)
        graphics.drawRoundRect(35, 35, 1010, 1030, 42, 42)

        loadKatMusicLogoAwt()?.let { logo ->
            graphics.drawImage(logo, 80, 64, 66, 66, null)
        }
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 42)
        graphics.color = AwtColor.WHITE
        graphics.drawString("KAT MUSIC", 164, 111)
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        graphics.color = awt(TagCyan)
        graphics.drawString("SONG CODE", 82, 165)

        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 46)
        graphics.color = AwtColor(0xF5, 0xF8, 0xFC)
        graphics.drawString(ellipsize(graphics, track.title.ifBlank { "Shared song" }, 900), 82, 250)
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 32)
        graphics.color = AwtColor(0x9A, 0xA8, 0xB8)
        val details = listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · ")
            .ifBlank { "KatMusic" }
        graphics.drawString(ellipsize(graphics, details, 900), 82, 302)

        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 24)
        graphics.color = awt(TagCyan)
        graphics.drawString("SCAN WITH KATMUSIC", 82, 360)
        drawWaveform(graphics, waveform, 82, 388, 916, 302)

        graphics.color = awt(CardOutline)
        graphics.fillRoundRect(82, 718, 916, 86, 20, 20)
        graphics.font = Font(Font.MONOSPACED, Font.BOLD, 36)
        graphics.color = awt(TagCyan)
        drawCentered(graphics, normalized, 1080, 775)
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 22)
        graphics.color = AwtColor(0x9A, 0xA8, 0xB8)
        drawCentered(graphics, "Or enter this code in Search", 1080, 850)
        graphics.color = awt(CardOutline)
        graphics.fillRect(82, 902, 916, 2)
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 23)
        graphics.color = AwtColor(0x9A, 0xA8, 0xB8)
        drawCentered(graphics, "ONE CODE · ANY DEVICE · YOUR MUSIC", 1080, 976)
    } finally {
        graphics.dispose()
    }
    return image
}

private fun drawWaveform(
    graphics: Graphics2D,
    waveform: Waveform,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    graphics.color = awt(TagBlue)
    graphics.fillRoundRect(x, y, width, height, 26, 26)
    val insetX = 12f
    val insetY = 10f
    val innerWidth = width - 2 * insetX
    val innerHeight = height - 2 * insetY
    val centerY = y + height / 2f
    graphics.color = awt(TagNavy)
    waveform.bars.forEach { bar ->
        val left = x + insetX + innerWidth * bar.start / waveform.matrixWidth
        val right = x + insetX + innerWidth * bar.end / waveform.matrixWidth
        val barHeight = innerHeight * bar.heightRatio
        val radius = minOf(4f, (right - left) / 2f)
        graphics.fillRoundRect(
            left.toInt(),
            (centerY - barHeight / 2f).toInt(),
            (right - left).toInt().coerceAtLeast(1),
            barHeight.toInt().coerceAtLeast(1),
            (radius * 2).toInt(),
            (radius * 2).toInt(),
        )
    }
}

private fun loadKatMusicLogo(): ImageBitmap? = runCatching {
    val bytes = Thread.currentThread().contextClassLoader
        .getResourceAsStream("katmusic_logo.png")
        ?.use { it.readBytes() }
        ?: error("KatMusic logo resource missing")
    ImageIO.read(ByteArrayInputStream(bytes)).toComposeImageBitmap()
}.getOrNull()

private fun loadKatMusicLogoAwt(): BufferedImage? = runCatching {
    Thread.currentThread().contextClassLoader
        .getResourceAsStream("katmusic_logo.png")
        ?.use { ImageIO.read(it) }
}.getOrNull()

private fun awt(color: Color) = AwtColor(color.red, color.green, color.blue, color.alpha)

private fun drawCentered(graphics: Graphics2D, text: String, canvasWidth: Int, baseline: Int) {
    graphics.drawString(text, (canvasWidth - graphics.fontMetrics.stringWidth(text)) / 2, baseline)
}

private fun ellipsize(graphics: Graphics2D, text: String, maxWidth: Int): String {
    if (graphics.fontMetrics.stringWidth(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && graphics.fontMetrics.stringWidth("${text.take(end)}…") > maxWidth) end--
    return "${text.take(end)}…"
}
