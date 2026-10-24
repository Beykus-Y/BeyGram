package com.beykus.beygram.ui.screens.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Контакты из адресной книги Telegram: список, поиск, добавление, изменение, удаление и отправка. */
@Composable
fun ContactsScreen(
    loadContacts: suspend () -> Result<List<ContactPreview>>,
    addContact: suspend (firstName: String, lastName: String, phone: String) -> Result<Unit>,
    updateContact: suspend (userId: Long, firstName: String, lastName: String, phone: String) -> Result<Unit>,
    deleteContact: suspend (userId: Long) -> Result<Unit>,
    sendContact: suspend (chatId: Long, userId: Long) -> Result<Unit>,
    chats: List<ChatPreview>,
    openPrivateChat: suspend (userId: Long) -> Result<Long>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onChatOpened: (chatId: Long) -> Unit,
    accountId: String = "legacy"
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state: ContactsScreenState = viewModel(key = "contacts:$accountId")
    val contacts = state.contacts
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var openingUserId by remember { mutableStateOf<Long?>(null) }
    var editingContact by remember { mutableStateOf<ContactPreview?>(null) }
    var deletingContact by remember { mutableStateOf<ContactPreview?>(null) }
    var sharingContact by remember { mutableStateOf<ContactPreview?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val visible = remember(contacts, query) {
        val needle = query.trim()
        if (needle.isEmpty()) contacts
        else contacts.filter { it.title.contains(needle, ignoreCase = true) || it.subtitle.contains(needle, ignoreCase = true) }
    }
    val shareChats = remember(chats) {
        chats.filter { it.canPost }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    fun reload() {
        scope.launch {
            loading = true
            loadError = null
            loadContacts()
                .onSuccess { state.contacts = it }
                .onFailure { loadError = it.userMessage(context, R.string.contacts_load_failed) }
            loading = false
        }
    }
    fun openChat(contact: ContactPreview) {
        if (openingUserId != null) return
        scope.launch {
            openingUserId = contact.userId
            openError = null
            openPrivateChat(contact.userId)
                .onSuccess { chatId -> onChatOpened(chatId) }
                .onFailure { openError = it.userMessage(context, R.string.contacts_open_chat_failed) }
            openingUserId = null
        }
    }
    fun copyNumber(contact: ContactPreview) {
        if (contact.phone.isBlank()) {
            Toast.makeText(context, R.string.contacts_number_hidden, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(contact.title, contact.phone))
        Toast.makeText(context, R.string.contacts_number_copied, Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(Unit) { reload() }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.contacts_title), color = palette.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Rounded.PersonAdd, stringResource(R.string.contacts_add_contact_description), tint = palette.accent) }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.contacts_search_placeholder))
        }
        if (loading && contacts.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        openError?.let { Text(it, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        when {
            loading && contacts.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            loadError != null && contacts.isEmpty() -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(loadError.orEmpty(), color = palette.danger, textAlign = TextAlign.Center)
                TextButton(onClick = { reload() }) { Text(stringResource(R.string.common_retry)) }
            }
            contacts.isEmpty() -> EmptyState(stringResource(R.string.contacts_empty_none), Modifier.weight(1f))
            visible.isEmpty() -> EmptyState(stringResource(R.string.contacts_empty_no_results), Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(visible, key = { it.userId }) { contact ->
                    ContactRow(
                        contact = contact,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        enabled = openingUserId == null,
                        onClick = { openChat(contact) },
                        onEdit = { editingContact = contact },
                        onDelete = { deletingContact = contact },
                        onShare = { sharingContact = contact },
                        onCopy = { copyNumber(contact) }
                    )
                }
            }
        }
    }
    if (showAddDialog) AddContactDialog(
        onDismiss = { showAddDialog = false },
        onSubmit = addContact,
        onAdded = { reload() }
    )
    editingContact?.let { contact ->
        EditContactDialog(
            contact = contact,
            onSubmit = updateContact,
            onDismiss = { editingContact = null },
            onSaved = { reload() }
        )
    }
    deletingContact?.let { contact ->
        DeleteContactDialog(
            contact = contact,
            onDelete = deleteContact,
            onDismiss = { deletingContact = null },
            onDeleted = {
                state.contacts = state.contacts.filterNot { it.userId == contact.userId }
                reload()
            }
        )
    }
    sharingContact?.let { contact ->
        ShareContactDialog(
            contact = contact,
            chats = shareChats,
            sendContact = sendContact,
            observeFile = observeFile,
            downloadFile = downloadFile,
            onDismiss = { sharingContact = null },
            onSent = { Toast.makeText(context, R.string.contacts_share_sent, Toast.LENGTH_SHORT).show() }
        )
    }
}

