package com.beykus.beygram.ui.screens.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AudioQualityOption
import com.beykus.beygram.data.media.VideoQualityOption
import com.beykus.beygram.data.media.audioQualityBitrate
import com.beykus.beygram.data.media.estimatedAudioBytes
import com.beykus.beygram.data.media.estimatedVideoBytes
import com.beykus.beygram.data.media.videoQualityAudioBitrate
import com.beykus.beygram.data.media.videoQualityVideoBitrate
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import java.io.Serializable
import java.util.Locale

/** Per-file quality choice. It belongs to one staged attachment and never changes another file. */
internal enum class MediaQualityMode { Compact, Balanced, Original }

/** Telegram delivery semantics: a video message or the untouched bytes as a document. */
internal enum class MediaDeliveryMode { Media, File }

internal enum class MediaQualityKind { Video, Audio }

internal enum class MediaPreparationHint {
    AlreadyEfficient,
    UnknownMetadata,
    NotWorthIt,
    MetadataPreserved,
    TooLongAsFile,
    TooLargeAsFile
}

/**
 * State of one video/audio preparation. The staged original is never replaced: [prepared] points
 * at an extra file in the same upload directory, so cancelling, failing or switching back to the
 * original never loses the user's source media.
 */
internal data class MediaPreparation(
    val mode: MediaQualityMode = MediaQualityMode.Balanced,
    val delivery: MediaDeliveryMode = MediaDeliveryMode.Media,
    val prepared: OutgoingMedia? = null,
    val processing: Boolean = false,
    val progress: Float? = null,
    val hint: MediaPreparationHint? = null,
    val failedMode: MediaQualityMode? = null,
    val error: String? = null
) : Serializable

/** Quality applies only to videos sent as media and to plain audio documents. */
internal fun OutgoingMedia.qualityKind(): MediaQualityKind? = when {
    kind == MediaKind.Video -> MediaQualityKind.Video
    kind == MediaKind.Document && mimeType.startsWith("audio/") -> MediaQualityKind.Audio
    else -> null
}

internal fun preparationFor(preparations: Map<String, MediaPreparation>, item: OutgoingMedia): MediaPreparation? =
    preparations[item.path]

/**
 * The exact item handed to the repository. A "File" video is sent as a document with its original
 * bytes; "Original" keeps the video message but skips re-encoding; compact/balanced use [prepared].
 */
internal fun outgoingMediaFor(item: OutgoingMedia, preparation: MediaPreparation?): OutgoingMedia {
    if (preparation == null) return item
    if (item.kind == MediaKind.Video && preparation.delivery == MediaDeliveryMode.File) {
        return item.copy(kind = MediaKind.Document)
    }
    if (preparation.mode == MediaQualityMode.Original) return item
    return preparation.prepared ?: item
}

internal fun MediaQualityMode.toVideoOption(): VideoQualityOption = when (this) {
    MediaQualityMode.Compact -> VideoQualityOption.Compact
    MediaQualityMode.Balanced -> VideoQualityOption.Balanced
    MediaQualityMode.Original -> VideoQualityOption.Original
}

internal fun MediaQualityMode.toAudioOption(): AudioQualityOption = when (this) {
    MediaQualityMode.Compact -> AudioQualityOption.Compact
    MediaQualityMode.Balanced -> AudioQualityOption.Balanced
    MediaQualityMode.Original -> AudioQualityOption.Original
}

/** Approximate output size; the actual size is shown only after the staging pipeline finishes. */
internal fun qualityEstimateBytes(item: OutgoingMedia, mode: MediaQualityMode): Long? {
    if (mode == MediaQualityMode.Original) return item.size.takeIf { it > 0 }
    val seconds = item.durationSeconds
    if (seconds <= 0) return null
    return when (item.qualityKind()) {
        MediaQualityKind.Video -> estimatedVideoBytes(
            seconds,
            videoQualityVideoBitrate(mode.toVideoOption()),
            videoQualityAudioBitrate(mode.toVideoOption())
        ).takeIf { it > 0 }
        MediaQualityKind.Audio -> estimatedAudioBytes(seconds, audioQualityBitrate(mode.toAudioOption())).takeIf { it > 0 }
        null -> null
    }
}

