package com.beykus.beygram.share

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Один запрос «Поделиться» с файлами. Копирование и отправка идут в общем scope приложения, а не в композиции,
 * поэтому поворот экрана не прерывает их и не даёт отправить второй раз.
 */
internal class ShareSession(
    val requestId: Long,
    val uris: List<Uri>,
    initialDraft: String,
    val accountId: String = "legacy",
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    var draft by mutableStateOf(initialDraft)
    var staged by mutableStateOf<List<OutgoingMedia>>(emptyList())
    var staging by mutableStateOf(uris.isNotEmpty())
    /** Сколько файлов уже подготовлено из [stagingTotal]: для полосы прогресса. */
    var stagingDone by mutableStateOf(0)
        private set
    val stagingTotal = uris.size
    var sending by mutableStateOf(false)
    /** Всё отправлено: экран закрывается и открывает чат. */
    var sent by mutableStateOf(false)
    var sentChatId by mutableStateOf(0L)
    var sentTopicId by mutableStateOf(0L)
        private set
    var retryBlocked by mutableStateOf(false)
        private set
    var selectedDestinations by mutableStateOf<List<ShareDestination>>(emptyList())
        private set
    var deliveryStarted by mutableStateOf(false)
        private set
    var pendingDestinations by mutableStateOf<List<ShareDestination>>(emptyList())
        private set
    /** Итог частичной отправки: сколько получателей уже получили сообщение. Причины по адресатам остаются в [error]. */
    var deliveryNote by mutableStateOf<UiText?>(null)
        private set
    /** Адресат, чья отправка не подтверждена: его нельзя слать повторно, пока его не уберут. */
    private var uncertainKey: Pair<Long, Long>? = null
    /** Адресат, к которому относится [error]: при его удалении ошибка больше не нужна. */
    private var errorKey: Pair<Long, Long>? = null
    private var ledger: ShareBatchLedger? = null
    private var frozenBatches: List<List<OutgoingMedia>> = emptyList()
    private var frozenDraft = ""
    fun addDestination(destination: ShareDestination) {
        if (deliveryStarted) return
        if (selectedDestinations.none { it.chatId == destination.chatId && it.topicId == destination.topicId })
            selectedDestinations = selectedDestinations + destination
    }
    /** Заменяет выбранных адресатов до начала отправки. Список хранится здесь, а не в композиции: переживает поворот. */
    fun replaceDestinations(destinations: List<ShareDestination>) {
        if (!deliveryStarted) selectedDestinations = destinations.distinctBy { it.chatId to it.topicId }
    }
    fun removeDestination(destination: ShareDestination) {
        if (!deliveryStarted) selectedDestinations = selectedDestinations.filterNot {
            it.chatId == destination.chatId && it.topicId == destination.topicId
        }
    }
    var error by mutableStateOf<UiText?>(null)
    /** Подготовка файлов не удалась: её можно повторить на месте, без нового запроса из «Поделиться». */
    var stagingFailed by mutableStateOf(false)
    private var started = false
    var invalidFileCount by mutableStateOf(false)
        private set
    private var released = false
    private var importer: OutgoingMediaImporter? = null

    /** Копирует файлы в приватную папку один раз; вызов после поворота ничего не запускает повторно. */
    fun startStaging(context: Context, scope: CoroutineScope) {
        if (started || released || uris.isEmpty()) return
        started = true
        val staging = OutgoingMediaImporter(context, accountId)
        importer = staging
        scope.launch(mainDispatcher) { stage(staging, context, scope) }
    }

    /** Повторяет копирование после сбоя: файлы уже переданы приложению, поэтому системный «Поделиться» не нужен. */
    fun retryStaging(context: Context, scope: CoroutineScope) {
        if (!stagingFailed || released || deliveryStarted) return
        stagingFailed = false
        error = null
        started = false
        staging = true
        stagingDone = 0
        startStaging(context, scope)
    }

    private suspend fun stage(importer: OutgoingMediaImporter, context: Context, scope: CoroutineScope) {
        stagingDone = 0
        if (uris.size > IncomingShareParser.MAX_FILES) {
            error = UiText.Res(R.string.composer_too_many_attachments)
            invalidFileCount = true
            staging = false
            return
        }
        val items = mutableListOf<OutgoingMedia>()
        try {
            for (uri in uris) {
                // Фото и видео уходят как медиа, остальное как документ.
                if (released) break
                val asDocument = withContext(Dispatchers.IO) { !isPhotoOrVideo(context, uri) }
                items += importer.prepare(uri, asDocument)
                stagingDone++
            }
            // Экран уже закрыли, пока копировали: копии никто не заберёт.
            if (released) discard(importer, scope, items) else staged = items
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) { items.forEach { importer.discard(it) } }
            throw cancelled
        } catch (failure: Exception) {
            discard(importer, scope, items)
            error = UiText.Res(R.string.composer_prepare_failed)
            stagingFailed = true
        } finally {
            staging = false
        }
    }

    /**
     * Отправляет подготовленные файлы или текст в выбранные чаты и темы.
     * Отправка идёт в [scope], переживает поворот экрана и не запускается дважды.
     */
    fun send(
        destinations: List<ShareDestination>,
        scope: CoroutineScope,
        validateDestination: suspend (ShareDestination) -> Result<Unit>,
        sendText: suspend (chatId: Long, topicId: Long, text: String) -> Result<Unit>,
        sendMedia: suspend (chatId: Long, topicId: Long, media: List<OutgoingMedia>, caption: String) -> Result<Unit>
    ) {
        if (sending || sent || retryBlocked || staging || stagingFailed || invalidFileCount || released) return
        if (ledger == null) {
            if (destinations.isEmpty() || (staged.isEmpty() && draft.isBlank())) return
            frozenDraft = draft.trim()
            val album = staged.size in 2..IncomingShareParser.MAX_FILES && staged.all {
                it.kind == MediaKind.Photo || it.kind == MediaKind.Video
            }
            frozenBatches = if (album) listOf(staged) else staged.map { listOf(it) }
            ledger = ShareBatchLedger(destinations, frozenBatches.size.coerceAtLeast(1))
            deliveryStarted = true
            selectedDestinations = ledger!!.destinations
            pendingDestinations = selectedDestinations
        }
        val delivery = checkNotNull(ledger)
        sending = true
        error = null
        errorKey = null
        deliveryNote = null
        scope.launch(mainDispatcher) {
            try {
                for (destination in delivery.pending()) {
                    if (released || retryBlocked) break
                    val key = destination.chatId to destination.topicId
                    val validation = withContext(workDispatcher) { validateDestination(destination) }
                    if (validation.isFailure) {
                        error = (validation.exceptionOrNull() as? UserMessageException)?.text ?: UiText.Res(R.string.share_unavailable_recipient)
                        errorKey = key
                        continue
                    }
                    for (index in delivery.nextBatch(destination) until delivery.batchCount) {
                        if (released) break
                        val result = if (frozenBatches.isEmpty()) {
                            withContext(workDispatcher) { sendText(destination.chatId, destination.topicId, frozenDraft) }
                        } else {
                            val owner = checkNotNull(importer)
                            val copies = mutableListOf<OutgoingMedia>()
                            val preparation = try {
                                for (item in frozenBatches[index]) copies += owner.copyForShareDelivery(item)
                                Result.success(Unit)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                withContext(kotlinx.coroutines.NonCancellable) { copies.forEach { owner.discard(it) } }
                                throw cancelled
                            } catch (failure: Exception) {
                                copies.forEach { owner.discard(it) }
                                Result.failure(failure)
                            }
                            if (preparation.isFailure) preparation
                            else if (released) {
                                copies.forEach { owner.discard(it) }
                                break
                            } else {
                                // From this point TDLib owns these paths, even when its response is uncertain.
                                val sent = withContext(workDispatcher) {
                                    sendMedia(destination.chatId, destination.topicId, copies, if (index == 0) frozenDraft else "")
                                }
                                if (sent.exceptionOrNull() is ShareMediaNotHandedOffException) copies.forEach { owner.discard(it) }
                                sent
                            }
                        }
                        if (result.isFailure) {
                            val failure = result.exceptionOrNull()
                            if (isShareOutcomeUnknown(failure)) {
                                retryBlocked = true
                                uncertainKey = key
                            }
                            val shown = if (failure is ShareMediaNotHandedOffException) failure.cause else failure
                            error = (shown as? UserMessageException)?.text ?: UiText.Res(R.string.share_send_failed)
                            errorKey = key
                            break
                        }
                        delivery.confirm(destination, index)
                        pendingDestinations = delivery.pending()
                    }
                }
                pendingDestinations = delivery.pending()
                if (pendingDestinations.isEmpty()) {
                    val first = delivery.completed().firstOrNull() ?: delivery.destinations.first()
                    sentChatId = first.chatId
                    sentTopicId = first.topicId
                    sent = true
                } else {
                    deliveryNote = progressNote(delivery)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                // Неизвестно, какой адресат был в работе: блокируем повтор целиком.
                retryBlocked = true
                uncertainKey = null
                throw cancelled
            } catch (failure: Exception) {
                retryBlocked = true
                uncertainKey = null
                errorKey = null
                error = UiText.Res(R.string.share_send_failed)
            } finally {
                pendingDestinations = delivery.pending()
                sending = false
                if (released || sent) {
                    val masters = staged
                    staged = emptyList()
                    withContext(kotlinx.coroutines.NonCancellable) { importer?.let { owner -> masters.forEach { owner.discard(it) } } }
                }
            }
        }
    }

    /** Можно ли убрать адресата: отправка уже шла и сейчас стоит, адресатов больше одного, и хоть кому-то что-то ушло. */
    private fun canDrop(delivery: ShareBatchLedger, destination: ShareDestination): Boolean {
        val isPending = delivery.pending().any { it.chatId == destination.chatId && it.topicId == destination.topicId }
        // Последнего неотправленного нельзя убрать, если никому ничего не ушло: отправка закончилась бы ничем.
        return deliveryStarted && !sending && !sent && !released && delivery.destinations.size > 1 && isPending &&
            (delivery.completed().isNotEmpty() || delivery.pending().size > 1)
    }

    /** Есть ли адресат, которого можно убрать из неотправленных (показываем подсказку на экране). */
    val canDropPending: Boolean
        get() = pendingDestinations.isNotEmpty() && ledger?.let { delivery -> delivery.pending().any { canDrop(delivery, it) } } == true

    /**
     * Убирает неотправленного адресата, чтобы ему больше не слать. Возвращает true, если адресат убран.
     * Если так все адресаты отправлены, сессия завершается как успешная.
     */
    fun dropPending(destination: ShareDestination, scope: CoroutineScope): Boolean {
        val delivery = ledger ?: return false
        if (!canDrop(delivery, destination)) return false
        val key = destination.chatId to destination.topicId
        delivery.drop(destination)
        if (uncertainKey == key) {
            retryBlocked = false
            uncertainKey = null
        }
        if (errorKey == key) {
            error = null
            errorKey = null
        }
        pendingDestinations = delivery.pending()
        if (pendingDestinations.isEmpty()) {
            val first = delivery.completed().first()
            sentChatId = first.chatId
            sentTopicId = first.topicId
            error = null
            deliveryNote = null
            sent = true
            discardStaged(scope)
        } else {
            deliveryNote = progressNote(delivery)
        }
        return true
    }

    private fun progressNote(delivery: ShareBatchLedger): UiText? =
        if (delivery.confirmedCount() > 0) UiText.Res(R.string.share_delivery_progress, listOf(delivery.completedCount(), delivery.destinations.size))
        else null

    /** Копии для отправки больше не нужны: удаляем их так же, как после успешной отправки. */
    private fun discardStaged(scope: CoroutineScope) {
        val masters = staged
        staged = emptyList()
        val owner = importer ?: return
        scope.launch(NonCancellable) { masters.forEach { owner.discard(it) } }
    }

    fun removeStaged(item: OutgoingMedia, importer: OutgoingMediaImporter, scope: CoroutineScope) {
        if (sending || retryBlocked || deliveryStarted) return
        staged = staged.filterNot { it.path == item.path }
        scope.launch { importer.discard(item) }
    }

    /** Экран закрыт без отправки: непосланные копии удаляем, отправку, которая идёт, не трогаем. */
    fun release(context: Context, scope: CoroutineScope) {
        released = true
        if (sending) return
        discard(OutgoingMediaImporter(context, accountId), scope, staged)
        staged = emptyList()
    }

    private fun discard(importer: OutgoingMediaImporter, scope: CoroutineScope, items: List<OutgoingMedia>) {
        scope.launch { items.forEach { importer.discard(it) } }
    }

    private companion object {
        fun isPhotoOrVideo(context: Context, uri: Uri): Boolean {
            val mime = context.contentResolver.getType(uri).orEmpty()
            return mime.startsWith("image/") || mime.startsWith("video/")
        }
    }
}

/** Единственный активный запрос «Поделиться»: держит его состояние между поворотами экрана. */
internal object ShareSessions {
    private var current: ShareSession? = null
    // All mutations are invoked by the UI; asynchronous staging and send also resume on Main.

    fun hasSession(requestId: Long): Boolean = current?.requestId == requestId

    fun deliveryAccount(requestId: Long): String? = current?.takeIf { it.requestId == requestId && it.deliveryStarted }?.accountId

    fun of(context: Context, requestId: Long, uris: List<Uri>, text: String, accountId: String = "legacy"): ShareSession {
        current?.takeIf { it.requestId == requestId && it.accountId == accountId }?.let { return it }
        // Пришёл новый запрос: копии старого, если он не отправлен, больше не нужны.
        current?.release(context.applicationContext, scopeOf(context))
        return ShareSession(requestId, uris, text, accountId).also { current = it }
    }

    fun release(context: Context, requestId: Long) {
        val session = current?.takeIf { it.requestId == requestId } ?: return
        current = null
        session.release(context.applicationContext, scopeOf(context))
    }

    private fun scopeOf(context: Context) = (context.applicationContext as com.beykus.beygram.BeyGramApplication).applicationScope
}
