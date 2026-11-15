package com.beykus.beygram.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * BeyGram's own centred dialog for short confirmations and small inputs. Built on the low-level Compose [Dialog]
 * and drawn with [beyGramPalette] colours, so it follows the active theme instead of Material's AlertDialog.
 *
 * - [title], [message] and [content] are slots: callers pass strings from string resources.
 * - [confirm] is the primary action; [dismiss] is optional. Set [destructive] to draw the confirm action in the
 *   danger colour (never make a destructive action the default).
 * - Long content scrolls, so the actions stay reachable with a large system font.
 */
@Composable
fun BeyGramDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable () -> Unit)? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissOnClickOutside: Boolean = true
) {
    val palette = beyGramPalette()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 24.dp)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = palette.surface,
            contentColor = palette.textPrimary,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
                if (content != null) {
                    Spacer(Modifier.height(16.dp))
                    content()
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissText != null) {
                        TextButton(onClick = onDismiss ?: onDismissRequest) {
                            Text(dismissText, color = palette.textSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                        Text(
                            text = confirmText,
                            color = when {
                                !confirmEnabled -> palette.disabled
                                destructive -> palette.danger
                                else -> palette.accent
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
