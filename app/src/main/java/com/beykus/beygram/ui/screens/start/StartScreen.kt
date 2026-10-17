package com.beykus.beygram.ui.screens.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.tdlib.TdLibReadiness
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.beyGramPalette

@Composable
fun StartScreen(
    isDarkTheme: Boolean,
    tdLibReadiness: TdLibReadiness,
    connectionState: TelegramConnectionState,
    onThemeChange: (Boolean) -> Unit,
    onProxySettings: () -> Unit,
    onStartClick: () -> Unit
) {
    val palette = beyGramPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BeyGram",
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDarkTheme) "Темная" else "Светлая",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp
                )

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onThemeChange
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BG",
                    color = palette.onAccent,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "BeyGram",
                color = palette.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Клиент Telegram",
                color = palette.textSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            TdLibStatusPanel(readiness = tdLibReadiness)
            Spacer(modifier = Modifier.height(10.dp))
            val (connectionText, connectionColor) = when (connectionState) {
                TelegramConnectionState.Connected -> "Связь с Telegram установлена" to palette.successText
                TelegramConnectionState.WaitingForNetwork -> "Нет подключения к сети" to palette.warningText
                TelegramConnectionState.Connecting -> "Подключаемся к Telegram…" to palette.warningText
                TelegramConnectionState.Updating -> "Восстанавливаем связь…" to palette.warningText
                TelegramConnectionState.Unknown -> "Проверяем доступность Telegram…" to palette.textSecondary
            }
            Text(connectionText, color = connectionColor, fontSize = 13.sp)
            TextButton(onClick = onProxySettings) { Text("Прокси для Telegram") }
        }

        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent
            ),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(
                text = if (tdLibReadiness.canCreateClient) "Войти" else "Продолжить в демо-режиме",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun TdLibStatusPanel(
    readiness: TdLibReadiness
) {
    val palette = beyGramPalette()
    val isReady = readiness.canCreateClient

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isReady) palette.successSurface else palette.warningSurface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = if (isReady) "Клиент готов" else "Клиент не готов",
                color = if (isReady) palette.successText else palette.warningText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isReady) {
                    "Можно тестировать реальную авторизацию."
                } else {
                    readiness.issues.joinToString(separator = "\n") { "- $it" }
                },
                color = if (isReady) palette.successText else palette.warningText,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                letterSpacing = 0.sp
            )
        }
    }
}