@Composable
private fun ContactRow(
    contact: ContactPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(contact.initials, Color(contact.avatarColorArgb), 48.dp, contact.photo, observeFile, downloadFile)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(contact.title.ifBlank { stringResource(R.string.user_name_unknown) }, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contact.subtitle, color = palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.feature_more_actions), tint = palette.textSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.contacts_action_edit)) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.contacts_action_share)) },
                    onClick = { menuOpen = false; onShare() }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (contact.phone.isBlank()) R.string.contacts_number_hidden else R.string.contacts_action_copy
                            )
                        )
                    },
                    enabled = contact.phone.isNotBlank(),
                    onClick = { menuOpen = false; onCopy() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.contacts_action_delete), color = palette.danger) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier) {
    val palette = beyGramPalette()
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = palette.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (firstName: String, lastName: String, phone: String) -> Result<Unit>,
    onAdded: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Личные данные нового контакта не уходят в saved state: после пересоздания экрана поля очищаются.
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSave = !saving && firstName.isNotBlank() && phone.count { it.isDigit() } >= 7
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.contacts_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(firstName, { firstName = it.take(64) }, label = { Text(stringResource(R.string.contacts_field_first_name)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(lastName, { lastName = it.take(64) }, label = { Text(stringResource(R.string.contacts_field_last_name)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(phone, { phone = it.take(20) }, label = { Text(stringResource(R.string.contacts_field_phone)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                error?.let { Text(it, color = palette.danger, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    onSubmit(firstName.trim(), lastName.trim(), phone.filter { it.isDigit() || it == '+' })
                        .onSuccess { onAdded(); onDismiss() }
                        .onFailure { error = it.userMessage(context, R.string.contacts_add_failed) }
                    saving = false
                }
            }) { Text(if (saving) stringResource(R.string.contacts_adding) else stringResource(R.string.contacts_add)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun EditContactDialog(
    contact: ContactPreview,
    onSubmit: suspend (userId: Long, firstName: String, lastName: String, phone: String) -> Result<Unit>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Черновик живёт только в этом диалоге: после отмены на экране остаются данные TDLib.
    var firstName by remember(contact.userId) { mutableStateOf(contact.firstName) }
    var lastName by remember(contact.userId) { mutableStateOf(contact.lastName) }
    var phone by remember(contact.userId) { mutableStateOf(contact.phone) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val nameValid = firstName.isNotBlank() || lastName.isNotBlank()
    val phoneValid = phone.isBlank() || phone.count { it.isDigit() } >= 7
    val canSave = !saving && nameValid && phoneValid
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.contacts_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(firstName, { firstName = it.take(64) }, label = { Text(stringResource(R.string.contacts_field_first_name)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(lastName, { lastName = it.take(64) }, label = { Text(stringResource(R.string.contacts_field_last_name)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(phone, { phone = it.take(20) }, label = { Text(stringResource(R.string.contacts_field_phone)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                if (phone.isNotBlank() && !phoneValid) Text(stringResource(R.string.err_phone_incomplete), color = palette.danger, fontSize = 14.sp)
                Text(stringResource(R.string.contacts_edit_sync_hint), color = palette.textSecondary, fontSize = 13.sp)
                error?.let { Text(it, color = palette.danger, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    onSubmit(contact.userId, firstName.trim(), lastName.trim(), phone.filter { it.isDigit() || it == '+' })
                        .onSuccess { onSaved(); onDismiss() }
                        .onFailure { error = it.userMessage(context, R.string.contacts_edit_failed) }
                    saving = false
                }
            }) { Text(if (saving) stringResource(R.string.contacts_saving) else stringResource(R.string.contacts_save)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun DeleteContactDialog(
    contact: ContactPreview,
    onDelete: suspend (userId: Long) -> Result<Unit>,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val displayName = contact.title.ifBlank { stringResource(R.string.user_name_unknown) }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.contacts_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.contacts_delete_message, displayName))
                error?.let { Text(it, color = palette.danger, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = {
                scope.launch {
                    deleting = true
                    error = null
                    onDelete(contact.userId)
                        .onSuccess { onDeleted(); onDismiss() }
                        .onFailure { error = it.userMessage(context, R.string.contacts_delete_failed) }
                    deleting = false
                }
            }) { Text(if (deleting) stringResource(R.string.contacts_deleting) else stringResource(R.string.contacts_delete_confirm), color = palette.danger) }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun ShareContactDialog(
    contact: ContactPreview,
    chats: List<ChatPreview>,
    sendContact: suspend (chatId: Long, userId: Long) -> Result<Unit>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSent: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val displayName = contact.title.ifBlank { stringResource(R.string.user_name_unknown) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.contacts_share_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                if (sending) Text(stringResource(R.string.contacts_share_sending), color = palette.textSecondary, fontSize = 13.sp)
                error?.let { Text(it, color = palette.danger, fontSize = 14.sp) }
                if (chats.isEmpty()) {
                    Text(stringResource(R.string.contacts_share_no_chats), color = palette.textSecondary, fontSize = 14.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(enabled = !sending, onClick = {
                                        if (!sending) scope.launch {
                                            sending = true
                                            error = null
                                            sendContact(chat.id, contact.userId)
                                                .onSuccess { onSent(); onDismiss() }
                                                .onFailure { error = it.userMessage(context, R.string.contacts_share_failed) }
                                            sending = false
                                        }
                                    })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChatAvatar(chat, 40.dp, observeFile, downloadFile)
                                Spacer(Modifier.width(12.dp))
                                Text(chat.title, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
