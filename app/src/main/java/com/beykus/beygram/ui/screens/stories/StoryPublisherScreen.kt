package com.beykus.beygram.ui.screens.stories

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryPostDenial
import com.beykus.beygram.domain.model.StoryPostEvent
import com.beykus.beygram.domain.model.StoryPostPermission
import com.beykus.beygram.domain.model.StoryPrivacy
import com.beykus.beygram.domain.model.storyActivePeriodChoices
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date

private const val POST_WAIT_MS = 5 * 60_000L

/**
 * Publishes one photo or video with the privacy, lifetime and protection options TDLib actually exposes.
 * PostStory returns a temporary story; the screen waits for UpdateStoryPostSucceeded/Failed and never
 * reports success before Telegram confirms it.
 */
@Composable
fun StoryPublisherScreen(
    repository: TelegramRepository,
    myProfile: ProfileInfo?,
    accountId: String,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    SecureStoryWindow()
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val importer = remember(context, accountId) { OutgoingMediaImporter(context, accountId) }

    var media by remember { mutableStateOf<OutgoingMedia?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var captionLimit by remember { mutableIntStateOf(1_024) }
    var privacy by remember { mutableStateOf(StoryPrivacy.Everyone) }
    var selectedUsers by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var activePeriod by remember { mutableIntStateOf(24 * 3600) }
    var protectContent by remember { mutableStateOf(false) }
    var keepInProfile by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRecipients by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf<StoryPostPermission?>(null) }
    var permissionLoading by remember { mutableStateOf(true) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var permissionAttempt by remember { mutableIntStateOf(0) }

    val premium = myProfile?.isPremium == true
    val periods = storyActivePeriodChoices(premium)
    val denied = (permission as? StoryPostPermission.Denied)?.reason

    LaunchedEffect(repository) {
        captionLimit = repository.storyCaptionLimit().getOrDefault(1_024).coerceAtLeast(1)
    }
    LaunchedEffect(repository, permissionAttempt) {
        permissionLoading = true
        permissionError = null
        repository.storyPostPermission()
            .onSuccess { permission = it }
            .onFailure {
                permission = null
                permissionError = it.userMessage(context, R.string.stories_permission_failed)
            }
        permissionLoading = false
    }

    val discard: (OutgoingMedia?) -> Unit = { item ->
        if (item != null) cleanupScope.launch { importer.discard(item) }
    }
    val close: () -> Unit = {
        if (!publishing) {
            discard(media)
            media = null
            onBack()
        }
    }
    BackHandler { close() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparing = true
        error = null
        scope.launch {
            try {
                val prepared = importer.prepare(uri, asDocument = false)
                if (prepared.kind != MediaKind.Photo && prepared.kind != MediaKind.Video) {
                    discard(prepared)
                    error = context.getString(R.string.stories_media_invalid)
                } else {
                    val old = media
                    media = prepared
                    discard(old)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.userMessage(context, R.string.stories_media_prepare_failed)
            } finally {
                preparing = false
            }
        }
    }

    val startPublish: () -> Unit = {
        val item = media
        when {
            item == null -> error = context.getString(R.string.stories_publish_need_media)
            privacy == StoryPrivacy.SelectedUsers && selectedUsers.isEmpty() ->
                error = context.getString(R.string.stories_selected_empty)
            permission is StoryPostPermission.Denied -> Unit
            else -> {
                publishing = true
                error = null
                scope.launch {
                    repository.publishStory(
                        media = item,
                        caption = caption,
                        privacy = privacy,
                        selectedUserIds = selectedUsers.toList(),
                        activePeriodSeconds = activePeriod,
                        protectContent = protectContent,
                        keepInProfile = keepInProfile
                    ).onSuccess { ticket ->
                        if (!ticket.isPending) {
                            publishing = false
                            discard(item)
                            media = null
                            onPublished()
                            return@onSuccess
                        }
                        val result = withTimeoutOrNull(POST_WAIT_MS) {
                            repository.storyPostEvents.first { event ->
                                when (event) {
                                    is StoryPostEvent.Published -> event.posterChatId == ticket.posterChatId &&
                                        (event.storyId == ticket.storyId || event.oldStoryId == ticket.storyId)
                                    is StoryPostEvent.Failed -> event.posterChatId == ticket.posterChatId &&
                                        event.storyId == ticket.storyId
                                }
                            }
                        }
                        publishing = false
                        when (result) {
                            is StoryPostEvent.Published -> {
                                discard(item)
                                media = null
                                onPublished()
                            }
                            is StoryPostEvent.Failed -> {
                                error = context.getString(storyPostDenialText(result.reason))
                            }
                            null -> error = context.getString(R.string.stories_publish_pending)
                        }
                    }.onFailure {
                        publishing = false
                        error = it.userMessage(context, R.string.stories_publish_failed)
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = close, enabled = !publishing) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Text(
                stringResource(R.string.stories_publish_title),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            IconButton(onClick = { showArchive = true }, enabled = !publishing) {
                Icon(Icons.Rounded.Archive, stringResource(R.string.stories_archive_open), tint = palette.textSecondary)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            val description = stringResource(
                if (media == null) R.string.stories_choose_media_description else R.string.stories_change_media
            )
            Box(
                Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(24.dp))
                    .background(palette.surface2)
                    .clickable(enabled = !preparing && !publishing, role = Role.Button) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                val previewPath = media?.thumbnailPath ?: media?.takeIf { it.kind == MediaKind.Photo }?.path
                if (previewPath != null) {
                    LocalMediaImage(previewPath, Modifier.fillMaxSize())
                } else if (media == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Image, null, tint = palette.accent, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.stories_pick_media), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (media?.kind == MediaKind.Video) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = palette.accent, modifier = Modifier.size(42.dp))
                }
                if (media?.kind == MediaKind.Video && previewPath != null) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                if (media != null) {
                    Text(
                        stringResource(R.string.stories_change_media),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                if (preparing) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text(stringResource(R.string.stories_preparing), color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = { value ->
                    if (value.codePointCount(0, value.length) <= captionLimit) caption = value
                },
                label = { Text(stringResource(R.string.stories_caption)) },
                placeholder = { Text(stringResource(R.string.stories_caption_placeholder)) },
                minLines = 2,
                maxLines = 5,
                enabled = !publishing,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.stories_caption_counter, caption.codePointCount(0, caption.length), captionLimit),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.End
            )

            StorySectionTitle(stringResource(R.string.stories_privacy))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    StoryPrivacy.Everyone to R.string.stories_privacy_everyone,
                    StoryPrivacy.Contacts to R.string.stories_privacy_contacts,
                    StoryPrivacy.CloseFriends to R.string.stories_privacy_close_friends,
                    StoryPrivacy.SelectedUsers to R.string.stories_privacy_selected
                ).forEach { (option, label) ->
                    StoryChoiceChip(
                        label = stringResource(label),
                        selected = privacy == option,
                        enabled = !publishing,
                        onClick = { privacy = option }
                    )
                }
            }
            if (privacy == StoryPrivacy.SelectedUsers) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { showRecipients = true },
                        enabled = !publishing
                    ) { Text(stringResource(R.string.stories_choose_recipients)) }
                    Text(
                        stringResource(R.string.stories_selected_count, selectedUsers.size),
                        color = if (selectedUsers.isEmpty()) palette.danger else palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
                if (selectedUsers.isEmpty()) {
                    Text(stringResource(R.string.stories_selected_empty), color = palette.danger, fontSize = 13.sp)
                }
            }

            StorySectionTitle(stringResource(R.string.stories_lifetime))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periods.forEach { seconds ->
                    StoryChoiceChip(
                        label = stringResource(
                            when (seconds) {
                                6 * 3600 -> R.string.stories_period_6h
                                12 * 3600 -> R.string.stories_period_12h
                                24 * 3600 -> R.string.stories_period_24h
                                else -> R.string.stories_period_48h
                            }
                        ),
                        selected = activePeriod == seconds,
                        enabled = !publishing,
                        onClick = { activePeriod = seconds }
                    )
                }
            }
            if (!premium) {
                Text(stringResource(R.string.stories_premium_lifetime), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }

            StorySwitchRow(
                title = stringResource(R.string.stories_protect),
                description = stringResource(R.string.stories_protect_note),
                checked = protectContent,
                enabled = !publishing,
                onCheckedChange = { protectContent = it }
            )
            StorySwitchRow(
                title = stringResource(R.string.stories_keep_profile),
                description = stringResource(R.string.stories_keep_profile_note),
                checked = keepInProfile,
                enabled = !publishing,
                onCheckedChange = { keepInProfile = it }
            )

            if (permissionLoading) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stories_permission_checking), color = palette.textSecondary, fontSize = 13.sp)
                }
            }
            if (permissionError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(permissionError.orEmpty(), color = palette.danger, fontSize = 13.sp, modifier = Modifier.weight(1f).errorAnnouncement())
                    TextButton(onClick = { permissionAttempt++ }) { Text(stringResource(R.string.common_retry)) }
                }
            } else if (denied != null) {
                Text(
                    stringResource(storyPostDenialText(denied)),
                    color = palette.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp).errorAnnouncement()
                )
            }

            error?.let {
                Text(it, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp).errorAnnouncement())
            }

            Button(
                onClick = startPublish,
                enabled = !preparing && !publishing && !permissionLoading && media != null &&
                    permission !is StoryPostPermission.Denied && permissionError == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 16.dp)
            ) {
                if (publishing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (publishing) R.string.stories_publishing else R.string.stories_publish), fontSize = 16.sp)
            }
        }
    }

    if (showRecipients) {
        StoryRecipientsDialog(
            repository = repository,
            selected = selectedUsers,
            onDismiss = { showRecipients = false },
            onConfirm = { selectedUsers = it; showRecipients = false }
        )
    }
    if (showArchive) {
        StoryArchiveDialog(repository = repository, onDismiss = { showArchive = false })
    }
}

