package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shows [StickerSetSheet] only while a sticker set is selected. Keeping the attachment send/dismiss wiring here leaves
 * ChatScreen's own composable body small (it is close to the JVM method-size limit).
 */
@Composable
internal fun StickerSetOverlay(
    setId: Long?,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    attachments: AttachmentState,
    onDismiss: () -> Unit
) {
    val set = setId ?: return
    StickerSetSheet(
        setId = set,
        conversation = conversation,
        observeFile = observeFile,
        downloadFile = downloadFile,
        onSend = { item ->
            if (!attachments.sending && !attachments.importing) {
                attachments.sendStickerNow(item.toOutgoingMedia())
                onDismiss()
            }
        },
        onDismiss = onDismiss
    )
}

/**
 * Read-only set browser for views without a composer, such as a discussion thread or the original-message dialog.
 * The grid still shows and downloads previews, while sending stays in the chat that owns the message.
 */
@Composable
internal fun StickerSetBrowser(
    setId: Long?,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val set = setId ?: return
    StickerSetSheet(
        setId = set,
        conversation = conversation,
        observeFile = observeFile,
        downloadFile = downloadFile,
        onSend = null,
        onDismiss = onDismiss
    )
}

/**
 * Information about the sticker set behind a message. The user can browse it, send one of its stickers, and add or
 * remove the set. The sheet is independent from [StickerPickerPanel]: both share the grid and artwork primitives, but
 * this screen only fetches the one set TDLib identified for the tapped sticker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerSetSheet(
    setId: Long,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    /** Null keeps the set readable when the caller has no chat to send into. */
    onSend: ((StickerItem) -> Unit)?,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember(setId) { mutableStateOf<StickerSetContent?>(null) }
    var loading by remember(setId) { mutableStateOf(setId > 0L) }
    var loadError by remember(setId) { mutableStateOf<String?>(null) }
    var retry by remember(setId) { mutableIntStateOf(0) }
    var installedOverride by remember(setId) { mutableStateOf<Boolean?>(null) }
    var changing by remember(setId) { mutableStateOf(false) }
    var changeError by remember(setId) { mutableStateOf<String?>(null) }
    // Null means "not known yet"; an unknown premium status never blocks browsing, and TDLib still owns the check.
    var premium by remember { mutableStateOf<Boolean?>(null) }
    var notice by remember(setId) { mutableStateOf<String?>(null) }
    val premiumNotice = stringResource(R.string.sticker_picker_premium_locked)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(setId, retry) {
        if (setId <= 0L) return@LaunchedEffect
        loading = true
        loadError = null
        content = null
        conversation.stickerSet(setId)
            .onSuccess { content = it }
            .onFailure { loadError = it.userMessage(context, R.string.sticker_set_load_failed) }
        loading = false
    }
    LaunchedEffect(setId) {
        if (setId > 0L) conversation.isPremiumAccount().onSuccess { premium = it }
    }

    val loaded = content
    val installed = installedOverride ?: loaded?.isInstalled ?: false
    val cover = loaded?.stickers?.firstOrNull()
    val title = loaded?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.sticker_set_title)

    fun toggleInstalled() {
        if (changing || setId <= 0L) return
        val target = !installed
        scope.launch {
            changing = true
            changeError = null
            val result = conversation.changeStickerSet(setId, target)
            if (result.isSuccess) {
                installedOverride = target
                notice = null
            } else {
                changeError = result.exceptionOrNull().userMessage(
                    context,
                    if (target) R.string.sticker_set_add_failed else R.string.sticker_set_remove_failed
                )
            }
            changing = false
        }
    }

    fun send(item: StickerItem) {
        val action = onSend ?: return
        if (item.premium && premium == false) {
            notice = premiumNotice
            return
        }
        notice = null
        action(item)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.surface) {
        Column(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 560.dp).padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cover != null) {
                    StickerArtwork(
                        cover,
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                        observeFile,
                        downloadFile
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    title,
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textSecondary)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val current = loaded
                when {
                    setId <= 0L -> PickerEmpty(stringResource(R.string.sticker_set_unavailable))
                    loading -> PickerCenter {
                        CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
                    loadError != null -> PickerError(loadError.orEmpty(), onRetry = { retry++ })
                    current == null -> PickerEmpty(stringResource(R.string.sticker_set_empty))
                    stickerSetContentState(setId, current) == StickerSetContentState.Empty ->
                        PickerEmpty(stringResource(R.string.sticker_set_empty))
                    else -> Column(Modifier.fillMaxSize()) {
                        StickerSetActions(
                            count = current.stickers.size,
                            installed = installed,
                            changing = changing,
                            onToggle = ::toggleInstalled
                        )
                        notice?.let { message ->
                            Text(
                                message,
                                color = palette.danger,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).errorAnnouncement()
                            )
                        }
                        changeError?.let { message ->
                            Text(
                                message,
                                color = palette.danger,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).errorAnnouncement()
                            )
                        }
                        StickerGrid(
                            stickers = current.stickers,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            onClick = if (onSend != null) ::send else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerSetActions(
    count: Int,
    installed: Boolean,
    changing: Boolean,
    onToggle: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.sticker_picker_set_size, count),
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(if (installed) R.string.sticker_set_installed else R.string.sticker_set_not_installed),
                color = if (installed) palette.accentInk else palette.textSecondary,
                fontSize = 12.sp
            )
        }
        if (changing) {
            CircularProgressIndicator(Modifier.size(22.dp), color = palette.accent, strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(
                    if (installed) Icons.Rounded.Remove else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (installed) R.string.sticker_set_remove else R.string.sticker_set_add),
                    color = palette.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
