package com.beykus.beygram.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.MessageExtraDraft
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.security.AppLockManager
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/**
 * Poll, location, contact and dice choices of the attachment menu. Kept out of [ChatScreen] so its
 * composable body stays under the JVM method size limit; each choice ends in [AttachmentState.prepareExtra].
 */
@Composable
internal fun ComposerExtraPickers(chatId: Long, isSecret: Boolean, conversation: ConversationRepository, attachments: AttachmentState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    attachments.allowsPolls = !isSecret
    var pollComposerOpen by remember(chatId) { mutableStateOf(false) }
    var contactPickerOpen by remember(chatId) { mutableStateOf(false) }
    var dicePickerOpen by remember(chatId) { mutableStateOf(false) }
    var contactPickerContacts by remember(chatId) { mutableStateOf<List<ContactPreview>?>(null) }
    var contactPickerLoading by remember(chatId) { mutableStateOf(false) }
    var contactPickerError by remember(chatId) { mutableStateOf<String?>(null) }
    fun loadContactPicker() {
        if (contactPickerLoading) return
        contactPickerLoading = true
        contactPickerError = null
        scope.launch {
            conversation.contacts()
                .onSuccess { contactPickerContacts = it }
                .onFailure { contactPickerError = it.userMessage(context, R.string.contact_picker_load_failed) }
            contactPickerLoading = false
        }
    }
    LaunchedEffect(contactPickerOpen) {
        if (contactPickerOpen && contactPickerContacts == null && !contactPickerLoading) loadContactPicker()
    }
    fun locateNow() {
        val request = attachments.beginLocationRequest() ?: return
        scope.launch {
            val point = currentGeoPoint(context).getOrNull()
            val accepted = attachments.completeLocationRequest(request, point?.let { MessageExtraDraft.Location(it) })
            if (accepted && point == null) attachments.reportError(context.getString(R.string.chat_location_unavailable))
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        AppLockManager.get(context).clearExpectedExternalActivity()
        if (granted.values.any { it }) {
            locateNow()
        } else {
            attachments.reportError(context.getString(R.string.chat_location_permission_denied))
        }
    }
    fun requestLocation() {
        if (attachments.sending || attachments.importing || attachments.locating) return
        if (hasLocationPermission(context)) locateNow()
        else {
            AppLockManager.get(context).expectExternalActivity()
            locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    attachments.pickPoll = { pollComposerOpen = true }
    attachments.pickLocation = { requestLocation() }
    attachments.pickContact = { contactPickerOpen = true }
    attachments.pickDice = { dicePickerOpen = true }

    if (pollComposerOpen) PollComposerDialog(
        onDismiss = { pollComposerOpen = false },
        onSend = { poll ->
            pollComposerOpen = false
            attachments.prepareExtra(MessageExtraDraft.Poll(poll))
        }
    )
    if (contactPickerOpen) ContactPickerDialog(
        contacts = contactPickerContacts.orEmpty(),
        loading = contactPickerLoading && contactPickerContacts == null,
        error = contactPickerError,
        onRetry = ::loadContactPicker,
        onDismiss = { contactPickerOpen = false },
        onPick = { contact ->
            contactPickerOpen = false
            attachments.prepareExtra(MessageExtraDraft.Contact(contact.userId, contact.title))
        }
    )
    if (dicePickerOpen) DicePickerDialog(
        onDismiss = { dicePickerOpen = false },
        onPick = { emoji ->
            dicePickerOpen = false
            attachments.prepareExtra(MessageExtraDraft.Dice(emoji))
        }
    )
}
