package com.beykus.beygram.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface QrRender {
    data object Loading : QrRender
    data class Ready(val image: ImageBitmap) : QrRender
    data object Failed : QrRender
}

/**
 * Renders a Telegram login link with ZXing. Encoding runs off the main thread; the bitmap is only built
 * from the link handed over by TDLib and is never written to logs or disk.
 */
@Composable
fun TelegramQrCode(
    content: String,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(64)
    val render by produceState<QrRender>(QrRender.Loading, content, sizePx) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                QrRender.Ready(encodeQr(content, sizePx).asImageBitmap())
            }.getOrElse { QrRender.Failed }
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        when (val state = render) {
            QrRender.Loading -> CircularProgressIndicator(strokeWidth = 2.dp)
            is QrRender.Ready -> Image(
                bitmap = state.image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                filterQuality = FilterQuality.None
            )
            QrRender.Failed -> Text(
                text = stringResource(R.string.auth_qr_render_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun encodeQr(content: String, sizePx: Int): Bitmap {
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val row = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[row + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
