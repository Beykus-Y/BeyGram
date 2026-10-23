package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Shown only when the selected account has no usable full repository. It is a
 * recovery state, not a second account-management flow: the shared switcher
 * remains the only place where accounts are selected or modified.
 */
@Composable
internal fun AccountRecoveryScreen(
    accountTitle: String?,
    accountLabel: String,
    busy: Boolean,
    errorText: String?,
    onRetry: () -> Unit,
    onChooseAccount: () -> Unit,
    onConfigureRoute: () -> Unit,
    onPrimary: () -> Unit
) {
    val palette = beyGramPalette()
    Box(
        Modifier.fillMaxSize().background(palette.screen).safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(palette.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = palette.warningText,
                modifier = Modifier.size(36.dp)
            )
            Text(
                stringResource(R.string.account_recovery_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                accountTitle ?: accountLabel,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.account_recovery_message),
                color = palette.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.account_recovery_busy),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            if (errorText != null) {
                Text(
                    errorText,
                    color = palette.danger,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = onRetry,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.account_recovery_retry)) }
            OutlinedButton(
                onClick = onChooseAccount,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.account_recovery_choose)) }
            TextButton(onClick = onConfigureRoute, enabled = !busy) {
                Text(stringResource(R.string.account_recovery_configure))
            }
            TextButton(onClick = onPrimary, enabled = !busy) {
                Text(stringResource(R.string.account_recovery_primary))
            }
        }
    }
}
