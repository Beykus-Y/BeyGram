package com.beykus.beygram.ui.screens.share

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.share.IncomingShareParser
import com.beykus.beygram.share.ShareDestination
import com.beykus.beygram.share.ShareSession
import com.beykus.beygram.share.directShareCandidate
import com.beykus.beygram.share.isShareTopicValid
import com.beykus.beygram.share.shareRecipientCatalog
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.chat.ForwardChatDialog
import com.beykus.beygram.ui.screens.chat.ForwardTarget
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Входящий «Поделиться» и отправка архива: тот же встроенный выбор чатов, что у пересылки ([ForwardChatDialog]),
 * с компактным предпросмотром файлов сверху. Выбор чатов хранится в [ShareSession], отправку, повторы и отмену
 * ведёт сессия; диалог только передаёт выбор, подпись и команду «Отправить».
 * Выбор чата ничего не отправляет. Пока идёт отправка, диалог не закрывается.
 */
@Composable
internal fun ShareTargetScreen(
    requestId: Long,
    initialChatId: Long = 0L,
    text: String,
    uris: List<Uri>,
    session: ShareSession,
    chats: List<ChatPreview>,
    captionLimit: Int,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    loadTopics: suspend (chatId: Long) -> Result<List<ForumTopic>?>,
    sendText: suspend (chatId: Long, topicId: Long, text: String) -> Result<Unit>,
    sendMedia: suspend (chatId: Long, topicId: Long, media: List<OutgoingMedia>, caption: String) -> Result<Unit>,
    onSent: (chatId: Long, topicId: Long) -> Unit,
    onCancel: () -> Unit,
    recipientRepository: TelegramRepository? = null
) {
    val context = LocalContext.current
    val appScope = remember(context) { (context.applicationContext as BeyGramApplication).applicationScope }
    val importer = remember(context, session.accountId) { OutgoingMediaImporter(context, session.accountId) }
    var shortcutApplied by rememberSaveable(requestId, session.accountId) { mutableStateOf(false) }
    // Архив и папки - такие же получатели, как в пересылке, поэтому предвыбор ищет чат во всём каталоге.
    // Без репозитория архива и папок нет: используем пустые потоки, чтобы вызов collectAsStateWithLifecycle оставался безусловным.
    val emptyArchived = remember { MutableStateFlow<List<ChatPreview>>(emptyList()) }
    val emptyFolders = remember { MutableStateFlow<Map<Int, List<ChatPreview>>>(emptyMap()) }
    val archivedChats = (recipientRepository?.archivedChats ?: emptyArchived).collectAsStateWithLifecycle().value
    val folderChats = (recipientRepository?.folderChats ?: emptyFolders).collectAsStateWithLifecycle().value
    val recipientCatalog = remember(chats, archivedChats, folderChats) {
        shareRecipientCatalog(chats, archivedChats, folderChats)
    }
    // Direct Share предвыбирает только личный чат и не отправляет сам. Группы с темами выбираются вручную.
    // Чаты приходят асинхронно при холодном старте, поэтому ждём, пока чат появится в каталоге (список, архив, папки).
    LaunchedEffect(initialChatId, recipientCatalog) {
        if (!shortcutApplied && !session.deliveryStarted && session.selectedDestinations.isEmpty()) {
            directShareCandidate(initialChatId, recipientCatalog)?.let { chat ->
                session.addDestination(ShareDestination(chat.id, 0L, chat.title))
                shortcutApplied = true
            }
        }
    }
    LaunchedEffect(session) { session.startStaging(context, appScope) }
    // Отправка могла закончиться, пока экрана не было (поворот): тогда открываем чат, куда ушло сообщение.
    LaunchedEffect(session.sent) { if (session.sent) onSent(session.sentChatId, session.sentTopicId) }

    val validateDestination: suspend (ShareDestination) -> Result<Unit> = { destination ->
        val current = if (recipientRepository != null) {
            (recipientRepository.chats.value + recipientRepository.archivedChats.value + recipientRepository.folderChats.value.values.flatten())
                .firstOrNull { it.id == destination.chatId }
        } else chats.firstOrNull { it.id == destination.chatId }
        if (current?.canPost != true) Result.failure(UserMessageException(UiText.Res(R.string.share_unavailable_recipient)))
        else loadTopics(destination.chatId).mapCatching { loaded ->
            if (!isShareTopicValid(destination.topicId, Result.success(loaded))) {
                throw UserMessageException(UiText.Res(R.string.share_topics_failed))
            }
        }
    }

    // После начала отправки список фиксирован ledger-ом сессии: показываем тех, кто ещё не получил сообщение.
    val selection = if (session.deliveryStarted) session.pendingDestinations else session.selectedDestinations
    val selectedTargets = selection.map { ForwardTarget(it.chatId, it.title, it.topicId) }
    val stagedPreview: @Composable () -> Unit = { SharePreviewStrip(session, importer, appScope, context) }
    // Подсказка: неотправленного получателя можно убрать из списка, чтобы ему не слать повторно.
    val dropHint = session.canDropPending && (session.error != null || session.retryBlocked)
    val errorText = listOfNotNull(
        session.error?.resolve(),
        session.deliveryNote?.resolve(),
        if (dropHint) stringResource(R.string.share_drop_pending_hint) else null
    ).joinToString("\n").ifEmpty { null }

    ForwardChatDialog(
        chats = chats,
        busy = session.sending,
        error = errorText,
        observeFile = observeFile,
        downloadFile = downloadFile,
        commentLimit = if (session.staged.isNotEmpty()) captionLimit else IncomingShareParser.MAX_TEXT_LENGTH,
        loadTopics = loadTopics,
        singleRecipient = false,
        titleOverride = stringResource(R.string.share_title),
        previewSlot = if (uris.isEmpty()) null else stagedPreview,
        selection = selectedTargets,
        onSelectionChange = { next ->
            if (!session.deliveryStarted) session.replaceDestinations(next.map { ShareDestination(it.chatId, it.topicId, it.title) })
            // После начала отправки убранные из списка получатели больше не получат сообщение.
            else session.pendingDestinations
                .filter { destination -> next.none { it.chatId == destination.chatId && it.topicId == destination.topicId } }
                .forEach { session.dropPending(it, appScope) }
        },
        initialComment = session.draft,
        onCommentChange = { session.draft = it },
        sendBlocked = session.staging || session.stagingFailed || session.invalidFileCount || session.retryBlocked ||
            (session.staged.isEmpty() && session.draft.isBlank()),
        retryBlocked = session.retryBlocked,
        recipientRepository = recipientRepository,
        onSend = { requested, comment ->
            session.draft = comment
            session.send(session.selectedDestinations, appScope, validateDestination, sendText, sendMedia)
            // Возврат непустого списка оставляет диалог открытым: закрывает его сессия, когда отправка завершена.
            requested
        },
        onClose = onCancel
    )
}

