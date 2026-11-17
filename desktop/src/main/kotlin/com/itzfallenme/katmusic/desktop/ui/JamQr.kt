package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** A crisp QR rendered module-by-module, keeping vector edges at any window size. */
@Composable
fun JamQrCode(
    value: String,
    modifier: Modifier = Modifier,
    size: Dp = 188.dp,
) {
    val matrix = remember(value) {
        runCatching {
            QRCodeWriter().encode(
                value,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 1),
            )
        }.getOrNull()
    }
    Canvas(modifier = modifier.then(Modifier.size(size))) {
        drawRect(Color.White)
        val qr = matrix ?: return@Canvas
        val cell = this.size.minDimension / qr.width
        for (y in 0 until qr.height) {
            for (x in 0 until qr.width) {
                if (qr.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.35f, cell + 0.35f),
                    )
                }
            }
        }
    }
}
