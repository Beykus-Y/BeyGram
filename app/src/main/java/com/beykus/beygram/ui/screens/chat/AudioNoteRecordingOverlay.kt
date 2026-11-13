package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem

/**
 * The voice recording bar: one row of the composer's 48 dp height, with the field's radius. While locked, the trash
 * button sits on the left; the recording itself never changes the row's height.
 */
@Composable
internal fun AudioNoteRecordingDock(recorder: AudioNoteRecorder, modifier: Modifier = Modifier, onCancel: () -> Unit) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(LocalDesignSystem.current.shapes.composer)
    Row(modifier.height(ComposerButtonSize).clip(shape).background(palette.surface2).padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (recorder.locked) RecordingDiscardButton(onClick = onCancel)
        Spacer(Modifier.size(9.dp).clip(CircleShape).background(palette.danger))
        Icon(Icons.Rounded.Mic, null, tint = palette.danger, modifier = Modifier.size(19.dp))
        Text(duration((recorder.elapsedMs / 1000).toInt()), color = palette.textPrimary, fontSize = 16.sp)
        Text(
            when {
                recorder.locked -> stringResource(R.string.recorder_locked)
                recorder.canceling -> stringResource(R.string.recorder_release_to_cancel)
                else -> stringResource(R.string.voice_recorder_recording)
            },
            color = if (recorder.canceling) palette.warningText else palette.textSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (!recorder.locked) Text(stringResource(R.string.recorder_swipe_up_lock), color = palette.textSecondary, fontSize = 11.sp)
    }
}
