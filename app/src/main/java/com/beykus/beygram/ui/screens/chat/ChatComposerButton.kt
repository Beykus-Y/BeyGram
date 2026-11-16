package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Every round control in the composer row (attach, record or video note, send) shares one size, so the row keeps
 * the same height whatever it holds. The tap target stays 48 dp; the glyph is 24 dp.
 */
internal val ComposerButtonSize = 48.dp
internal val ComposerIconSize = 24.dp

/**
 * Discards a locked recording: a 48 dp trash button on the left of the recording bar, in the danger colour.
 * Replaces the old text "Отмена" that made the bar grow.
 */
@Composable
internal fun RecordingDiscardButton(onClick: () -> Unit) {
    val palette = beyGramPalette()
    val label = stringResource(R.string.recorder_discard)
    Box(
        Modifier.size(ComposerButtonSize).clip(CircleShape).background(palette.danger.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Delete, contentDescription = null, tint = palette.danger, modifier = Modifier.size(ComposerIconSize))
    }
}
