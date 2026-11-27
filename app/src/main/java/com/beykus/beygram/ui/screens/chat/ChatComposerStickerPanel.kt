package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** The picker replaces the IME below the field and retains its view when it closes. */
@Composable
internal fun ChatComposerStickerPanel(
    open: Boolean,
    accountId: String,
    panelTopicId: Long,
    keyboardHeightHint: Dp,
    attachments: AttachmentState,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    chatId: Long,
    replyId: Long?,
    forumTopicId: Long,
    scope: CoroutineScope,
    onReplyConsumed: (Long?) -> Unit,
    onStickerOptions: (OutgoingMedia) -> Unit,
    onSendError: (String) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onCloseStickerPanel: () -> Unit
) {
    key(accountId, chatId, panelTopicId) {
        val state = rememberSaveableStateHolder()
        val motion = rememberMotionSpec(MotionToken.PickerSwitch)
        AnimatedVisibility(
            visible = open,
            enter = if (motion.isInstant) EnterTransition.None else expandVertically(motion.animationSpec()),
            exit = if (motion.isInstant) ExitTransition.None else shrinkVertically(motion.animationSpec())
        ) {
            state.SaveableStateProvider("composer-picker") {
                StickerPanelHost(
                    conversation = conversation,
                    chatId = chatId,
                    replyId = replyId,
                    forumTopicId = forumTopicId,
                    observeFile = observeFile,
                    downloadFile = onDownloadFile,
                    scope = scope,
                    composerReserve = 0.dp,
                    keyboardHeightHint = keyboardHeightHint,
                    onReplyConsumed = onReplyConsumed,
                    onAnimationAttach = { animation ->
                        attachments.addAnimation(animation.toOutgoingMedia())
                        onCloseStickerPanel()
                    },
                    onStickerOptions = { sticker ->
                        onStickerOptions(sticker.toOutgoingMedia())
                        onCloseStickerPanel()
                    },
                    onSendError = onSendError,
                    onInsertEmoji = onInsertEmoji,
                    onDeleteBackward = onDeleteBackward,
                    onClose = onCloseStickerPanel
                )
            }
        }
    }
}
