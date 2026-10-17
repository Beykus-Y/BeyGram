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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdLibReadiness
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve

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
                text = stringResource(R.string.app_name),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDarkTheme) stringResource(R.string.start_theme_dark) else stringResource(R.string.start_theme_light),
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
                text = stringResource(R.string.app_name),
                color = palette.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.start_subtitle),
                color = palette.textSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            TdLibStatusPanel(readiness = tdLibReadiness)
            Spacer(modifier = Modifier.height(10.dp))
            val (connectionText, connectionColor) = when (connectionState) {
                TelegramConnectionState.Connected -> stringResource(R.string.start_connection_connected) to palette.successText
                TelegramConnectionState.WaitingForNetwork -> stringResource(R.string.start_connection_no_network) to palette.warningText
                TelegramConnectionState.Connecting -> stringResource(R.string.auth_status_connecting) to palette.warningText
                TelegramConnectionState.Updating -> stringResource(R.string.start_connection_updating) to palette.warningText
                TelegramConnectionState.Unknown -> stringResource(R.string.start_connection_checking) to palette.textSecondary
            }
            if (tdLibReadiness.canCreateClient) Text(connectionText, color = connectionColor, fontSize = 13.sp)
            TextButton(onClick = onProxySettings) { Text(stringResource(R.string.start_proxy_settings)) }
        }

        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = tdLibReadiness.canCreateClient,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent
            ),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_sign_in),
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
                text = if (isReady) stringResource(R.string.start_ready_to_sign_in) else stringResource(R.string.start_sign_in_unavailable),
                color = if (isReady) palette.successText else palette.warningText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isReady) {
                    stringResource(R.string.start_ready_hint)
                } else {
                    readiness.issues.map { it.resolve() }.joinToString(separator = "\n") { "- $it" }
                },
                color = if (isReady) palette.successText else palette.warningText,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                letterSpacing = 0.sp
            )
        }
    }
}