/** Source bitrate including the audio track; 0 when the duration is unknown. */
internal fun OutgoingMedia.estimatedBitrate(): Int =
    if (durationSeconds > 0) ((size * 8L) / durationSeconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0

@StringRes
internal fun MediaQualityMode.labelRes(): Int = when (this) {
    MediaQualityMode.Compact -> R.string.composer_quality_mode_compact
    MediaQualityMode.Balanced -> R.string.composer_quality_mode_balanced
    MediaQualityMode.Original -> R.string.composer_quality_mode_original
}

@StringRes
internal fun MediaQualityMode.descriptionRes(kind: MediaQualityKind): Int = when (kind) {
    MediaQualityKind.Video -> when (this) {
        MediaQualityMode.Compact -> R.string.composer_quality_video_compact_desc
        MediaQualityMode.Balanced -> R.string.composer_quality_video_balanced_desc
        MediaQualityMode.Original -> R.string.composer_quality_video_original_desc
    }
    MediaQualityKind.Audio -> when (this) {
        MediaQualityMode.Compact -> R.string.composer_quality_audio_compact_desc
        MediaQualityMode.Balanced -> R.string.composer_quality_audio_balanced_desc
        MediaQualityMode.Original -> R.string.composer_quality_audio_original_desc
    }
}

/** Short second line under each mode name, so all three differences are visible at once. */
@StringRes
internal fun MediaQualityMode.shortDescriptionRes(kind: MediaQualityKind): Int = when (kind) {
    MediaQualityKind.Video -> when (this) {
        MediaQualityMode.Compact -> R.string.composer_quality_short_480p
        MediaQualityMode.Balanced -> R.string.composer_quality_short_720p
        MediaQualityMode.Original -> R.string.composer_quality_short_no_compression
    }
    MediaQualityKind.Audio -> when (this) {
        MediaQualityMode.Compact -> R.string.composer_quality_short_compact_audio
        MediaQualityMode.Balanced -> R.string.composer_quality_short_balanced_audio
        MediaQualityMode.Original -> R.string.composer_quality_short_no_compression
    }
}

@StringRes
internal fun MediaPreparationHint.messageRes(): Int = when (this) {
    MediaPreparationHint.AlreadyEfficient -> R.string.composer_quality_hint_efficient
    MediaPreparationHint.UnknownMetadata -> R.string.composer_quality_hint_unknown
    MediaPreparationHint.NotWorthIt -> R.string.composer_quality_hint_not_worth
    MediaPreparationHint.MetadataPreserved -> R.string.audio_metadata_kept
    MediaPreparationHint.TooLongAsFile -> R.string.video_too_long_as_file
    MediaPreparationHint.TooLargeAsFile -> R.string.video_too_large_as_file
}

/**
 * Quality controls live inside one attachment card, next to the file they affect. The row shows
 * both Telegram delivery semantics and the three re-encode presets with the selected state.
 */
@Composable
internal fun MediaQualityPanel(
    item: OutgoingMedia,
    preparation: MediaPreparation,
    enabled: Boolean,
    onModeSelected: (MediaQualityMode) -> Unit,
    onDeliverySelected: (MediaDeliveryMode) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = item.qualityKind() ?: return
    val palette = beyGramPalette()
    val label = if (item.fileName.isBlank()) stringResource(R.string.composer_quality_title) else item.fileName
    val source = mediaSourceText(item)
    val hint = preparation.hint
    val error = preparation.error
    val prepared = preparation.prepared
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            stringResource(R.string.composer_quality_title),
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (source.isNotEmpty()) {
            Text(source, color = palette.textSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (kind == MediaQualityKind.Video) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.composer_quality_delivery), color = palette.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QualityChoice(
                    label = stringResource(R.string.composer_quality_delivery_media),
                    selected = preparation.delivery == MediaDeliveryMode.Media,
                    enabled = enabled,
                    onClick = { onDeliverySelected(MediaDeliveryMode.Media) },
                    modifier = Modifier.weight(1f)
                )
                QualityChoice(
                    label = stringResource(R.string.composer_quality_delivery_file),
                    selected = preparation.delivery == MediaDeliveryMode.File,
                    enabled = enabled,
                    onClick = { onDeliverySelected(MediaDeliveryMode.File) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (kind == MediaQualityKind.Video && preparation.delivery == MediaDeliveryMode.File) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.composer_quality_delivery_file_hint), color = palette.textSecondary, fontSize = 12.sp)
            hint?.let { message ->
                Text(stringResource(message.messageRes()), color = palette.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            sourceSizeText(item)?.let { size ->
                Text(stringResource(R.string.composer_quality_original_bytes, size), color = palette.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaQualityMode.entries.forEach { mode ->
                    QualityChoice(
                        label = stringResource(mode.labelRes()),
                        subtitle = stringResource(mode.shortDescriptionRes(kind)),
                        selected = preparation.mode == mode,
                        enabled = enabled,
                        onClick = { onModeSelected(mode) },
                        modifier = Modifier.weight(1f),
                        semanticsLabel = "$label, ${stringResource(mode.labelRes())}, ${stringResource(mode.shortDescriptionRes(kind))}"
                    )
                }
            }
            Text(
                stringResource(preparation.mode.descriptionRes(kind)),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (kind == MediaQualityKind.Video && preparation.mode != MediaQualityMode.Original) {
                Text(stringResource(R.string.composer_quality_delivery_media_hint), color = palette.textSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            when {
                preparation.processing -> {
                    ProcessingRow(preparation, kind, enabled, onCancel)
                    val estimate = qualityEstimateBytes(item, preparation.mode)
                    if (estimate != null) {
                        val size = formatMediaSize(estimate)
                        if (size.isNotEmpty()) {
                            Text(
                                stringResource(R.string.composer_quality_estimate, size),
                                color = palette.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                error != null -> {
                    val genericError = stringResource(R.string.composer_quality_error)
                    Text(
                        genericError,
                        color = palette.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.errorAnnouncement()
                    )
                    if (error != genericError) {
                        Text(error, color = palette.textSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    sourceSizeText(item)?.let { size ->
                        Text(stringResource(R.string.composer_quality_original_bytes, size), color = palette.textSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = onRetry, enabled = enabled, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.composer_quality_retry), color = palette.accent)
                    }
                }
                hint != null -> {
                    Text(
                        stringResource(hint.messageRes()),
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    sourceSizeText(item)?.let { size ->
                        Text(stringResource(R.string.composer_quality_original_bytes, size), color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
                preparation.mode == MediaQualityMode.Original -> sourceSizeText(item)?.let { size ->
                    Text(stringResource(R.string.composer_quality_original_bytes, size), color = palette.textSecondary, fontSize = 12.sp)
                }
                prepared != null -> {
                    val size = formatMediaSize(prepared.size)
                    if (size.isNotEmpty()) {
                        Text(stringResource(R.string.composer_quality_actual, size), color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                else -> {
                    val estimate = qualityEstimateBytes(item, preparation.mode)
                    Text(
                        if (estimate != null) {
                            val size = formatMediaSize(estimate)
                            if (size.isNotEmpty()) stringResource(R.string.composer_quality_estimate, size)
                            else stringResource(R.string.composer_quality_estimate_unknown)
                        } else stringResource(R.string.composer_quality_estimate_unknown),
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingRow(
    preparation: MediaPreparation,
    kind: MediaQualityKind,
    enabled: Boolean,
    onCancel: () -> Unit
) {
    val palette = beyGramPalette()
    val fraction = preparation.progress
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (fraction != null) stringResource(
                    if (kind == MediaQualityKind.Audio) R.string.audio_compressing else R.string.video_compressing,
                    (fraction * 100).toInt().coerceIn(0, 100)
                ) else stringResource(
                    if (kind == MediaQualityKind.Audio) R.string.audio_compressing_indeterminate else R.string.video_compressing_indeterminate
                ),
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accent,
                    trackColor = palette.surface2
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.accent, trackColor = palette.surface2)
            }
        }
        IconButton(onClick = onCancel, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Rounded.Close,
                stringResource(if (kind == MediaQualityKind.Audio) R.string.audio_compression_cancel else R.string.video_compression_cancel),
                tint = palette.textSecondary
            )
        }
    }
}

@Composable
private fun QualityChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    semanticsLabel: String? = null
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(12.dp)
    val tint = if (enabled) palette.textPrimary else palette.textPrimary.copy(alpha = 0.38f)
    Column(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface2)
            .then(if (selected) Modifier.border(1.dp, palette.accent, shape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics {
                if (semanticsLabel != null) contentDescription = semanticsLabel
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (selected) palette.accentInk else tint,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = if (selected) palette.accentInk.copy(alpha = 0.8f) else palette.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun mediaSourceText(item: OutgoingMedia): String {
    val parts = mutableListOf<String>()
    if (item.width > 0 && item.height > 0) parts += stringResource(R.string.composer_quality_resolution, item.width, item.height)
    if (item.durationSeconds > 0) parts += duration(item.durationSeconds)
    val bitrate = item.estimatedBitrate()
    if (bitrate > 0) parts += bitrateText(bitrate)
    val sourceSize = formatMediaSize(item.size)
    if (sourceSize.isNotEmpty()) parts += sourceSize
    if (parts.isEmpty()) return ""
    return stringResource(R.string.composer_quality_source, parts.joinToString(" · "))
}

@Composable
private fun sourceSizeText(item: OutgoingMedia): String? = formatMediaSize(item.size).takeIf { it.isNotEmpty() }

@Composable
private fun bitrateText(bitsPerSecond: Int): String = if (bitsPerSecond >= 1_000_000) {
    stringResource(
        R.string.composer_quality_bitrate_mbps,
        String.format(Locale.getDefault(), "%.1f", bitsPerSecond / 1_000_000f)
    )
} else {
    stringResource(R.string.composer_quality_bitrate_kbps, (bitsPerSecond / 1000).coerceAtLeast(1))
}
