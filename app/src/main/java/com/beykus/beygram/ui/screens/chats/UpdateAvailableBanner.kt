package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.update.UpdateBannerState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem

/**
 * A quiet line above the chat list: one build, one tap to the Updates screen, one "Позже" that hides it for a day.
 *
 * It never downloads anything by itself, never covers the list, and disappears as soon as the build is installed or
 * the offer is withdrawn. It is part of the scrolling list rather than an overlay, so it cannot cover a chat row, the
 * composer, the keyboard or a fullscreen surface.
 */
@Composable
internal fun UpdateAvailableBanner(
    model: UpdateBannerState,
    onOpen: () -> Unit,
    onSnooze: () -> Unit,
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(LocalDesignSystem.current.mainScreen.archiveRadius)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Surface(color = palette.surface2, shape = shape, tonalElevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.updates_banner_title),
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // The build number is what the user compares with the screen; the notes are a separate preview.
                        text = if (model.stale) {
                            stringResource(R.string.updates_banner_text_unconfirmed, model.versionName)
                        } else {
                            stringResource(R.string.updates_banner_text, model.versionName)
                        },
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.updates_banner_open), color = palette.accentInk)
                }
                TextButton(onClick = onSnooze) {
                    Text(stringResource(R.string.updates_banner_later), color = palette.textSecondary)
                }
            }
        }
    }
}