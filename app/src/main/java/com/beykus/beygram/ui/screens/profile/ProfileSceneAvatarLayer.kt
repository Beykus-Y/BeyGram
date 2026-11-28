package com.beykus.beygram.ui.screens.profile

import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.LocalBackgroundMediaDownload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** One square image layer morphs continuously from circular avatar to edge-to-edge full-bleed cover and into toolbar. */
@Composable
internal fun ProfileSceneAvatarLayer(
    initials: String,
    color: Color,
    photo: AvatarPhoto?,
    frame: () -> ProfileSceneFrame?,
    reveal: () -> Float,
    visibility: () -> Float,
    loadLargePhoto: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    modifier: Modifier = Modifier,
    surfaceFadeColor: Color = Color.Black,
    photoCount: Int = 0,
    photoIndex: Int = 0
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val mask = remember { Path() }

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .clearAndSetSemantics {}
            .drawWithContent {
                val scene = frame() ?: return@drawWithContent
                val rect = scene.avatarRect
                val left = rect.left - origin.x
                val top = rect.top - origin.y
                val right = rect.right - origin.x
                val bottom = rect.bottom - origin.y
                val circleRadius = minOf(rect.width, rect.height) / 2f
                val morph = reveal() * (1f - scene.collapse)
                val radius = circleRadius * (1f - morph)
                val clipTop = (scene.clipTopPx - origin.y).coerceAtLeast(0f)
                if (bottom <= clipTop || right <= left || bottom <= top) return@drawWithContent
                mask.rewind()
                mask.addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius)))
                clipRect(top = clipTop) {
                    clipPath(mask) {
                        this@drawWithContent.drawContent()
                        if (morph > 0.01f) {
                            val coverHeight = (bottom - top).coerceAtLeast(1f)
                            val coverWidth = (right - left).coerceAtLeast(1f)
                            // Top scrim keeps overlaid back button & menu readable.
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.44f * morph), Color.Transparent),
                                    startY = top,
                                    endY = top + coverHeight * 0.28f
                                ),
                                topLeft = Offset(left, top),
                                size = Size(coverWidth, coverHeight * 0.28f)
                            )
                            // Multi-stop bottom gradient preserves white text contrast and blends naturally into palette.screen.
                            val scrimStartY = bottom - coverHeight * 0.52f
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    0.42f to Color.Black.copy(alpha = 0.34f * morph),
                                    0.78f to lerp(Color.Black, surfaceFadeColor, 0.45f).copy(alpha = 0.72f * morph),
                                    1.00f to surfaceFadeColor.copy(alpha = 0.98f * morph),
                                    startY = scrimStartY,
                                    endY = bottom
                                ),
                                topLeft = Offset(left, scrimStartY),
                                size = Size(coverWidth, coverHeight * 0.52f)
                            )
                        }
                    }
                    val haloAlpha = ((1f - reveal()) * (1f - scene.collapse) * visibility()).coerceIn(0f, 1f)
                    if (haloAlpha > 0.01f) {
                        drawPath(
                            path = mask,
                            color = Color.White.copy(alpha = 0.22f * haloAlpha),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
    ) {
        val backgroundDownload = LocalBackgroundMediaDownload.current
        val smallId = photo?.smallFileId?.takeIf { it > 0 }
        val bigId = photo?.bigFileId?.takeIf { loadLargePhoto && it > 0 && it != smallId }
        val smallPath = smallId?.let { id ->
            val file by remember(id, observeFile) { observeFile(id) }.collectAsStateWithLifecycle()
            file.localPath
        }
        val bigPath = bigId?.let { id ->
            val file by remember(id, observeFile) { observeFile(id) }.collectAsStateWithLifecycle()
            file.localPath
        }
        val thumbnail = rememberProfileSceneBitmap(smallPath, 448)
        val largePhoto = rememberProfileSceneBitmap(bigPath, 1280)
        LaunchedEffect(smallId) { smallId?.let { (backgroundDownload ?: downloadFile)(it) } }
        LaunchedEffect(bigId) { bigId?.let { (backgroundDownload ?: downloadFile)(it) } }
        Box(
            Modifier.layout { measurable, constraints ->
                val side = (frame()?.layerSidePx ?: 112.dp.toPx()).roundToInt().coerceAtLeast(1)
                val image = measurable.measure(Constraints.fixed(side, side))
                layout(constraints.constrainWidth(side), constraints.constrainHeight(side)) {
                    image.place(0, 0)
                }
            }.graphicsLayer {
                val scene = frame()
                val rect = scene?.avatarRect
                if (scene == null || rect == null) {
                    alpha = 0f
                } else {
                    scaleX = scene.layerScale
                    scaleY = scene.layerScale
                    translationX = (rect.left + rect.right) / 2f - origin.x - size.width / 2f
                    translationY = (rect.top + rect.bottom) / 2f - origin.y - size.height / 2f + scene.parallaxPx
                    alpha = visibility()
                }
            }.background(color),
            contentAlignment = Alignment.Center
        ) {
            val image = largePhoto ?: thumbnail
            if (image != null) {
                Image(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Avatar(initials, color, 112.dp)
            }
        }
        if (photoCount > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        val scene = frame()
                        if (scene == null) {
                            alpha = 0f
                        } else {
                            translationY = (scene.clipTopPx - origin.y) + 6.dp.toPx()
                            alpha = (1f - scene.collapse * 4f).coerceAtLeast(0f) * visibility()
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (index in 0 until photoCount) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .background(
                                color = Color.White.copy(alpha = if (index == photoIndex) 0.95f else 0.32f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberProfileSceneBitmap(path: String?, maxDimension: Int): ImageBitmap? {
    if (path == null) return null
    return remember(path, maxDimension) { mutableStateOf<ImageBitmap?>(null) }.also { bitmap ->
        LaunchedEffect(path, maxDimension) {
            bitmap.value = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > maxDimension) {
                            decoder.setTargetSize(
                                (info.size.width.toLong() * maxDimension / longest).toInt().coerceAtLeast(1),
                                (info.size.height.toLong() * maxDimension / longest).toInt().coerceAtLeast(1)
                            )
                        }
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }.value
}
