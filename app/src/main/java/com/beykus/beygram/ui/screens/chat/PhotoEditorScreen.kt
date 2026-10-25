package com.beykus.beygram.ui.screens.chat

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.CropEdge
import com.beykus.beygram.data.media.NormalizedCrop
import com.beykus.beygram.data.media.PhotoEditor
import com.beykus.beygram.data.media.PhotoExportQuality
import com.beykus.beygram.data.media.PhotoRotation
import com.beykus.beygram.data.media.cropForAspect
import com.beykus.beygram.data.media.cropPixels
import com.beykus.beygram.data.media.moveCrop
import com.beykus.beygram.data.media.resizeCrop
import com.beykus.beygram.data.media.rotateCrop
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import java.io.File
import kotlin.math.hypot

/**
 * Crop/rotate editor for one photo. The source file is never modified; [onSave] hands the chosen
 * transform to [AttachmentState], which imports the export as a new attachment. Camera captures
 * also offer "send without changes", so the original is never silently replaced.
 */
@Composable
internal fun PhotoEditorScreen(
    sourcePath: String,
    saving: Boolean,
    error: String?,
    canSendOriginal: Boolean,
    onCancel: () -> Unit,
    onSendOriginal: () -> Unit,
    onSave: (PhotoRotation, NormalizedCrop, PhotoExportQuality, Boolean) -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val editor = remember { PhotoEditor(context) }
    var preview by remember(sourcePath) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(sourcePath) { mutableStateOf(false) }
    var rotation by remember(sourcePath) { mutableStateOf(PhotoRotation.None) }
    var crop by remember(sourcePath) { mutableStateOf(NormalizedCrop()) }
    var aspect by remember(sourcePath) { mutableStateOf<Float?>(null) }
    var quality by remember(sourcePath) { mutableStateOf(PhotoExportQuality.High) }
    var keepOriginal by remember(sourcePath) { mutableStateOf(false) }

    LaunchedEffect(sourcePath) {
        val loaded = editor.loadPreview(File(sourcePath))
        preview = loaded?.bitmap
        loadFailed = loaded == null
    }
    BackHandler(enabled = !saving) { onCancel() }

    // The preview is rotated in memory; the source bitmap stays untouched for repeated rotations.
    val shown = remember(preview, rotation) { preview?.let { editor.rotatePreview(it, rotation) } }
    val shownWidth = shown?.width ?: 0
    val shownHeight = shown?.height ?: 0

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = !saving, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.photo_editor_close_description), tint = palette.textPrimary)
            }
            Text(
                stringResource(R.string.photo_editor_title), color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { onSave(rotation, crop, quality, keepOriginal) },
                enabled = !saving && shown != null && !loadFailed
            ) {
                Text(stringResource(R.string.photo_editor_apply), color = if (saving) palette.textSecondary else palette.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        if (saving) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent, trackColor = palette.surface2)
            Text(
                stringResource(R.string.photo_editor_saving), color = palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).errorAnnouncement()
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
            when {
                loadFailed -> Text(
                    stringResource(R.string.photo_editor_preview_failed), color = palette.danger, fontSize = 14.sp,
                    modifier = Modifier.errorAnnouncement()
                )
                shown != null -> CropEditor(
                    bitmap = shown,
                    crop = crop,
                    onCropChange = { crop = it; aspect = null },
                    modifier = Modifier.fillMaxSize()
                )
                else -> LinearProgressIndicator(Modifier.width(120.dp), color = palette.accent, trackColor = palette.surface2)
            }
        }
        error?.let { message ->
            Text(
                message, color = palette.danger, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).errorAnnouncement()
            )
        }
        Column(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorAction(
                    icon = Icons.Rounded.RotateLeft,
                    label = stringResource(R.string.photo_editor_rotate_left),
                    enabled = !saving,
                    onClick = {
                        rotation = rotation.rotatedLeft()
                        crop = rotateCrop(crop, clockwise = false)
                        aspect = null
                    }
                )
                EditorAction(
                    icon = Icons.Rounded.RotateRight,
                    label = stringResource(R.string.photo_editor_rotate_right),
                    enabled = !saving,
                    onClick = {
                        rotation = rotation.rotatedRight()
                        crop = rotateCrop(crop, clockwise = true)
                        aspect = null
                    }
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.photo_editor_crop_hint), color = palette.textSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectChip(stringResource(R.string.photo_editor_aspect_free), aspect == null && crop.isFull, !saving) {
                    aspect = null
                    crop = NormalizedCrop()
                }
                AspectChip(stringResource(R.string.photo_editor_aspect_square), aspect == 1f, !saving) {
                    aspect = 1f
                    crop = cropForAspect(1f, shownWidth, shownHeight, crop)
                }
                AspectChip(stringResource(R.string.photo_editor_aspect_four_three), aspect == 4f / 3f, !saving) {
                    aspect = 4f / 3f
                    crop = cropForAspect(4f / 3f, shownWidth, shownHeight, crop)
                }
                AspectChip(stringResource(R.string.photo_editor_aspect_sixteen_nine), aspect == 16f / 9f, !saving) {
                    aspect = 16f / 9f
                    crop = cropForAspect(16f / 9f, shownWidth, shownHeight, crop)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.photo_editor_quality_title), color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityChip(stringResource(R.string.photo_editor_quality_high), quality == PhotoExportQuality.High, !saving) { quality = PhotoExportQuality.High }
                QualityChip(stringResource(R.string.photo_editor_quality_balanced), quality == PhotoExportQuality.Balanced, !saving) { quality = PhotoExportQuality.Balanced }
                QualityChip(stringResource(R.string.photo_editor_quality_compact), quality == PhotoExportQuality.Compact, !saving) { quality = PhotoExportQuality.Compact }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    .toggleable(value = keepOriginal, enabled = !saving, role = Role.Checkbox) { keepOriginal = it },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(checked = keepOriginal, onCheckedChange = null, enabled = !saving, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.photo_editor_keep_original), color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.photo_editor_keep_original_hint), color = palette.textSecondary, fontSize = 11.sp)
                }
            }
            if (canSendOriginal) {
                TextButton(onClick = onSendOriginal, enabled = !saving, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(stringResource(R.string.photo_editor_send_original), color = palette.accent)
                }
            }
        }
    }
}

