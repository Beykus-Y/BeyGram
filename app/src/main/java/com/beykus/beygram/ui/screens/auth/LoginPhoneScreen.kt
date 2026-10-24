package com.beykus.beygram.ui.screens.auth

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.AuthScaffold
import com.beykus.beygram.ui.components.TelegramQrCode
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.launch

@Composable
fun LoginPhoneScreen(
    authState: AuthState,
    connectionState: TelegramConnectionState,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    onReconnect: () -> Unit,
    onProxySettings: () -> Unit,
    onQrLogin: () -> Unit,
    onCancelQrLogin: () -> Unit
) {
    var phone by rememberSaveable { mutableStateOf("+7 ") }
    var qrMode by rememberSaveable { mutableStateOf(false) }
    val isLoading = authState is AuthState.Loading
    val errorMessage = when (authState) {
        is AuthState.Error -> authState.text.resolve()
        AuthState.ConnectionTimeout -> stringResource(R.string.auth_phone_connection_timeout)
        else -> null
    }
    val requestWaitingForNetwork = authState == AuthState.ConnectionTimeout
    val canContinue = phone.count { it.isDigit() } >= 11 && !isLoading && !requestWaitingForNetwork
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharingLogs by remember { mutableStateOf(false) }
    var shareLogsError by remember { mutableStateOf<String?>(null) }

    // TDLib owns the QR flow. If a restored/refreshed authorization state arrives while this screen
    // is visible, keep the QR panel in sync instead of showing the phone fields over an active QR login.
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.WaitingForQr -> qrMode = true
            AuthState.WaitingForPhone -> qrMode = false
            else -> Unit
        }
    }

    val leaveQr: () -> Unit = {
        qrMode = false
        onCancelQrLogin()
    }
    BackHandler(enabled = qrMode) { leaveQr() }

    AuthScaffold(
        title = stringResource(if (qrMode) R.string.auth_qr_title else R.string.auth_phone_title),
        subtitle = stringResource(if (qrMode) R.string.auth_qr_subtitle else R.string.auth_phone_subtitle),
        onBack = {
            if (qrMode) leaveQr() else onBack()
        }
    ) {
        LoginModeTabs(
            qrSelected = qrMode,
            qrEnabled = qrMode || !isLoading,
            onPhone = { if (qrMode) leaveQr() },
            onQr = {
                if (!qrMode) {
                    qrMode = true
                    onQrLogin()
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (qrMode) {
            QrLoginPanel(
                authState = authState,
                onRetry = onQrLogin,
                onCancel = leaveQr
            )
        } else {
            Text(
                text = when (connectionState) {
                    TelegramConnectionState.Connected -> stringResource(R.string.auth_phone_status_connected)
                    TelegramConnectionState.Connecting -> stringResource(R.string.auth_status_connecting)
                    TelegramConnectionState.Updating -> stringResource(R.string.auth_phone_status_updating)
                    TelegramConnectionState.WaitingForNetwork -> stringResource(R.string.auth_phone_status_no_network)
                    TelegramConnectionState.Unknown -> stringResource(R.string.auth_phone_status_unknown)
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
                label = { Text(stringResource(R.string.auth_phone_label)) },
                placeholder = { Text("+7 999 123-45-67") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                isError = errorMessage != null,
                supportingText = if (errorMessage != null) {
                    { Text(text = errorMessage, color = palette.danger) }
                } else null,
                shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.disabled,
                    disabledContentColor = palette.textSecondary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = palette.onAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = if (requestWaitingForNetwork) stringResource(R.string.auth_phone_waiting_response) else stringResource(R.string.auth_phone_get_code),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onProxySettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_phone_proxy_help))
        }
        if (!qrMode && requestWaitingForNetwork) {
            TextButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_phone_reconnect))
            }
        }
        // Доступно до входа: отчёт не содержит введённый номер (см. DiagnosticsRedactor).
        TextButton(
            enabled = !sharingLogs,
            onClick = {
                scope.launch {
                    sharingLogs = true; shareLogsError = null
                    try { shareLogsError = shareConnectionDiagnosticsOrError(context, connectionState) }
                    finally { sharingLogs = false }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sharingLogs) stringResource(R.string.auth_share_logs_progress) else stringResource(R.string.auth_share_logs))
        }
        shareLogsError?.let { Text(text = it, color = palette.danger, fontSize = 13.sp) }
    }
}

@Composable
private fun LoginModeTabs(
    qrSelected: Boolean,
    qrEnabled: Boolean,
    onPhone: () -> Unit,
    onQr: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LoginModeButton(
            text = stringResource(R.string.auth_phone_mode_phone),
            selected = !qrSelected,
            enabled = true,
            onClick = onPhone
        )
        LoginModeButton(
            text = stringResource(R.string.auth_phone_mode_qr),
            selected = qrSelected,
            enabled = qrEnabled,
            onClick = onQr
        )
    }
}

@Composable
private fun RowScope.LoginModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(42.dp),
        shape = RoundedCornerShape(11.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) palette.accent else Color.Transparent,
            contentColor = if (selected) palette.onAccent else palette.textSecondary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = palette.textSecondary
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QrLoginPanel(
    authState: AuthState,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = beyGramPalette()
    val link = (authState as? AuthState.WaitingForQr)?.link
    val errorText = when (authState) {
        is AuthState.Error -> authState.text.resolve()
        AuthState.ConnectionTimeout -> stringResource(R.string.auth_qr_timeout)
        else -> null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            link != null -> {
                QrCodeView(link)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.auth_qr_waiting),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.auth_qr_auto_refresh),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                CopyQrLinkButton(link)
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auth_qr_cancel))
                }
            }
            errorText != null -> {
                Text(
                    text = errorText,
                    color = palette.danger,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.onAccent
                    )
                ) {
                    Text(stringResource(R.string.auth_qr_retry), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auth_qr_cancel))
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.auth_qr_requesting),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auth_qr_cancel))
                }
            }
        }
    }
}

@Composable
private fun QrCodeView(link: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(236.dp)
                .background(Color.White, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            TelegramQrCode(
                content = link,
                size = 216.dp,
                contentDescription = stringResource(R.string.auth_qr_code_description)
            )
        }
    }
}

@Composable
private fun CopyQrLinkButton(link: String) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    var copied by remember(link) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = link,
                color = beyGramPalette().textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        TextButton(
            onClick = {
                runCatching {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("BeyGram Telegram QR", link))
                }
                copied = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (copied) stringResource(R.string.auth_qr_copied) else stringResource(R.string.auth_qr_copy_link))
        }
    }
}
