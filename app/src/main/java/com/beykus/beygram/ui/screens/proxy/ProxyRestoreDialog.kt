package com.beykus.beygram.ui.screens.proxy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.ui.components.BeyGramDialog
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
    BeyGramDialog(
        onDismissRequest = {},
        title = stringResource(R.string.proxy_list_title),
        message = stringResource(
            if (restoreRetrying) R.string.proxy_restore_retrying else R.string.proxy_restore_failed_question
        ),
        confirmText = stringResource(R.string.proxy_restore_retry),
        onConfirm = { scope.launch { vpnBackend.retryRestore() } },
        confirmEnabled = !restoreRetrying,
        dismissText = stringResource(R.string.proxy_restore_turn_off),
        onDismiss = { scope.launch { vpnBackend.stopProxy() } },
        dismissOnBackPress = false,
        dismissOnClickOutside = false
    )
}
