package com.beykus.beygram.ui.screens.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.delay

/**
 * Сколько должно продержаться переходное состояние TDLib, прежде чем шапка о нём напишет.
 *
 * Холодный старт и переподключение проходят через `Connecting → Updating → Connected` за доли
 * секунды. Если показывать каждую ступень, заголовок моргает; короткая пауза это скрывает,
 * а реальное ожидание сети или долгую синхронизацию всё равно видно.
 */
internal const val ChatsConnectionStatusHoldMillis = 1_000L

/**
 * Строка под заголовком главного экрана. [None] оставляет обычное название раздела: сессия
 * работает, либо TDLib ещё не сообщил состояние, о котором можно честно написать.
 */
internal enum class ChatsConnectionStatus {
    None,
    WaitingForNetwork,
    Connecting,
    Updating
}

/**
 * Переводит состояние TDLib в строку шапки. Это состояние именно сессии Telegram, а не наличие
 * интернета в Android и не статус VPN/Mihomo, поэтому прокси и reachability сюда не подмешиваются.
 */
internal fun TelegramConnectionState.toChatsConnectionStatus(): ChatsConnectionStatus = when (this) {
    TelegramConnectionState.WaitingForNetwork -> ChatsConnectionStatus.WaitingForNetwork
    TelegramConnectionState.Connecting -> ChatsConnectionStatus.Connecting
    TelegramConnectionState.Updating -> ChatsConnectionStatus.Updating
    // Connected не нуждается в пояснении, а Unknown не даёт права утверждать что-либо о связи.
    TelegramConnectionState.Connected,
    TelegramConnectionState.Unknown -> ChatsConnectionStatus.None
}

/**
 * Следит за состоянием одного аккаунта и публикует то, что должна показать шапка.
 *
 * [follow] приостанавливается, пока переходный статус выдерживает [holdMillis]. Отмена вызывающей
 * корутины (новое состояние, другой аккаунт, уход с экрана) выбрасывает отложенный статус, поэтому
 * запоздавшее «Подключение…» не может достаться другому аккаунту или появиться после reconnect.
 * Переключение на другой аккаунт сразу убирает строку предыдущего.
 */
internal class ChatsConnectionStatusTracker(
    private val holdMillis: Long = ChatsConnectionStatusHoldMillis
) {
    private var accountId: String? = null

    suspend fun follow(
        accountId: String,
        connection: TelegramConnectionState,
        publish: (ChatsConnectionStatus) -> Unit
    ) {
        val switchedAccount = this.accountId != accountId
        this.accountId = accountId
        if (switchedAccount) publish(ChatsConnectionStatus.None)
        val status = connection.toChatsConnectionStatus()
        // Возврат к обычному заголовку не ждёт: пользователь должен увидеть восстановление сразу.
        if (status != ChatsConnectionStatus.None) delay(holdMillis)
        publish(status)
    }
}

/**
 * Статус подключения активного аккаунта для шапки списка чатов.
 *
 * [accountId] — ключ аккаунта: его смена перезапускает ожидание и не оставляет строку прежнего
 * аккаунта. [connection] приходит из репозитория выбранного аккаунта, поэтому переключение
 * аккаунтов учитывается автоматически.
 */
@Composable
internal fun rememberChatsConnectionStatus(
    accountId: String,
    connection: TelegramConnectionState
): ChatsConnectionStatus {
    val tracker = remember { ChatsConnectionStatusTracker() }
    // Ключ по аккаунту убирает строку прежнего аккаунта уже в кадре переключения, не ожидая эффекта.
    var status by remember(accountId) { mutableStateOf(ChatsConnectionStatus.None) }
    // Новый ключ отменяет предыдущее ожидание: быстрые переходы TDLib не доходят до шапки,
    // а смена аккаунта очищает строку прежнего аккаунта до описания нового.
    LaunchedEffect(tracker, accountId, connection) {
        tracker.follow(accountId, connection) { status = it }
    }
    return status
}
