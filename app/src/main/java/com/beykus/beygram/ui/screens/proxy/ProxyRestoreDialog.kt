package com.beykus.beygram.ui.screens.proxy

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.vpn.VpnBackend
import kotlinx.coroutines.launch

/**
 * Вопрос, когда прокси не запустился после автоматических попыток: повторить или выключить прокси.
 * Окно нельзя закрыть без выбора, потому что Telegram не переключается на прямое соединение без решения пользователя.
 * Пока идут повторы по кнопке «Повторить», вместо вопроса показывается подсказка.
 */
@Composable
internal fun ProxyRestoreDialog(vpnBackend: VpnBackend) {
    val restoreFailed by vpnBackend.restoreFailed.collectAsStateWithLifecycle()
    val restoreRetrying by vpnBackend.restoreRetrying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (!restoreFailed && !restoreRetrying) return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = {
            Text(
                stringResource(
                    if (restoreRetrying) R.string.proxy_restore_retrying else R.string.proxy_restore_failed_question
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { scope.launch { vpnBackend.retryRestore() } },
                enabled = !restoreRetrying
            ) {
                Text(stringResource(R.string.proxy_restore_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = { scope.launch { vpnBackend.stopProxy() } }) {
                Text(stringResource(R.string.proxy_restore_turn_off))
            }
        }
    )
}
