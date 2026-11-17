package com.itzfallenme.katmusic.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.itzfallenme.katmusic.shared.share.KatMusicCode

private const val CODE_BLUE = 0xFF0877F9.toInt()
private const val CODE_CYAN = 0xFF21C4FF.toInt()
private const val CODE_NAVY = 0xFF061321.toInt()
private const val CARD_BACKGROUND = 0xFF070C13.toInt()
private const val CARD_SURFACE = 0xFF0E1722.toInt()
private const val CARD_OUTLINE = 0xFF203349.toInt()

/**
 * Builds KatMusic's own horizontal scan tag. Its bars are a Code 128 barcode
 * with a shared center scan-line and code-derived heights, so it looks like a
 * music waveform while remaining readable by KatMusic's in-app scanner.
 */
internal fun createKatMusicCodeTag(code: String, width: Int = 920, height: Int = 280): Bitmap? =
    runCatching {
        val normalized = KatMusicCode.normalize(code) ?: error("Invalid KatMusic Code")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawKatMusicCodeTag(Canvas(bitmap), normalized, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        bitmap
    }.getOrNull()

internal fun createKatMusicCodeCard(
    title: String,
    artist: String,
    code: String,
    album: String? = null,
    brandLogo: Bitmap? = null,
): Bitmap? = runCatching {
    val normalized = KatMusicCode.normalize(code) ?: error("Invalid KatMusic Code")
    val bitmap = Bitmap.createBitmap(1080, 1100, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val white = 0xFFF5F6F2.toInt()
    val muted = 0xFF9BA39A.toInt()

    canvas.drawColor(CARD_BACKGROUND)
    paint.color = CARD_SURFACE
    canvas.drawRoundRect(RectF(34f, 34f, 1046f, 1066f), 42f, 42f, paint)
    paint.color = CARD_OUTLINE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawRoundRect(RectF(35.5f, 35.5f, 1044.5f, 1064.5f), 42f, 42f, paint)
    paint.style = Paint.Style.FILL

    brandLogo?.let { logo ->
        canvas.drawBitmap(
            logo,
            null,
            RectF(82f, 64f, 148f, 130f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }
    paint.color = white
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 42f
    canvas.drawText("KAT MUSIC", if (brandLogo != null) 170f else 82f, 112f, paint)

    paint.color = CODE_BLUE
    canvas.drawRoundRect(RectF(82f, 136f, 252f, 174f), 19f, 19f, paint)
    paint.color = white
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 18f
    canvas.drawText("SONG CODE", 100f, 161f, paint)

    paint.color = white
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 46f
    canvas.drawText(ellipsize(title.ifBlank { "Shared song" }, paint, 900f), 82f, 250f, paint)
    paint.color = muted
    paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    paint.textSize = 32f
    val detailLine = listOf(artist.ifBlank { "KatMusic" }, album?.takeIf(String::isNotBlank))
        .filterNotNull()
        .joinToString(" · ")
    canvas.drawText(ellipsize(detailLine, paint, 900f), 82f, 302f, paint)

    paint.color = CODE_CYAN
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 24f
    canvas.drawText("SCAN WITH KATMUSIC", 82f, 360f, paint)

    drawKatMusicCodeTag(canvas, normalized, RectF(82f, 388f, 998f, 690f))

    paint.color = CARD_OUTLINE
    canvas.drawRoundRect(RectF(82f, 718f, 998f, 804f), 20f, 20f, paint)
    paint.color = CODE_CYAN
    paint.typeface = Typeface.create("monospace", Typeface.BOLD)
    paint.textSize = 36f
    canvas.drawText(normalized, (1080f - paint.measureText(normalized)) / 2f, 775f, paint)

    paint.color = muted
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textSize = 22f
    val instruction = "Or enter this code in Search"
    canvas.drawText(instruction, (1080f - paint.measureText(instruction)) / 2f, 850f, paint)

    paint.color = CARD_OUTLINE
    canvas.drawRect(82f, 902f, 998f, 904f, paint)
    paint.color = muted
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textSize = 23f
    val footer = "ONE CODE · ANY DEVICE · YOUR MUSIC"
    canvas.drawText(footer, (1080f - paint.measureText(footer)) / 2f, 976f, paint)
    bitmap
}.getOrNull()

private fun drawKatMusicCodeTag(canvas: Canvas, code: String, bounds: RectF) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            CODE_BLUE, CODE_CYAN, Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(bounds, 26f, 26f, paint)

    val insetX = 26f
    val insetY = 22f
    val innerWidth = (bounds.width() - insetX * 2).toInt()
    val innerHeight = (bounds.height() - insetY * 2).toInt()
    // Keep the barcode compact and visually open; the scanner accepts the
    // compact payload and normalizes it back to the formatted manual code.
    val barcodePayload = code.filter { it.isLetterOrDigit() }.removePrefix("KM")
    val matrix = Code128Writer().encode(
        barcodePayload,
        BarcodeFormat.CODE_128,
        innerWidth,
        innerHeight,
        mapOf(EncodeHintType.MARGIN to 10),
    )
    val scaleX = innerWidth.toFloat() / matrix.width
    val scanRow = matrix.height / 2
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CODE_NAVY }
    val centerY = bounds.centerY()
    var x = 0
    var barIndex = 0
    while (x < matrix.width) {
        if (!matrix.get(x, scanRow)) {
            x++
            continue
        }
        val runStart = x
        while (x < matrix.width && matrix.get(x, scanRow)) x++
        val seed = code[barIndex % code.length].code
        val secondSeed = code[(barIndex * 7) % code.length].code
        // Keep every bar tall enough to cross the scan row reliably while
        // still reading as a soundwave. Rounded ends are kept subtle.
        val heightRatio = 0.55f + ((seed * 31 + secondSeed * 17 + barIndex * 47) % 45) / 100f
        val barHeight = innerHeight * heightRatio
        val left = bounds.left + insetX + runStart * scaleX
        val right = bounds.left + insetX + x * scaleX
        val top = centerY - barHeight / 2f
        val radius = minOf(3f, (right - left) / 2f)
        canvas.drawRoundRect(RectF(left, top, right, top + barHeight), radius, radius, barPaint)
        barIndex++
    }
}

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end) + "…"
}
