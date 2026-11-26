package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material3.*
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/** Profile actions: no destructive RPC is fired until a confirmation has been accepted. */
@Composable
internal fun ChatInfoMenu(
    canBlock: Boolean,
    canReport: Boolean,
    isBlocked: Boolean,
    canDelete: Boolean,
    onSetBlocked: suspend (Boolean) -> Result<Unit>,
    onReportSpam: suspend () -> Result<Unit>,
    onDelete: suspend () -> Result<Unit>,
    onDeleted: () -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.feature_more_actions), tint = palette.textPrimary)
        }
        BeyGramPopover(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canBlock) BeyGramPopoverItem(
                text = stringResource(if (isBlocked) R.string.feature_unblock_user else R.string.feature_block_user),
                leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, tint = palette.textSecondary) },
                onClick = { expanded = false; confirmation = if (isBlocked) "unblock" else "block" })
            if (canReport) BeyGramPopoverItem(
                text = stringResource(R.string.feature_report_spam),
                leadingIcon = { Icon(Icons.Rounded.Report, contentDescription = null, tint = palette.danger) },
                danger = true,
                onClick = { expanded = false; confirmation = "report" })
            if (canDelete) BeyGramPopoverItem(
                text = stringResource(R.string.feature_delete_chat),
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = palette.danger) },
                danger = true,
                onClick = { expanded = false; confirmation = "delete" })
        }
    }
    confirmation?.let { kind ->
        BeyGramDialog(
            onDismissRequest = { if (!busy) confirmation = null },
            title = stringResource(
                when (kind) {
                    "block" -> R.string.feature_block_user
                    "unblock" -> R.string.feature_unblock_user
                    "report" -> R.string.feature_report_spam
                    else -> R.string.feature_delete_chat
                }
            ),
            message = stringResource(
                when (kind) {
                    "block" -> R.string.feature_block_warning
                    "unblock" -> R.string.feature_unblock_warning
                    "report" -> R.string.feature_report_spam_warning
                    else -> R.string.feature_delete_chat_info_warning
                }
            ),
            confirmText = stringResource(R.string.feature_confirm),
            confirmEnabled = !busy,
            destructive = kind == "block" || kind == "delete",
            onConfirm = {
                scope.launch {
                    busy = true; error = null
                    val result = when (kind) {
                        "block" -> onSetBlocked(true)
                        "unblock" -> onSetBlocked(false)
                        "report" -> onReportSpam()
                        else -> onDelete()
                    }
                    busy = false
                    result.fold(
                        { confirmation = null; if (kind == "delete") onDeleted() },
                        { error = it.userMessage(context, R.string.feature_action_failed) }
                    )
                }
            },
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !busy,
            onDismiss = { confirmation = null }
        )
    }
    if (error != null && confirmation != null) BeyGramDialog(
        onDismissRequest = { error = null },
        title = stringResource(R.string.feature_action_failed),
        message = error.orEmpty(),
        confirmText = stringResource(R.string.common_close),
        onConfirm = { error = null }
    )
}
