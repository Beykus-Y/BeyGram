package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.BotCommandMenu
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramFilledTextFieldColors

/** Composer entry point; only mounted when TDLib reported at least one command for the chat's bot. */
@Composable
internal fun BotCommandButton(enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val label = stringResource(R.string.chat_bot_commands_open)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label }
    ) {
        Icon(Icons.Rounded.Code, contentDescription = null, tint = if (enabled) palette.accent else palette.disabled, modifier = Modifier.size(22.dp))
    }
}

/** Searchable command list. Inserting a command only changes the draft; nothing is sent without a second tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BotCommandSheet(chatTitle: String, menu: BotCommandMenu, onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    val palette = beyGramPalette()
    var query by remember(menu) { mutableStateOf("") }
    val filtered = remember(menu, query) {
        val needle = query.trim()
        if (needle.isEmpty()) menu.commands
        else menu.commands.filter { it.command.contains(needle, ignoreCase = true) || it.description.contains(needle, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.chat_bot_commands_title), color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(chatTitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.chat_bot_commands_search), color = palette.textSecondary) },
                colors = beyGramFilledTextFieldColors()
            )
            Spacer(Modifier.height(6.dp))
            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.chat_bot_commands_empty),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered, key = { it.command }) { command ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.chat_bot_commands_insert, command.command)) { onInsert(command.command) }
                                .defaultMinSize(minHeight = 52.dp)
                                .padding(vertical = 9.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                "/${command.command}",
                                color = palette.accentInk,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            if (command.description.isNotBlank()) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    command.description,
                                    color = palette.textSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider(color = palette.divider.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
