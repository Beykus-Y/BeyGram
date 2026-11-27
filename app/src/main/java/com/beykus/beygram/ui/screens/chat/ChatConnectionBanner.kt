package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.chats.ChatsConnectionStatus
import com.beykus.beygram.ui.screens.chats.labelResource
import com.beykus.beygram.ui.screens.chats.rememberChatsConnectionStatus

/**
 * Строка состояния сессии Telegram над лентой переписки. Использует тот же трекер, что и шапка
 * списка чатов: переходные состояния TDLib показываются только после удержания около секунды,
 * а возврат к норме убирает строку сразу. Так короткие переподключения не мигают поверх сообщений.
 *
 * Это состояние именно сессии Telegram, а не наличие сети в Android, VPN или прокси. История
 * остаётся на экране: баннер только сообщает, что сообщения подождут или догрузятся.
 */
@Composable
internal fun ChatConnectionBanner(accountId: String, connection: TelegramConnectionState) {
    val status = rememberChatsConnectionStatus(accountId, connection)
    val motion = rememberMotionSpec(MotionToken.BannerChange)
    // При исчезновении строки держим последний текст, чтобы анимация скрытия не показывала пустоту.
    var retainedStatus by remember(accountId) { mutableStateOf(ChatsConnectionStatus.None) }
    SideEffect { if (status != ChatsConnectionStatus.None) retainedStatus = status }
    val shown = if (status != ChatsConnectionStatus.None) status else retainedStatus
    AnimatedVisibility(
        visible = status != ChatsConnectionStatus.None,
        enter = if (motion.isInstant) EnterTransition.None else
            expandVertically(expandFrom = Alignment.Top, animationSpec = motion.animationSpec()) + motion.fadeEnter(),
        exit = if (motion.isInstant) ExitTransition.None else
            shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = motion.animationSpec()) + motion.fadeExit()
    ) {
        ConnectionBannerRow(shown)
    }
}

@Composable
private fun ConnectionBannerRow(status: ChatsConnectionStatus) {
    if (status == ChatsConnectionStatus.None) return
    val palette = beyGramPalette()
    // Ожидание сети в чате говорит, что сообщения уйдут после подключения, поэтому текст свой.
    val warning = status == ChatsConnectionStatus.WaitingForNetwork
    val label = stringResource(if (warning) R.string.chat_connection_waiting_network else status.labelResource())
    val spokenLabel = stringResource(R.string.chats_connection_a11y, label)
    // Ожидание сети — предупреждение; остальные состояния нейтральны и не выглядят как ошибка.
    val onGlassChrome = LocalChatChromeOnGlass.current
    val background = when {
        onGlassChrome && warning -> palette.warningSurface.copy(alpha = 0.65f)
        onGlassChrome -> Color.Transparent
        warning -> palette.warningSurface
        else -> palette.surface2
    }
    val color = if (warning) palette.warningText else palette.textSecondary
    val icon = when (status) {
        ChatsConnectionStatus.WaitingForNetwork -> Icons.Rounded.WifiOff
        ChatsConnectionStatus.Connecting -> Icons.Rounded.Schedule
        else -> Icons.Rounded.Refresh
    }
    Row(
        Modifier.fillMaxWidth().background(background).semantics(mergeDescendants = true) {
            // Строка появляется без действия пользователя: сообщаем её, не уводя фокус с сообщений.
            liveRegion = LiveRegionMode.Polite
            contentDescription = spokenLabel
        }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
