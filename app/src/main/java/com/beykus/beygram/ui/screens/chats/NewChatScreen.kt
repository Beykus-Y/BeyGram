package com.beykus.beygram.ui.screens.chats

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.beykus.beygram.domain.model.SecretChatEligibility
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.StyledListDivider
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.contacts.CONTACT_SYMBOL_GROUP
import com.beykus.beygram.ui.screens.contacts.contactAlphabetGroup
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where a divider between two entries starts: the row padding, the 40 dp icon tile and its 12 dp gap. */
private val NewChatRowDividerInset = 68.dp

/**
 * The one screen for starting a conversation. Creation entries, the contact search and the public lookup are separate
 * scenarios: the contact search is the single search for people and stays at the top, its results replace the entries,
 * and the public lookup opens its own field instead of sharing the text of the contact search.
 */
@Composable
internal fun NewChatScreen(
    loadContacts: suspend () -> Result<List<ContactPreview>>,
    openPrivateChat: suspend (Long) -> Result<Long>,
    createSecretChat: suspend (Long) -> Result<Long>,
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
    val design = LocalDesignSystem.current
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
    var publicOpen by rememberSaveable { mutableStateOf(false) }
    var invitePreview by remember { mutableStateOf<ChatInvitePreview?>(null) }
    var inviteUrl by remember { mutableStateOf("") }
    var inviteJoining by remember { mutableStateOf(false) }
    // Picking a contact while this mode is on creates an E2E secret chat instead of opening a cloud conversation.
    var secretMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        loadContacts().fold(
            onSuccess = { contacts = it },
            onFailure = { error = it.userMessage(context, R.string.contacts_load_failed) }
        )
        loading = false
    }

    val searching = query.isNotBlank()
    val visible = remember(contacts, query) { filterNewChatContacts(contacts, query) }
    // Letter sections as in the Contacts tab: sorted by name, names that start with a symbol last under #.
    val contactGroups = remember(visible) {
        val grouped = visible.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .groupBy { contactAlphabetGroup(it.title) }
        grouped.filterKeys { it != CONTACT_SYMBOL_GROUP } + grouped.filterKeys { it == CONTACT_SYMBOL_GROUP }
    }
    // A contact search without a match may still be a public @username or link: offered as one explicit action.
    val publicMatch = remember(query) { query.trim().takeIf { publicDiscoveryTarget(it) != null } }

    fun discoverPublicChat(text: String) {
        if (discoveryBusy) return
        val target = publicDiscoveryTarget(text)
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

    fun openContact(contact: ContactPreview) {
        if (openingUser != null) return
        if (secretMode && !SecretChatEligibility.canStart(contact.userId, isSelf = false, isBot = contact.isBot)) {
            openError = context.getString(R.string.secret_chat_bot_unavailable)
            return
        }
        // Set before the coroutine starts, so a second tap on another contact cannot open a second chat.
        openingUser = contact.userId
        openError = null
        scope.launch {
            try {
                val result = if (secretMode) createSecretChat(contact.userId) else openPrivateChat(contact.userId)
                result
                    .onSuccess { onChatOpened(it) }
                    .onFailure {
                        openError = it.userMessage(
                            context,
                            if (secretMode) R.string.secret_chat_create_failed else R.string.contacts_open_chat_failed
                        )
                    }
            } finally {
                openingUser = null
            }
        }
    }

    Column(
        Modifier.fillMaxSize().glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .imePadding()
    ) {
        ProfileToolbar(stringResource(R.string.new_chat_title), onBack) {
            IconButton(onClick = onAddContact) {
                Icon(Icons.Rounded.PersonAdd, stringResource(R.string.new_chat_add_contact), tint = palette.accent)
            }
        }
        Text(
            stringResource(R.string.new_chat_subtitle),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
        )
        // Fixed above the list, so the field keeps its focus and the typed text while the list scrolls or the keyboard opens.
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SearchField(
                query = query,
                onChange = { query = it; discoveryError = null },
                placeholder = stringResource(R.string.contacts_search_placeholder)
            )
        }
        // The entries and the contacts are one list inside one group card, as the search results are.
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
                .glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)) {
                if (!searching) {
                    item(key = "create") {
                        Column(Modifier.fillMaxWidth()) {
                            SearchSectionHeader(
                                title = stringResource(R.string.new_chat_create_section),
                                count = null,
                                loading = false
                            )
                            NewChatActionRow(
                                title = stringResource(R.string.new_chat_group),
                                subtitle = stringResource(R.string.new_chat_group_hint),
                                icon = Icons.Rounded.GroupAdd,
                                onClick = onNewGroup
                            )
                            StyledListDivider(startIndent = NewChatRowDividerInset)
                            NewChatActionRow(
                                title = stringResource(R.string.feature_create_channel),
                                subtitle = stringResource(R.string.new_chat_channel_hint),
                                icon = Icons.Rounded.Campaign,
                                onClick = onNewChannel
                            )
                            StyledListDivider(startIndent = NewChatRowDividerInset)
                            NewChatActionRow(
                                title = stringResource(R.string.secret_chat_create_action),
                                subtitle = stringResource(R.string.secret_chat_create_hint),
                                icon = Icons.Rounded.Lock,
                                checked = secretMode,
                                onClick = { secretMode = !secretMode; openError = null }
                            )
                        }
                    }
                }
                if (secretMode) {
                    item(key = "secret_banner") {
                        SecretChatBanner(onCancel = { secretMode = false; openError = null })
                    }
                }
                if (!searching) {
                    item(key = "public_entry") {
                        NewChatActionRow(
                            title = stringResource(R.string.new_chat_public_search),
                            subtitle = stringResource(R.string.new_chat_public_hint),
                            icon = Icons.Rounded.Language,
                            onClick = { publicOpen = !publicOpen },
                            trailing = if (publicOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown
                        )
                    }
                    if (publicOpen) {
                        item(key = "public_panel") {
                            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                                Text(
                                    stringResource(R.string.new_chat_public_explain),
                                    color = palette.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                OutlinedTextField(
                                    value = discoveryQuery,
                                    onValueChange = { discoveryQuery = it; discoveryError = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.new_chat_public_hint)) },
                                    singleLine = true,
                                    shape = styledShape(14.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { discoverPublicChat(discoveryQuery) })
                                )
                                Button(
                                    onClick = { discoverPublicChat(discoveryQuery) },
                                    enabled = !discoveryBusy && discoveryQuery.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)
                                ) {
                                    if (discoveryBusy) {
                                        CircularProgressIndicator(Modifier.size(18.dp), color = palette.onAccent, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(stringResource(R.string.chats_search_network_action))
                                }
                                PublicLookupStatus(busy = discoveryBusy, error = discoveryError)
                            }
                        }
                    }
                }
                item(key = "contacts_heading") {
                    SearchSectionHeader(
                        title = stringResource(R.string.new_chat_contacts),
                        count = if (contacts.isEmpty()) null else visible.size,
                        loading = loading && contacts.isNotEmpty()
                    )
                }
                openError?.let { message ->
                    item(key = "open_error") {
                        Text(
                            message,
                            color = palette.danger,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).errorAnnouncement()
                        )
                    }
                }
                when {
                    loading && contacts.isEmpty() -> item(key = "contacts_loading") {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = palette.accent)
                        }
                    }
                    error != null && contacts.isEmpty() -> item(key = "contacts_error") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                error.orEmpty(),
                                color = palette.danger,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.errorAnnouncement()
                            )
                            TextButton(onClick = { refreshToken++ }) { Text(stringResource(R.string.common_retry)) }
                        }
                    }
                    // A search with no contacts at all falls through to the no-results state, which still offers the public lookup.
                    contacts.isEmpty() && !searching -> item(key = "contacts_none") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.contacts_empty_none),
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                stringResource(R.string.new_chat_contacts_empty_hint),
                                color = palette.textSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Button(
                                onClick = onAddContact,
                                modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.new_chat_add_contact))
                            }
                        }
                    }
                    visible.isEmpty() -> item(key = "contacts_no_results") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.contacts_empty_no_results),
                                color = palette.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            publicMatch?.let { target ->
                                Button(
                                    onClick = {
                                        discoveryQuery = target
                                        discoverPublicChat(target)
                                    },
                                    enabled = !discoveryBusy,
                                    modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.new_chat_public_find_for, target),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            PublicLookupStatus(busy = discoveryBusy, error = discoveryError)
                        }
                    }
                    else -> contactGroups.forEach { (initial, group) ->
                        item(key = "letter_$initial") {
                            Text(
                                initial,
                                color = palette.accentInk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
                                    .semantics { heading() }
                            )
                        }
                        items(group, key = { "contact_${it.userId}" }) { contact ->
                            NewChatContactRow(
                                contact = contact,
                                observeFile = observeFile,
                                downloadFile = downloadFile,
                                enabled = openingUser == null,
                                selected = null,
                                busy = openingUser == contact.userId,
                                onClick = { openContact(contact) }
                            )
                        }
                    }
                }
            }
        }
    }
    invitePreview?.let { preview ->
        BeyGramDialog(
            onDismissRequest = { if (!inviteJoining) invitePreview = null },
            title = preview.title.ifBlank { stringResource(R.string.new_chat_public_invite_title) },
            content = {
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
            confirmText = stringResource(if (preview.createsJoinRequest) R.string.new_chat_public_request else R.string.new_chat_public_join),
            confirmEnabled = !inviteJoining,
            onConfirm = {
                if (inviteJoining) return@BeyGramDialog
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
            },
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !inviteJoining,
            onDismiss = { invitePreview = null }
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
    val design = LocalDesignSystem.current
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
        Modifier.fillMaxSize().glassScreenBackground(design, palette.screen)
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

/**
 * An entry of the create block. With [checked] null it leaves for another screen; with a value it is a mode that stays
 * here and shows whether it is on. [trailing] is the arrow of a navigation row, or the arrow of a row that opens a panel.
 */
@Composable
private fun NewChatActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    checked: Boolean? = null,
    trailing: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward
) {
    val palette = beyGramPalette()
    val tap = if (checked == null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(tap).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(styledShape(12.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = palette.textSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            checked == null -> Icon(trailing, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
            checked == true -> Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
            else -> Spacer(Modifier.size(22.dp))
        }
    }
}

/** The mode banner under the entries: it says what the next contact tap does and how to leave the mode. */
@Composable
private fun SecretChatBanner(onCancel: () -> Unit) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(palette.accentSoft, styledShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            stringResource(R.string.secret_chat_pick_contact),
            color = palette.accentInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.secret_chat_device_notice),
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.secret_chat_pick_cancel), color = palette.accent)
        }
    }
}

/** Busy and failure of the public lookup, shown where the lookup was started. Errors are announced to TalkBack. */
@Composable
private fun PublicLookupStatus(busy: Boolean, error: String?) {
    val palette = beyGramPalette()
    if (busy) {
        Text(
            stringResource(R.string.new_chat_public_searching),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    error?.let {
        Text(
            it,
            color = palette.danger,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp).errorAnnouncement()
        )
    }
}

/**
 * One contact in a picker. With [selected] null a tap opens the contact; with a value the row is a member toggle and shows
 * its state. [busy] marks the contact whose chat is being opened, so the wait is visible on that row.
 */
@Composable
private fun NewChatContactRow(
    contact: ContactPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    enabled: Boolean,
    selected: Boolean?,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val tap = if (selected == null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(tap).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(contact.initials, Color(contact.avatarColorArgb), 40.dp, contact.photo, observeFile, downloadFile)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.title.ifBlank { stringResource(R.string.user_name_unknown) },
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // The second line exists only when the contact has one, so a bare contact stays a single dense line.
            if (contact.subtitle.isNotBlank()) {
                Text(
                    contact.subtitle,
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (busy) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
        } else if (selected == true) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, stringResource(R.string.new_chat_contact_selected), tint = palette.accent)
        }
    }
}
