package com.beykus.beygram.ui.screens.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.BeyGramDialog

/** Explicitly confirm discarding an unapplied theme draft, for both theme screens and system Back. */
@Composable
internal fun ThemeDiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    BeyGramDialog(
        onDismissRequest = onKeepEditing,
        title = stringResource(R.string.theme_discard_title),
        message = stringResource(R.string.theme_discard_message),
        confirmText = stringResource(R.string.theme_discard_confirm),
        onConfirm = onDiscard,
        destructive = true,
        dismissText = stringResource(R.string.theme_discard_cancel),
        onDismiss = onKeepEditing
    )
}
