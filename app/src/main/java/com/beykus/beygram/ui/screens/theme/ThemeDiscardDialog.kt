package com.beykus.beygram.ui.screens.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette

/** Explicitly confirm discarding an unapplied theme draft, for both theme screens and system Back. */
@Composable
internal fun ThemeDiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.theme_discard_title)) },
        text = { Text(stringResource(R.string.theme_discard_message)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.theme_discard_confirm), color = palette.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(R.string.theme_discard_cancel))
            }
        }
    )
}
