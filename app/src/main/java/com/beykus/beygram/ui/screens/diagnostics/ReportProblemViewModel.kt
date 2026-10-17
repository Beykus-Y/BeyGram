package com.beykus.beygram.ui.screens.diagnostics

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.diagnostics.StructuredEventLog
import com.beykus.beygram.diagnostics.report.DiagnosticsApiClient
import com.beykus.beygram.diagnostics.report.DiagnosticsApiConfig
import com.beykus.beygram.diagnostics.report.DiagnosticsReportCoordinator
import com.beykus.beygram.diagnostics.report.ReportClientMeta
import com.beykus.beygram.diagnostics.report.ReportDraftStore
import com.beykus.beygram.diagnostics.report.ReportSnapshot
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.Dispatchers
import java.util.Locale

/**
 * Android-обвязка координатора: публичная конфигурация сборки, TDLib-канал,
 * API-клиент через текущий сетевой путь и хранилище черновика.
 */
class ReportProblemViewModel(
    repository: TelegramRepository,
    appContext: Context
) : ViewModel() {

    val config = DiagnosticsApiConfig(
        baseUrl = BuildConfig.DIAGNOSTICS_API_BASE_URL,
        botUsername = BuildConfig.REPORT_BOT_USERNAME,
        botUserId = BuildConfig.REPORT_BOT_USER_ID
    )

    val coordinator = DiagnosticsReportCoordinator(
        scope = viewModelScope,
        config = config,
        botChannel = repository,
        apiClient = DiagnosticsApiClient(
            // Активный локальный Mihomo-прокси не обходится: запрос идёт тем же путём, что и пользователь ожидает.
            proxyProvider = { MihomoProxyClient.currentProxy() }
        ),
        draftStore = ReportDraftStore(appContext.filesDir),
        ioDispatcher = Dispatchers.IO,
        eventsProvider = { ReportSnapshot.collectEvents(StructuredEventLog.shared) },
        overflowedProvider = { StructuredEventLog.shared.hasOverflowed() },
        metaProvider = {
            ReportClientMeta(
                appVersion = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                platform = "android",
                osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                locale = Locale.getDefault().toString(),
                vpnActive = MihomoProxyClient.isRunning(),
                proxyEnabled = repository.isMihomoProxyEnabled().getOrNull() == true,
                eventCountTotal = null,
                bufferOverflowed = null
            )
        },
        telegramReadyProvider = { repository.authState.value is AuthState.Ready }
    )
}
