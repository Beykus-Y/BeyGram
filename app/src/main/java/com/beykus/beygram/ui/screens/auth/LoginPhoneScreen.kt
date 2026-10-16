package com.beykus.beygram.ui.screens.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.beykus.beygram.ui.components.ProxySettingsDialog
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.AuthScaffold
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors

@Composable
fun LoginPhoneScreen(
    authState: AuthState,
    connectionState: TelegramConnectionState,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    onReconnect: () -> Unit,
    onAddProxy: (String, Int, Boolean, String, String, String) -> Unit
) {
    var phone by rememberSaveable { mutableStateOf("+7 ") }
    val isLoading = authState is AuthState.Loading
    val errorMessage = when (authState) {
        is AuthState.Error -> authState.message
        AuthState.ConnectionTimeout -> "Telegram пока не ответил. Запрос остаётся активным и продолжится после восстановления связи."
        else -> null
    }
    var showProxyDialog by remember { mutableStateOf(false) }
    val requestWaitingForNetwork = authState == AuthState.ConnectionTimeout
    val canContinue = phone.count { it.isDigit() } >= 11 && !isLoading && !requestWaitingForNetwork
    val palette = beyGramPalette()

    AuthScaffold(
        title = "Номер телефона",
        subtitle = "Введите номер, к которому привязан Telegram.",
        onBack = onBack
    ) {
        Text(
            text = when (connectionState) {
                TelegramConnectionState.Connected -> "Соединение с Telegram установлено"
                TelegramConnectionState.Connecting -> "Подключаемся к Telegram…"
                TelegramConnectionState.Updating -> "Восстанавливаем соединение…"
                TelegramConnectionState.WaitingForNetwork -> "Нет доступной сети"
                TelegramConnectionState.Unknown -> "Проверяем соединение…"
            },
            color = if (connectionState == TelegramConnectionState.Connected) palette.successText else palette.textSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { value ->
                phone = value
                    .filter { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
                    .take(24)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Телефон") },
            placeholder = { Text("+7 999 123-45-67") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(text = errorMessage, color = palette.warningText) }
            } else null,
            colors = beyGramTextFieldColors()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val digits = phone.filter(Char::isDigit)
                onContinue("+$digits")
            },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.telegramBlue,
                contentColor = Color.White,
                disabledContainerColor = palette.disabled,
                disabledContentColor = palette.textSecondary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = if (requestWaitingForNetwork) "Ожидаем ответ Telegram…" else "Получить код",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                )
            }
        }

        TextButton(onClick = { showProxyDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Не подключается? Настроить прокси")
        }
        if (requestWaitingForNetwork) {
            TextButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Переподключиться")
            }
        }
    }

    if (showProxyDialog) ProxySettingsDialog(
        onDismiss = { showProxyDialog = false },
        onApply = onAddProxy
    )
}
