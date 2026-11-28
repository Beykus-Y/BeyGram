package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.settings.ChatOverridesStore
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.screens.music.AddToPlaylistSheet
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
internal fun ChatOverlaysHost(
    chat: ChatPreview,
    accountId: String,
    accountLabel: String,
    forumTopicTitle: String?,
    info: ProfileInfo?,
    chats: () -> List<ChatPreview>,
    conversation: ConversationRepository,
    chatOverrides: ChatOverridesStore,
    globalWallpaper: ChatWallpaper,
    supportsGroupAvatars: Boolean,
    palette: BeyGramPalette,
    context: Context,
    clipboard: ClipboardManager,
    scope: CoroutineScope,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)?,
    sendGuard: StealthSendGuard,
    riskGuard: StealthRiskGuard,
    attachments: AttachmentState,
    contextChatId: Long,
    contextMessageId: Long?,
    onDismissContextMessage: () -> Unit,
    playlistTrack: PlaylistTrack?,
    onDismissPlaylistTrack: () -> Unit,
    copyLinks: List<String>,
    onClearCopyLinks: () -> Unit,
    pinConfirm: Boolean,
    actionMessage: ChatMessage?,
    onDismissPinConfirm: () -> Unit,
    onSetPinned: (ChatMessage, Boolean) -> Unit,
    paidTextConfirmation: PaidTextConfirmation?,
    onClearPaidTextConfirmation: () -> Unit,
    paidTextDestination: PaidTextDestination,
    canPostHere: Boolean,
    composerDraft: MessageDraft,
    pendingSticker: OutgoingMedia?,
    onClearPendingSticker: () -> Unit,
    editId: Long?,
    textSendQueue: TextSendQueue,
    textSendScopeKey: TextSendScopeKey,
    textLimit: Int,
    botKeyboard: BotKeyboard?,
    updateDraft: ((MessageDraft) -> MessageDraft) -> Unit,
    failedSendTarget: FailedSendTarget?,
    onDismissFailedSendTarget: () -> Unit,
    onRetryLocalTextAttempt: (Long) -> Unit,
    onRetryFailedMessages: (List<Long>) -> Unit,
    queueStorageFailureText: String,
    sendOptionsOpen: Boolean,
    onDismissSendOptions: () -> Unit,
    onSendCurrentDraft: (MessageSendOptions) -> Unit,
    scheduledMessagesOpen: Boolean,
    onDismissScheduledMessages: () -> Unit,
    onRequestReplyFocus: () -> Unit,
    pinnedChrome: ChatPinnedChromeState,
    pinnedBarHidden: Boolean,
    onJumpToPinned: (ChatMessage) -> Unit,
    chatSettingsOpen: Boolean,
    onDismissChatSettings: () -> Unit,
    onBack: () -> Unit,
    exportOpen: Boolean,
    onDismissExport: () -> Unit,
    botCommandMenu: BotCommandMenu?,
    botCommandMenuOpen: Boolean,
    onDismissBotCommandMenu: () -> Unit,
    onInsertBotCommand: (String) -> Unit,
    pendingWebApp: PendingBotWebApp?,
    onDismissPendingWebApp: () -> Unit,
    onLaunchBotWebApp: (PendingBotWebApp) -> Unit,
    webAppSession: BotWebAppLaunch?,
    onDismissWebAppSession: () -> Unit,
    onError: (String) -> Unit
) {
    ChatScreenOverlays {
        contextMessageId?.let { id ->
            MessageContextDialog(
                contextChatId,
                id,
                conversation,
                riskGuard,
                observeFile,
                onDownloadFile,
                onClose = onDismissContextMessage,
                autoDownloadFile = onAutoDownloadFile,
                onOpenMessageEntity = onOpenMessageEntity,
                allowEntityCopy = !chat.hasProtectedContent
            )
        }
        playlistTrack?.let { track ->
            AddToPlaylistSheet(accountId = accountId, track = track, onDismiss = onDismissPlaylistTrack)
        }
        if (copyLinks.isNotEmpty()) {
            MessageLinkCopyDialog(
                links = copyLinks,
                onCopy = { value ->
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_action_copy_entity), value))
                    Toast.makeText(context, context.getString(R.string.message_entity_copied), Toast.LENGTH_SHORT).show()
                    onClearCopyLinks()
                },
                onDismiss = onClearCopyLinks
            )
        }
        if (pinConfirm && actionMessage != null) {
            BeyGramDialog(
                onDismissRequest = onDismissPinConfirm,
                title = stringResource(R.string.chat_pin_channel_title),
                message = stringResource(R.string.chat_pin_channel_message),
                confirmText = stringResource(R.string.message_action_pin),
                onConfirm = { onSetPinned(actionMessage, true) },
                dismissText = stringResource(R.string.common_cancel),
                onDismiss = onDismissPinConfirm
            )
        }
        paidTextConfirmation?.let { confirmation ->
            BeyGramDialog(
                onDismissRequest = onClearPaidTextConfirmation,
                title = stringResource(R.string.paid_text_title),
                message = stringResource(
                    R.string.paid_text_confirm,
                    formatStarAmount(confirmation.price),
                    formatStarAmount(confirmation.balance.starCount, confirmation.balance.nanostarCount)
                ),
                confirmText = stringResource(R.string.common_send),
                confirmEnabled = paidTextCanConfirm(confirmation.price, confirmation.balance),
                onConfirm = {
                    // Ignore a second callback from a dialog that already consumed its confirmation.
                    if (paidTextConfirmation === confirmation) {
                        onClearPaidTextConfirmation()
                        if (paidTextDestination != confirmation.destination || !canPostHere ||
                            composerDraft != confirmation.draft || attachments.active || pendingSticker != null || editId != null
                        ) {
                            onError(context.getString(R.string.paid_text_draft_changed))
                        } else if (!conversation.stealthHoldsSends() && !textSendQueue.isComposerWriting(textSendScopeKey)) {
                            val outgoing = composerLaunchPayload(confirmation.draft.text, confirmation.draft.entities, textLimit)
                            val forceReply = botKeyboard?.takeIf { it.forceReply }?.messageId
                            // Process-scoped composer mutex, but no queued/persisted retryable payment authorization.
                            textSendQueue.launchComposerWrite(textSendScopeKey) {
                                val result = conversation.sendPaidTextMessage(
                                    confirmation.destination.chatId, outgoing.text, confirmation.draft.replyToMessageId, outgoing.entities,
                                    confirmation.price, confirmation.disableNotification,
                                    confirmation.destination.forumTopicId, confirmation.disableLinkPreview
                                )
                                if (result.isSuccess) {
                                    updateDraft { if (it == confirmation.draft) MessageDraft() else it }
                                    forceReply?.let { conversation.dismissBotKeyboard(chat.id, it) }
                                } else {
                                    val reason = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_send_failed).orEmpty()
                                    Toast.makeText(
                                        context.applicationContext,
                                        reason + "\n" + context.getString(R.string.paid_text_uncertain),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            onError(context.getString(R.string.stealth_schedule_unavailable_paid))
                        }
                    }
                },
                dismissText = stringResource(R.string.common_cancel),
                onDismiss = onClearPaidTextConfirmation
            )
        }
        sendGuard.Dialog()
        riskGuard.Dialog()
        attachments.stealthFallback.Dialog()
        failedSendTarget?.let { target ->
            FailedSendDetailsDialog(
                target = target,
                onDismiss = onDismissFailedSendTarget,
                onRetry = {
                    val attemptId = target.localAttemptId
                    if (attemptId != null) {
                        onRetryLocalTextAttempt(attemptId)
                    } else {
                        onRetryFailedMessages(listOf(target.message.id))
                    }
                },
                onDiscard = {
                    val attemptId = target.localAttemptId
                    if (attemptId != null) {
                        textSendQueue.launchMutation {
                            try {
                                textSendQueue.discard(textSendScopeKey, attemptId)
                            } catch (_: TextSendStorageException) {
                                onError(queueStorageFailureText)
                            }
                        }
                    } else {
                        scope.launch {
                            conversation.deleteMessages(chat.id, listOf(target.message.id), DeleteMessageScope.ForSelf)
                        }
                    }
                }
            )
        }
        if (sendOptionsOpen) {
            SendOptionsDialog(
                canSchedule = !chat.isSecret,
                onDismiss = { onDismissSendOptions(); onClearPendingSticker() },
                onSendNow = { onDismissSendOptions(); sendGuard.run({ onClearPendingSticker() }) { onSendCurrentDraft(MessageSendOptions()) } },
                onSendSilently = { onDismissSendOptions(); sendGuard.run({ onClearPendingSticker() }) { onSendCurrentDraft(MessageSendOptions(disableNotification = true)) } },
                onSchedule = { at -> onDismissSendOptions(); sendGuard.run({ onClearPendingSticker() }) { onSendCurrentDraft(MessageSendOptions(scheduledAtSeconds = at)) } }
            )
        }
        if (scheduledMessagesOpen) {
            ScheduledMessagesDialog(
                chatId = chat.id,
                conversation = conversation,
                onClose = onDismissScheduledMessages,
                onCompose = if (canPostHere) ({
                    onDismissScheduledMessages()
                    onRequestReplyFocus()
                }) else null
            )
        }
        if (pinnedChrome.isListOpen) {
            PinnedMessagesScreen(
                state = pinnedChrome.messages,
                conversation = conversation,
                chat = chat,
                chatTitle = forumTopicTitle?.takeIf { it.isNotBlank() }?.let { "${chat.title} · $it" } ?: chat.title,
                members = info?.memberPreview.orEmpty(),
                senderChats = chats(),
                observeFile = observeFile,
                downloadFile = onDownloadFile,
                barHidden = pinnedBarHidden,
                previewSource = pinnedChrome.previewSource,
                livePreviewSource = { pinnedChrome.livePreview },
                headerSource = pinnedChrome.headerSource,
                liveHeaderSource = { pinnedChrome.liveHeader },
                onRestoreBar = { chatOverrides.setPinnedBarHidden(chat.id, false) },
                onOpenMessage = { message ->
                    pinnedChrome.closeList()
                    onJumpToPinned(message)
                },
                onClose = pinnedChrome::closeList
            )
        }
        if (chatSettingsOpen) {
            ChatSettingsSheet(
                chatId = chat.id,
                conversation = conversation,
                overrides = chatOverrides,
                globalWallpaper = globalWallpaper,
                isSecretChat = chat.isSecret,
                supportsGroupAvatars = supportsGroupAvatars,
                onSecretChatClosed = onBack,
                onClose = onDismissChatSettings
            )
        }
        if (exportOpen) {
            ChatExportDialog(
                chat = chat,
                accountId = accountId,
                accountLabel = accountLabel,
                conversation = conversation,
                onDismiss = onDismissExport
            )
        }
        botCommandMenu?.let { menu ->
            if (botCommandMenuOpen) {
                BotCommandSheet(chat.title, menu, onDismiss = onDismissBotCommandMenu, onInsert = onInsertBotCommand)
            }
        }
    }
    pendingWebApp?.let { request ->
        BeyGramDialog(
            onDismissRequest = onDismissPendingWebApp,
            title = stringResource(R.string.chat_webapp_consent_title),
            message = stringResource(R.string.chat_webapp_consent),
            confirmText = stringResource(R.string.chat_webapp_open_action),
            onConfirm = { onDismissPendingWebApp(); onLaunchBotWebApp(request) },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = onDismissPendingWebApp
        )
    }
    webAppSession?.let { session ->
        // DisposableEffect above closes the TDLib launch when this state is cleared or the screen is disposed.
        BotWebAppScreen(
            session = session,
            title = chat.title,
            theme = palette.toBotWebAppTheme(),
            onSendData = { data -> conversation.sendBotWebAppData(session.botUserId, session.buttonText, data) },
            onClose = onDismissWebAppSession
        )
    }
}