@Composable
private fun StorySectionTitle(text: String) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() }
    )
}

@Composable
private fun StoryChoiceChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.accent else palette.surface2)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = when {
                !enabled -> palette.textSecondary.copy(alpha = 0.5f)
                selected -> palette.onAccent
                else -> palette.textPrimary
            },
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun StorySwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun storyPostDenialText(reason: StoryPostDenial): Int = when (reason) {
    StoryPostDenial.PremiumRequired -> R.string.stories_denied_premium
    StoryPostDenial.ActiveStoryLimit -> R.string.stories_denied_active_limit
    StoryPostDenial.WeeklyLimit -> R.string.stories_denied_weekly
    StoryPostDenial.MonthlyLimit -> R.string.stories_denied_monthly
    StoryPostDenial.BoostRequired -> R.string.stories_denied_boost
    StoryPostDenial.LiveStoryActive -> R.string.stories_denied_live
    StoryPostDenial.Unavailable -> R.string.stories_denied_unknown
}

@Composable
private fun StoryRecipientsDialog(
    repository: TelegramRepository,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    val palette = beyGramPalette()
    var contacts by remember { mutableStateOf<List<ContactPreview>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(selected) }
    LaunchedEffect(repository) {
        repository.loadContacts()
            .onSuccess { contacts = it }
            .onFailure { failed = true }
        loading = false
    }
    val filtered = remember(contacts, query) {
        contacts.orEmpty().filter {
            query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(palette.surface).padding(18.dp)
        ) {
            Text(
                stringResource(R.string.stories_choose_recipients),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.stories_search_contacts)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            when {
                loading -> Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stories_contacts_loading), color = palette.textSecondary)
                }
                failed -> Text(
                    stringResource(R.string.stories_contacts_failed),
                    color = palette.danger,
                    modifier = Modifier.padding(vertical = 18.dp).errorAnnouncement()
                )
                contacts.orEmpty().isEmpty() -> Text(
                    stringResource(R.string.stories_contacts_empty),
                    color = palette.textSecondary,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
                filtered.isEmpty() -> Text(
                    stringResource(R.string.chats_empty_no_results),
                    color = palette.textSecondary,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 6.dp)) {
                    items(filtered, key = { it.userId }) { contact ->
                        val checked = contact.userId in chosen
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .toggleable(value = checked, role = Role.Checkbox) {
                                    chosen = if (checked) chosen - contact.userId else chosen + contact.userId
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            TelegramAvatar(
                                contact.initials,
                                Color(contact.avatarColorArgb),
                                38.dp,
                                contact.photo,
                                repository::observeFile,
                                repository::downloadFile
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (contact.subtitle.isNotBlank()) {
                                    Text(contact.subtitle, color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(enabled = chosen.isNotEmpty(), onClick = { onConfirm(chosen) }) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}

@Composable
private fun StoryArchiveDialog(repository: TelegramRepository, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stories by remember { mutableStateOf<List<StoryDetail>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<StoryDetail?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        loading = true
        error = null
        repository.storyArchive()
            .onSuccess { stories = it }
            .onFailure { error = it.userMessage(context, R.string.stories_archive_load_failed) }
        loading = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(palette.surface).padding(18.dp)
        ) {
            Text(
                stringResource(R.string.stories_archive_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            when {
                loading -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stories_archive_loading), color = palette.textSecondary)
                }
                error != null -> {
                    Text(error.orEmpty(), color = palette.danger, modifier = Modifier.padding(top = 16.dp).errorAnnouncement())
                    TextButton(onClick = { reload++ }) { Text(stringResource(R.string.common_retry)) }
                }
                stories.orEmpty().isEmpty() -> Text(
                    stringResource(R.string.stories_archive_empty),
                    color = palette.textSecondary,
                    modifier = Modifier.padding(vertical = 22.dp)
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(stories.orEmpty(), key = { it.storyId }) { story ->
                        StoryArchiveRow(story, repository, onDelete = { deleting = story })
                        HorizontalDivider(color = palette.divider)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
    deleting?.let { story ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.stories_delete)) },
            text = { Text(stringResource(R.string.stories_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        repository.deleteStory(story.posterChatId, story.storyId)
                            .onSuccess { stories = stories.orEmpty().filterNot { it.storyId == story.storyId } }
                            .onFailure { error = it.userMessage(context, R.string.stories_delete_failed) }
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun StoryArchiveRow(story: StoryDetail, repository: TelegramRepository, onDelete: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val fileId = story.media?.thumbnailFileId ?: story.media?.fileId
    val file by remember(fileId) {
        fileId?.let(repository::observeFile) ?: kotlinx.coroutines.flow.MutableStateFlow(com.beykus.beygram.domain.model.MediaFileState(0))
    }.collectAsStateWithLifecycle()
    LaunchedEffect(fileId) { if (fileId != null) repository.downloadFile(fileId) }
    val date = remember(story.dateSeconds) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(story.dateSeconds * 1000L))
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(palette.surface2), contentAlignment = Alignment.Center) {
            val path = file.localPath
            if (path != null) LocalMediaImage(path, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.Image, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(date, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (story.caption.isNotBlank()) {
                Text(story.caption, color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp).semantics {
            contentDescription = context.getString(R.string.stories_archive_delete_description, date)
        }) {
            Icon(Icons.Rounded.Delete, null, tint = palette.danger)
        }
    }
}