/** Компактная полоса вложений: миниатюры, имя, удаление до отправки и повтор подготовки при сбое. */
@Composable
private fun SharePreviewStrip(
    session: ShareSession,
    importer: OutgoingMediaImporter,
    scope: CoroutineScope,
    context: Context
) {
    val palette = beyGramPalette()
    Surface(
        shape = styledShape(16.dp),
        color = palette.accentSoft,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.share_preview_title),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accentInk
            )
            if (session.staged.isNotEmpty()) Text(
                stringResource(R.string.share_files_count, session.staged.size),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            if (session.staging) {
                val fraction = if (session.stagingTotal == 0) 0f else session.stagingDone.toFloat() / session.stagingTotal
                Text(
                    stringResource(R.string.share_preparing_progress, session.stagingDone, session.stagingTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                // Полоса на фоне accentSoft: её подложка должна отличаться от фона, поэтому берём серый текст.
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accent,
                    trackColor = palette.textSecondary.copy(alpha = 0.2f)
                )
            }
            if (session.staged.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(session.staged, key = { it.path }) { item ->
                    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val previewPath = item.thumbnailPath ?: item.path.takeIf { item.kind == MediaKind.Photo }
                        if (previewPath != null) LocalMediaImage(previewPath, Modifier.size(56.dp).clip(styledShape(10.dp)), maxDimension = 160, animate = false)
                        else {
                            // Документ без превью: значок и расширение вместо картинки, размер плитки тот же.
                            val extension = item.fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.uppercase()
                            Column(
                                Modifier.size(56.dp).clip(styledShape(10.dp)).background(palette.accentSoft),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
                                if (extension != null) Text(
                                    extension,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.accentInk
                                )
                            }
                        }
                        Text(
                            text = item.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textPrimary
                        )
                        TextButton(
                            enabled = !session.sending && !session.deliveryStarted,
                            onClick = { session.removeStaged(item, importer, scope) }
                        ) { Text(stringResource(R.string.common_delete)) }
                    }
                }
            }
            if (session.stagingFailed) TextButton(onClick = { session.retryStaging(context, scope) }) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}