@Composable
private fun CropEditor(
    bitmap: Bitmap,
    crop: NormalizedCrop,
    onCropChange: (NormalizedCrop) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleTouch = with(density) { 32.dp.toPx() }.toDouble()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val availableWidth = with(density) { maxWidth.toPx() }
        val availableHeight = with(density) { maxHeight.toPx() }
        val scale = minOf(availableWidth / bitmap.width, availableHeight / bitmap.height)
        val imageWidth = with(density) { (bitmap.width * scale).toDp() }
        val imageHeight = with(density) { (bitmap.height * scale).toDp() }
        val cropDescription = stringResource(R.string.photo_editor_crop_description)
        val moveLeft = stringResource(R.string.photo_editor_move_left)
        val moveRight = stringResource(R.string.photo_editor_move_right)
        val moveUp = stringResource(R.string.photo_editor_move_up)
        val moveDown = stringResource(R.string.photo_editor_move_down)
        Box(
            Modifier.size(imageWidth, imageHeight)
                .pointerInput(bitmap.width, bitmap.height) {
                    var edge: CropEdge? = null
                    var dragging = false
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toInt().coerceAtLeast(1)
                            val height = size.height.toInt().coerceAtLeast(1)
                            val rect = cropPixels(crop, width, height)
                            fun near(x: Int, y: Int) = hypot((offset.x - x).toDouble(), (offset.y - y).toDouble()) <= handleTouch
                            edge = when {
                                near(rect.left, rect.top) -> CropEdge.TopLeft
                                near(rect.left + rect.width, rect.top) -> CropEdge.TopRight
                                near(rect.left, rect.top + rect.height) -> CropEdge.BottomLeft
                                near(rect.left + rect.width, rect.top + rect.height) -> CropEdge.BottomRight
                                else -> null
                            }
                            dragging = edge != null ||
                                (offset.x >= rect.left && offset.x <= rect.left + rect.width &&
                                    offset.y >= rect.top && offset.y <= rect.top + rect.height)
                        },
                        onDrag = { change, drag ->
                            if (dragging) {
                                change.consume()
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val height = size.height.toFloat().coerceAtLeast(1f)
                                val dx = drag.x / width
                                val dy = drag.y / height
                                val currentEdge = edge
                                onCropChange(if (currentEdge != null) resizeCrop(crop, currentEdge, dx, dy) else moveCrop(crop, dx, dy))
                            }
                        },
                        onDragEnd = { dragging = false; edge = null },
                        onDragCancel = { dragging = false; edge = null }
                    )
                }
                .semantics {
                    contentDescription = cropDescription
                    customActions = listOf(
                        CustomAccessibilityAction(moveLeft) { onCropChange(moveCrop(crop, -ACCESSIBLE_CROP_STEP, 0f)); true },
                        CustomAccessibilityAction(moveRight) { onCropChange(moveCrop(crop, ACCESSIBLE_CROP_STEP, 0f)); true },
                        CustomAccessibilityAction(moveUp) { onCropChange(moveCrop(crop, 0f, -ACCESSIBLE_CROP_STEP)); true },
                        CustomAccessibilityAction(moveDown) { onCropChange(moveCrop(crop, 0f, ACCESSIBLE_CROP_STEP)); true }
                    )
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                val rect = cropPixels(crop, size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                val left = rect.left.toFloat()
                val top = rect.top.toFloat()
                val right = (rect.left + rect.width).toFloat()
                val bottom = (rect.top + rect.height).toFloat()
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset(0f, 0f), Size(size.width, top))
                drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))
                drawRect(dim, Offset(0f, top), Size(left, bottom - top))
                drawRect(dim, Offset(right, top), Size(size.width - right, bottom - top))
                drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2.dp.toPx()))
                val handle = 7.dp.toPx()
                listOf(
                    Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)
                ).forEach { drawCircle(Color.White, handle, it) }
            }
        }
    }
}

@Composable
private fun EditorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.defaultMinSize(minHeight = 48.dp)
            .background(palette.surface2, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (enabled) palette.textPrimary else palette.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun AspectChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Box(
        Modifier.defaultMinSize(minHeight = 48.dp)
            .background(if (selected) palette.accentSoft else palette.surface2, RoundedCornerShape(14.dp))
            .toggleable(value = selected, enabled = enabled, role = Role.RadioButton) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) palette.accentInk else palette.textPrimary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun QualityChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Box(
        Modifier.defaultMinSize(minHeight = 48.dp)
            .background(if (selected) palette.accentSoft else palette.surface2, RoundedCornerShape(14.dp))
            .toggleable(value = selected, enabled = enabled, role = Role.RadioButton) { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) palette.accentInk else palette.textPrimary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private const val ACCESSIBLE_CROP_STEP = 0.05f
