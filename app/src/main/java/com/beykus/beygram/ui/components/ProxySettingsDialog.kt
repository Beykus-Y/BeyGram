package com.beykus.beygram.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProxySettingsDialog(
    onDismiss: () -> Unit,
    onApply: (server: String, port: Int, isMtproto: Boolean, username: String, password: String, secret: String) -> Unit
) {
    var isMtproto by remember { mutableStateOf(true) }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var secret by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = server.isNotBlank() && (port.toIntOrNull() in 1..65535) &&
        if (isMtproto) secret.isNotBlank() else true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подключение к Telegram") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Введите данные прокси. Их можно получить у провайдера или администратора сервера.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isMtproto, onClick = { isMtproto = true })
                    Text("MTProto")
                    RadioButton(selected = !isMtproto, onClick = { isMtproto = false })
                    Text("SOCKS5")
                }
                OutlinedTextField(server, { server = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Сервер") }, singleLine = true)
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("Порт") }, singleLine = true)
                if (isMtproto) {
                    OutlinedTextField(secret, { secret = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Секрет") }, singleLine = true)
                } else {
                    OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Логин (если нужен)") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Пароль (если нужен)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(server.trim(), port.toInt(), isMtproto, username, password, secret); onDismiss() },
                enabled = valid
            ) { Text("Подключить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
