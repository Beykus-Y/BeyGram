package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
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
    canDelete: Boolean,
    onBlock: suspend () -> Result<Unit>,
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canBlock) DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_block_user)) },
                onClick = { expanded = false; confirmation = "block" })
            if (canDelete) DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_delete_chat), color = palette.danger) },
                onClick = { expanded = false; confirmation = "delete" })
        }
    }
    confirmation?.let { kind ->
        AlertDialog(
            onDismissRequest = { if (!busy) confirmation = null },
            title = { Text(stringResource(if (kind == "block") R.string.feature_block_user else R.string.feature_delete_chat)) },
            text = {
                Text(stringResource(if (kind == "block") R.string.feature_block_warning else R.string.feature_delete_chat_info_warning))
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; error = null
                        val result = if (kind == "block") onBlock() else onDelete()
                        busy = false
                        result.fold(
                            { confirmation = null; if (kind == "delete") onDeleted() },
                            { error = it.userMessage(context, R.string.feature_action_failed) }
                        )
                    }
                }) { Text(stringResource(R.string.feature_confirm), color = palette.danger) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmation = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    if (error != null && confirmation != null) AlertDialog(
        onDismissRequest = { error = null }, title = { Text(stringResource(R.string.feature_action_failed)) },
        text = { Text(error.orEmpty()) }, confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.common_close)) } }
    )
}
