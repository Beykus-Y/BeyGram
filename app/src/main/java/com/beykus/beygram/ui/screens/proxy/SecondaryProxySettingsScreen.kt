package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.BeyGramDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdSecondaryAccountController
import com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.settingsIconTint
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import com.beykus.beygram.ui.screens.settings.SettingsRowInset

/**
 * Secondary TDLib routing is owned by the process-wide native session
 * coordinator. Never pass the legacy VpnBackend into a secondary proxy editor.
 * Текущий маршрут аккаунта показан карточкой, выбор маршрута строками с кружками, как на экране прокси.
 */
@Composable
internal fun SecondaryProxySettingsScreen(
    accountId: String,
    repository: TelegramRepository,
    controller: TdSecondaryAccountController,
    onManage: () -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val accounts by controller.coordinator.snapshot.collectAsState()
    val row = accounts.accounts.firstOrNull { it.accountId == accountId }
    var route by remember(accountId, repository) { mutableStateOf<TelegramRoute?>(null) }
    var failed by remember(accountId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var changing by remember(accountId) { mutableStateOf(false) }
    var changeFailed by remember(accountId) { mutableStateOf(false) }
    var desiredRoute by remember(accountId) { mutableStateOf<TdSecondaryRoutePreference?>(null) }
    LaunchedEffect(accountId, repository, row?.networkSuspended) {
        val result = repository.currentRoute()
        route = result.getOrNull()
        failed = result.isFailure
    }
    val routeText = when (route) {
        TelegramRoute.Direct -> stringResource(R.string.multi_proxy_direct)
        TelegramRoute.Subscription -> stringResource(R.string.multi_proxy_mihomo)
        is TelegramRoute.Proxy -> stringResource(R.string.multi_proxy_manual)
        null -> if (failed) stringResource(R.string.multi_proxy_unknown) else stringResource(R.string.multi_proxy_checking)
    }
    Column(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).safeDrawingPadding()) {
        // The proxy pages share one app bar, so the style's top bar reaches the per-account route page too.
        ProxyTopBar(stringResource(R.string.multi_proxy_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The current route is a group card in the style's own surface, like the other cards on the proxy pages.
            ProxyCard {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = settingsIconTint(), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.multi_proxy_route),
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        routeText,
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (row?.networkSuspended == true) {
                        Text(
                            stringResource(R.string.multi_proxy_paused),
                            color = palette.danger,
                            fontSize = 14.sp,
                            modifier = Modifier.errorAnnouncement()
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.multi_proxy_description),
                color = palette.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                stringResource(R.string.multi_proxy_change),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() }
            )
            Text(
                stringResource(R.string.multi_proxy_change_note),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            ProxyCard {
                listOf(
                    TdSecondaryRoutePreference.FOLLOW_PRIMARY to stringResource(R.string.multi_proxy_follow),
                    TdSecondaryRoutePreference.SHARED_MIHOMO to stringResource(R.string.multi_proxy_shared),
                    TdSecondaryRoutePreference.EXPLICIT_DIRECT to stringResource(R.string.multi_proxy_direct)
                ).forEachIndexed { index, (preference, title) ->
                    if (index > 0) SettingsRowDivider(startInset = SettingsRowInset)
                    val selected = row?.routePreference == preference
                    ProxyRadioRow(
                        selected = selected,
                        title = title,
                        subtitle = null,
                        enabled = !changing && row?.phase == com.beykus.beygram.data.tdlib.TdAccountAuthPhase.READY,
                        onClick = {
                            // The current route is already selected: choosing it again asks nothing.
                            if (!selected) {
                                desiredRoute = preference
                            }
                        }
                    )
                }
            }
            if (changing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = palette.accent)
            if (changeFailed) Text(
                stringResource(R.string.multi_proxy_failed),
                color = palette.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp).errorAnnouncement()
            )
            Button(onClick = onManage, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.multi_proxy_manage))
            }
        }
    }
    desiredRoute?.let { preference ->
        BeyGramDialog(
            onDismissRequest = { if (!changing) desiredRoute = null },
            title = stringResource(R.string.multi_proxy_confirm_title),
            message = stringResource(R.string.multi_proxy_confirm_note),
            confirmText = stringResource(R.string.multi_proxy_reconnect),
            onConfirm = {
                desiredRoute = null
                changing = true
                changeFailed = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            controller.reconnectWithRoute(accountId, preference)
                        }
                    } catch (_: Exception) {
                        changeFailed = true
                    } finally { changing = false }
                }
            },
            confirmEnabled = !changing,
            dismissText = stringResource(R.string.multi_account_cancel),
            onDismiss = { desiredRoute = null },
            dismissEnabled = !changing
        )
    }
}
