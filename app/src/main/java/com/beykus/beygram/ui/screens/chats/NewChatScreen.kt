package com.beykus.beygram.ui.screens.chats

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatInvitePreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.GroupCreationState
import com.beykus.beygram.domain.model.InviteJoinOutcome
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A real contact picker, not another shortcut back to the old channel form. */
@Composable
internal fun NewChatScreen(
    loadContacts: suspend () -> Result<List<ContactPreview>>,
    openPrivateChat: suspend (Long) -> Result<Long>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onAddContact: () -> Unit,
    resolvePublicChat: suspend (String) -> Result<Long>,
    checkInviteLink: suspend (String) -> Result<ChatInvitePreview>,
    joinInviteLink: suspend (String) -> Result<InviteJoinOutcome>,
    onChatOpened: (Long) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf(emptyList<ContactPreview>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var openingUser by remember { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf(0) }
    var discoveryQuery by rememberSaveable { mutableStateOf("") }
    var discoveryBusy by remember { mutableStateOf(false) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var invitePreview by remember { mutableStateOf<ChatInvitePreview?>(null) }
    var inviteUrl by remember { mutableStateOf("") }
    var inviteJoining by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        loadContacts().fold(
            onSuccess = { contacts = it },
            onFailure = { error = it.userMessage(context, R.string.contacts_load_failed) }
        )
        loading = false
    }

    val visible = remember(contacts, query) { filterNewChatContacts(contacts, query) }

    fun discoverPublicChat() {
        if (discoveryBusy) return
        val target = publicDiscoveryTarget(discoveryQuery)
        if (target == null) {
            discoveryError = context.getString(R.string.new_chat_public_invalid)
            return
        }
        discoveryBusy = true
        discoveryError = null
        scope.launch {
            try {
                when (target) {
                    is PublicDiscoveryTarget.Username -> resolvePublicChat(target.username)
                        .onSuccess { onChatOpened(it) }
                        .onFailure {
                            discoveryError = it.userMessage(context, R.string.new_chat_public_lookup_failed)
                        }
                    is PublicDiscoveryTarget.Invite -> checkInviteLink(target.url)
                        .onSuccess { preview ->
                            inviteUrl = target.url
                            invitePreview = preview
                        }
                        .onFailure {
                            discoveryError = it.userMessage(context, R.string.new_chat_public_invite_failed)
                        }
                }
            } finally {
                discoveryBusy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .imePadding()
    ) {
        ProfileToolbar(stringResource(R.string.new_chat_title), onBack) {
            IconButton(onClick = onAddContact) {
                Icon(Icons.Rounded.Add, stringResource(R.string.new_chat_add_contact), tint = palette.accent)
            }
        }
        Text(
            stringResource(R.string.new_chat_subtitle),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
        )
        NewChatAction(
            title = stringResource(R.string.new_chat_group),
            subtitle = stringResource(R.string.new_chat_group_hint),
            icon = Icons.Rounded.GroupAdd,
            onClick = onNewGroup
        )
        NewChatAction(
            title = stringResource(R.string.feature_create_channel),
            subtitle = stringResource(R.string.new_chat_channel_hint),
            icon = Icons.Rounded.Campaign,
            onClick = onNewChannel
        )
        Text(
            stringResource(R.string.new_chat_public_title),
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = discoveryQuery,
                onValueChange = { discoveryQuery = it; discoveryError = null },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.new_chat_public_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = ::discoverPublicChat, enabled = !discoveryBusy && discoveryQuery.isNotBlank()) {
                if (discoveryBusy) CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Search, stringResource(R.string.new_chat_public_search), tint = palette.accent)
            }
        }
        discoveryError?.let {
            Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        HorizontalDivider(color = palette.divider, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
        Text(
            stringResource(R.string.new_chat_contacts),
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.contacts_search_placeholder))
        }
        if (loading && contacts.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        openError?.let {
            Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }

        when {
            loading && contacts.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            error != null && contacts.isEmpty() -> NewChatEmpty(
                text = error.orEmpty(), modifier = Modifier.weight(1f),
                onRetry = { refreshToken++ }
            )
            contacts.isEmpty() -> NewChatEmpty(
                text = stringResource(R.string.contacts_empty_none),
                modifier = Modifier.weight(1f),
                onRetry = onAddContact,
                retryLabel = stringResource(R.string.new_chat_add_contact)
            )
            visible.isEmpty() -> NewChatEmpty(
                text = stringResource(R.string.contacts_empty_no_results),
                modifier = Modifier.weight(1f)
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(visible, key = { it.userId }) { contact ->
                    NewChatContactRow(
                        contact, observeFile, downloadFile,
                        enabled = openingUser == null,
                        selected = false
                    ) {
                        if (openingUser == null) {
                            scope.launch {
                                openingUser = contact.userId
                                openError = null
                                try {
                                    openPrivateChat(contact.userId)
                                        .onSuccess { onChatOpened(it) }
                                        .onFailure { openError = it.userMessage(context, R.string.contacts_open_chat_failed) }
                                } finally {
                                    openingUser = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    invitePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!inviteJoining) invitePreview = null },
            title = { Text(preview.title.ifBlank { stringResource(R.string.new_chat_public_invite_title) }) },
            text = {
                Column {
                    Text(stringResource(R.string.new_chat_public_members, preview.memberCount))
                    Text(
                        stringResource(
                            if (preview.createsJoinRequest) R.string.new_chat_public_request_hint
                            else R.string.new_chat_public_join_hint
                        ),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        stringResource(R.string.new_chat_public_privacy_note),
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !inviteJoining,
                    onClick = {
                        if (inviteJoining) return@TextButton
                        inviteJoining = true
                        scope.launch {
                            try {
                                joinInviteLink(inviteUrl)
                                    .onSuccess { outcome ->
                                        invitePreview = null
                                        when (outcome) {
                                            is InviteJoinOutcome.Joined -> {
                                                Toast.makeText(context, context.getString(R.string.new_chat_public_joined), Toast.LENGTH_SHORT).show()
                                                onChatOpened(outcome.chatId)
                                            }
                                            InviteJoinOutcome.RequestSent -> {
                                                Toast.makeText(context, context.getString(R.string.new_chat_public_request_sent), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .onFailure {
                                        discoveryError = it.userMessage(context, R.string.new_chat_public_join_failed)
                                        invitePreview = null
                                    }
                            } finally {
                                inviteJoining = false
                            }
                        }
                    }
                ) { Text(stringResource(if (preview.createsJoinRequest) R.string.new_chat_public_request else R.string.new_chat_public_join)) }
            },
            dismissButton = {
                TextButton(onClick = { invitePreview = null }, enabled = !inviteJoining) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** Group creation uses the actual TDLib group API, with a chosen title and contacts. */
@Composable
internal fun CreateGroupScreen(
    loadContacts: suspend () -> Result<List<ContactPreview>>,
    creationState: GroupCreationState,
    startGroupCreation: (String, List<Long>) -> Result<Unit>,
    acknowledgeGroupCreation: () -> Unit,
    acknowledgeUncertainGroupCreation: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit,
    onCreated: (Long, Int) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    // An ArrayList of primitive IDs is Android-saveable; contact details are loaded from TDLib.
    var selectedIds by rememberSaveable { mutableStateOf(arrayListOf<Long>()) }
    var contacts by remember { mutableStateOf(emptyList<ContactPreview>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var creationError by remember { mutableStateOf<String?>(null) }
    val creating = creationState is GroupCreationState.Pending
    var refreshToken by remember { mutableStateOf(0) }
    // A successful TDLib response may arrive while another activity instance owns
    // this composable; shared repository state restores the final result.
    LaunchedEffect(creationState) {
        if (creationState is GroupCreationState.Created) {
            acknowledgeGroupCreation()
            onCreated(creationState.chatId, creationState.failedToAddMembers)
        }
    }

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        loadContacts().fold(
            onSuccess = { contacts = it },
            onFailure = { error = it.userMessage(context, R.string.contacts_load_failed) }
        )
        loading = false
    }

    val visible = remember(contacts, query) { filterNewChatContacts(contacts, query) }
    val memberIds = remember(selectedIds, contacts) { selectedNewGroupMembers(selectedIds, contacts).toList() }
    val canCreate = canSubmitNewGroup(title, memberIds, creating) && !loading

    val hasCreationFeedback = creationState is GroupCreationState.NeedsVerification ||
        creationState == GroupCreationState.Failed || creationError != null

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .imePadding()
    ) {
        ProfileToolbar(stringResource(R.string.new_chat_group), onBack)
        // Only the form and contacts scroll. The primary action must remain visible
        // independently of the length of the Telegram contact list.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            item(key = "group_name") {
                OutlinedTextField(
                    title, onValueChange = { title = it.take(128) },
                    label = { Text(stringResource(R.string.new_chat_group_name)) },
                    enabled = !creating,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                        .testTag("group_title_input")
                )
            }
            item(key = "selected_count") {
                Text(
                    stringResource(R.string.new_chat_selected_count, memberIds.size),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            item(key = "search") {
                SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.contacts_search_placeholder))
            }
            if (loading) {
                item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent) }
            }
            if (error != null && contacts.isEmpty()) {
                item(key = "load_error") {
                    Text(error.orEmpty(), color = palette.danger)
                    TextButton(onClick = { refreshToken++ }) { Text(stringResource(R.string.common_retry)) }
                }
            } else if (!loading && visible.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (contacts.isEmpty()) R.string.contacts_empty_none else R.string.contacts_empty_no_results),
                        color = palette.textSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(visible, key = { "member_${it.userId}" }) { contact ->
                NewChatContactRow(
                    contact, observeFile, downloadFile,
                    enabled = !creating,
                    selected = contact.userId in selectedIds
                ) {
                    selectedIds = ArrayList(toggledGroupMembers(selectedIds, contact.userId))
                }
            }
        }

        // The status area participates in the remaining-height weights with contacts:
        // it can never push the fixed bottom CTA off screen. On compact displays it
        // scrolls independently (including the "checked" verification action).
        if (hasCreationFeedback) {
            Column(
                Modifier.weight(0.5f, fill = false)
                    .fillMaxWidth().background(palette.screen)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (creationState is GroupCreationState.NeedsVerification) {
                    Text(
                        stringResource(R.string.new_chat_group_unchanged_unknown),
                        color = palette.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    TextButton(onClick = acknowledgeUncertainGroupCreation) {
                        Text(stringResource(R.string.new_chat_group_checked))
                    }
                } else if (creationState == GroupCreationState.Failed) {
                    Text(
                        stringResource(R.string.new_chat_group_failed),
                        color = palette.danger,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                creationError?.let { text ->
                    Text(text, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
        // Insets are consumed by the enclosing screen. Keep only the submit
        // button at a fixed height to reserve space for the contact list.
        HorizontalDivider(color = palette.divider)
        Column(
            Modifier.fillMaxWidth().background(palette.screen)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Button(
                enabled = canCreate && creationState !is GroupCreationState.NeedsVerification &&
                    creationState !is GroupCreationState.Created,
                onClick = {
                    val participants = selectedNewGroupMembers(selectedIds, contacts).toList()
                    if (!canSubmitNewGroup(title, participants, creating)) return@Button
                    creationError = null
                    startGroupCreation(title.trim(), participants)
                        .onFailure { creationError = it.userMessage(context, R.string.new_chat_group_failed) }
                },
                modifier = Modifier.fillMaxWidth().testTag("group_create_submit")
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = palette.onAccent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(if (creating) R.string.common_saving else R.string.new_chat_group_create))
            }
        }
    }
}

@Composable
private fun NewChatAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(palette.surface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.accentInk)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.textSecondary, fontSize = 12.sp, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = palette.textSecondary)
    }
}

@Composable
private fun NewChatContactRow(
    contact: ContactPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(contact.initials, Color(contact.avatarColorArgb), 48.dp, contact.photo, observeFile, downloadFile)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.title.ifBlank { stringResource(R.string.user_name_unknown) },
                color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (contact.subtitle.isNotBlank()) {
                Text(contact.subtitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.new_chat_contact_selected), tint = palette.accent)
        }
    }
}

@Composable
private fun NewChatEmpty(
    text: String, modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null, retryLabel: String? = null
) {
    val palette = beyGramPalette()
    Column(modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = palette.textSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRetry) {
                Text(retryLabel ?: stringResource(R.string.common_retry))
            }
        }
    }
}
