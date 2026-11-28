package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.ChatStealthMode
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.components.GlassScreenScaffold

private enum class ExceptionFilter(val label: Int) {
    All(R.string.stealth_exceptions_all),
    Allowed(R.string.stealth_exceptions_allow),
    Blocked(R.string.stealth_exceptions_block),
    Private(R.string.stealth_exceptions_private),
    Group(R.string.folder_groups),
    Channel(R.string.folder_channels);

    fun matches(mode: ChatStealthMode, chat: ChatPreview?): Boolean = when (this) {
        All -> true
        Allowed -> mode == ChatStealthMode.Allow
        Blocked -> mode == ChatStealthMode.Block
        Private -> chat != null && !chat.isGroup && !chat.isChannel
        Group -> chat?.let { it.isGroup && !it.isChannel } == true
        Channel -> chat?.isChannel == true
    }
}

@Composable
internal fun StealthExceptionsScreen(store: StealthSettingsStore, chats: List<ChatPreview>, onBack: () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val settings by store.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ExceptionFilter.All) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    var saveFailed by remember(store) { mutableStateOf(false) }
    val knownChats = remember(chats) { chats.associateBy { it.id } }
    val matching = remember(settings.chats, knownChats, filter, query) {
        val term = query.trim()
        settings.chats.entries.asSequence()
            .filter { (id, mode) ->
                val chat = knownChats[id]
                filter.matches(mode, chat) &&
                    (term.isEmpty() || chat?.title?.contains(term, ignoreCase = true) == true || id.toString().contains(term))
            }
            .sortedWith(compareBy({ knownChats[it.key]?.title.orEmpty() }, { it.key }))
            .toList()
    }

    BackHandler(onBack = onBack)
    GlassScreenScaffold(
        modifier = Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing),
        toolbar = {
            ProfileToolbar(stringResource(R.string.stealth_exceptions_title), onBack)
        }
    ) { chrome ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top + 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(stringResource(R.string.stealth_exceptions_inheritance), color = palette.textSecondary)
            }
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.forward_search_hint)) }, singleLine = true)
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExceptionFilter.entries.forEach { choice ->
                        FilterChip(filter == choice, { filter = choice }, label = { Text(stringResource(choice.label)) })
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.stealth_exceptions_results, matching.size, settings.chats.size), color = palette.textSecondary)
                    if (saveFailed) Text(stringResource(R.string.stealth_save_failed), color = palette.danger)
                }
            }
            items(matching, key = { it.key }) { (id, mode) ->
                Column(
                    Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
                        .heightIn(min = 48.dp).clickable(role = Role.Button) { editingId = id }.padding(12.dp)
                ) {
                    Text(knownChats[id]?.title ?: stringResource(R.string.stealth_exceptions_unknown_chat, id), color = palette.textPrimary)
                    Text(stringResource(mode.labelRes()), color = palette.textSecondary)
                }
            }
            if (matching.isEmpty()) item {
                Text(stringResource(R.string.stealth_exceptions_empty), color = palette.textSecondary)
            }
            if (settings.chats.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { resetting = true }) { Text(stringResource(R.string.stealth_exceptions_reset)) }
                }
            }
        }
    }

    editingId?.let { id ->
        BeyGramDialog(
            onDismissRequest = { editingId = null },
            title = knownChats[id]?.title ?: stringResource(R.string.stealth_exceptions_unknown_chat, id),
            content = {
                Column(Modifier.selectableGroup()) {
                    ChatStealthMode.entries.forEach { choice ->
                        val current = settings.chats[id] ?: ChatStealthMode.Inherit
                        ChoiceRow(stringResource(choice.labelRes()), selected = current == choice) {
                            saveFailed = !store.update {
                                it.copy(chats = if (choice == ChatStealthMode.Inherit) it.chats - id else it.chats + (id to choice))
                            }
                            editingId = null
                        }
                    }
                }
            },
            confirmText = stringResource(R.string.common_cancel),
            onConfirm = { editingId = null }
        )
    }
    if (resetting) {
        BeyGramDialog(
            onDismissRequest = { resetting = false },
            title = stringResource(R.string.stealth_exceptions_reset),
            message = stringResource(R.string.stealth_exceptions_inheritance),
            confirmText = stringResource(R.string.stealth_exceptions_reset),
            onConfirm = {
                saveFailed = !store.update { it.copy(chats = emptyMap()) }
                resetting = false
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { resetting = false },
            destructive = true
        )
    }
}

private fun ChatStealthMode.labelRes(): Int = when (this) {
    ChatStealthMode.Inherit -> R.string.stealth_chat_inherit
    ChatStealthMode.Allow -> R.string.stealth_chat_allow
    ChatStealthMode.Block -> R.string.stealth_chat_block
}
