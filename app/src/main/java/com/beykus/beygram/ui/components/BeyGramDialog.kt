package com.beykus.beygram.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * BeyGram's own centred dialog for short confirmations and small inputs. Built on the low-level Compose [Dialog]
 * and drawn with [beyGramPalette] colours, so it follows the active theme instead of Material's AlertDialog.
 *
 * - [title], [message] and [content] are slots: callers pass strings from string resources. Do not put a vertically
 *   scrolling container (LazyColumn and similar) in [content] unless [scrollableContent] is false; otherwise it sits
 *   inside the dialog's own scroll area.
 * - [onConfirm] is the primary action. [onDismiss] is the tap on the dismiss (cancel) button, if [dismissText] is set;
 *   [onDismissRequest] handles Back, outside taps and any other close.
 * - [dismissEnabled] turns the dismiss button off, for example while a request is still running.
 * - Set [destructive] to draw the confirm action in the danger colour. A destructive action is never the default.
 * - Only title, message and content scroll; the action row stays visible with a large system font.
 * - Set [scrollableContent] to false when [content] is a list that scrolls by itself, such as a LazyColumn with a
 *   bounded height. The dialog then leaves [content] unwrapped, so the two scroll areas do not nest.
 * - Width is capped at 560dp; height at 80% of the window, so tablets and landscape keep a sane size.
 * - Set [secure] for sensitive content such as a key or a code: the window gets FLAG_SECURE, so screenshots and screen
 *   recordings do not show it.
 * - Set [neutralText] and [onNeutral] for a third action, such as a save-as-copy choice beside the confirm and dismiss
 *   buttons. It sits at the start of the action row and is never the default.
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
    dismissEnabled: Boolean = true,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    secure: Boolean = false,
    scrollableContent: Boolean = true,
    neutralText: String? = null,
    onNeutral: (() -> Unit)? = null
) {
    if (com.beykus.beygram.LocalMainTabsCovered.current) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismissRequest() }
        return
    }
    val palette = beyGramPalette()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 560.dp)
                .semantics { paneTitle = title },
            shape = shape,
            color = palette.surface,
            contentColor = palette.textPrimary,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
                    .heightIn(max = maxHeight)
            ) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(if (scrollableContent) Modifier.verticalScroll(scrollState) else Modifier)
                ) {
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
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (neutralText != null && onNeutral != null) {
                        TextButton(onClick = onNeutral) {
                            Text(neutralText, color = palette.textSecondary)
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    if (dismissText != null) {
                        TextButton(onClick = onDismiss ?: onDismissRequest, enabled = dismissEnabled) {
                            Text(dismissText, color = if (dismissEnabled) palette.textSecondary else palette.disabled)
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
