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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.ui.components.beyGramPalette

/** Строка под названием чата: статус собеседника или число участников онлайн. */
internal fun chatPresenceLine(chat: ChatPreview, info: ProfileInfo?): String = when {
    chat.isGroup -> info?.onlineMemberCount?.takeIf { it > 0 }?.let { "$it в сети" } ?: "Группа"
    else -> info?.status?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.lowercaseChar() } ?: "Личный чат"
}

/** «Анна и Дима печатают…»: имена в порядке начала набора; при четырёх и больше остальные сворачиваются в «и ещё N». */
internal fun typingLabel(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> "${names[0]} печатает…"
    2 -> "${names[0]} и ${names[1]} печатают…"
    3 -> "${names[0]}, ${names[1]} и ${names[2]} печатают…"
    else -> "${names[0]}, ${names[1]} и ещё ${names.size - 2} печатают…"
}

@Composable
internal fun TypingRow(names: List<String>) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(typingLabel(names), color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
