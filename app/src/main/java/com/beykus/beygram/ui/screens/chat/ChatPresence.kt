package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.ProfilePresence
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.delay

/** Строка под названием чата: статус собеседника или число участников онлайн. */
internal fun chatPresenceLine(chat: ChatPreview, info: ProfileInfo?, lastSeenStyle: LastSeenStyle, nowMillis: Long): UiText = when {
    // Saved Messages is the user's own storage chat, not another Telegram user.
    // Never display self-presence ("online"/last seen) below its title.
    chat.isSavedMessages -> UiText.Res(R.string.presence_saved_messages)
    chat.isGroup -> info?.onlineMemberCount?.takeIf { it > 0 }?.let { UiText.Res(R.string.presence_online_count, listOf(it)) } ?: UiText.Res(R.string.presence_group)
    else -> info?.status?.let { presenceText(it, lastSeenStyle, nowMillis) } ?: UiText.Res(R.string.presence_private_chat)
}

/** Статус собеседника из TDLib: текст выбирается по виду статуса, а не по строке от TDLib. */
internal fun presenceText(status: ProfilePresence, lastSeenStyle: LastSeenStyle, nowMillis: Long): UiText = when (status) {
    ProfilePresence.Bot -> UiText.Res(R.string.presence_bot)
    ProfilePresence.DeletedAccount -> UiText.Res(R.string.presence_deleted_account)
    ProfilePresence.Online -> UiText.Res(R.string.presence_online)
    is ProfilePresence.LastSeen -> lastSeenText(status.wasOnlineMillis, nowMillis, lastSeenStyle)
    ProfilePresence.Recently -> UiText.Res(R.string.presence_recently)
    ProfilePresence.LastWeek -> UiText.Res(R.string.presence_last_week)
    ProfilePresence.LastMonth -> UiText.Res(R.string.presence_last_month)
    ProfilePresence.LongAgo -> UiText.Res(R.string.presence_long_ago)
}

private const val PRESENCE_REFRESH_MS = 30_000L

/**
 * Текущее время для строки статуса. Пока статус — время последнего входа, оно пересчитывается раз в 30 секунд,
 * иначе «5 минут назад» застыло бы в шапке. Для остальных статусов ничего не тикает.
 */
@Composable
internal fun rememberPresenceClock(lastSeen: Boolean): State<Long> = produceState(System.currentTimeMillis(), lastSeen) {
    while (lastSeen) {
        value = System.currentTimeMillis()
        delay(PRESENCE_REFRESH_MS)
    }
}

/** «Анна и Дима печатают…»: имена в порядке начала набора; при четырёх и больше остальные сворачиваются в «и ещё N». */
internal fun typingLabel(names: List<String>): UiText = when (names.size) {
    0 -> UiText.Raw("")
    1 -> UiText.Res(R.string.presence_typing_one, listOf(names[0]))
    2 -> UiText.Res(R.string.presence_typing_two, listOf(names[0], names[1]))
    3 -> UiText.Res(R.string.presence_typing_three, listOf(names[0], names[1], names[2]))
    else -> UiText.Res(R.string.presence_typing_more, listOf(names[0], names[1], names.size - 2))
}

@Composable
internal fun TypingRow(names: List<String>) {
    val palette = beyGramPalette()
    // Пустое имя значит, что имя неизвестно. UiText.Res не разворачивает вложенный UiText, поэтому подставляем его здесь.
    val someone = stringResource(R.string.presence_someone)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(typingLabel(names.map { it.ifBlank { someone } }).resolve(), color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        TypingDots(palette.accent)
    }
}

@Composable
private fun TypingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot$index"
            )
            Box(Modifier.size(5.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        }
    }
}
