package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdSecondaryAccountController
import com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Secondary TDLib routing is owned by the process-wide native session
 * coordinator. Never pass the legacy VpnBackend into a secondary proxy editor.
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
    Column(
        Modifier.fillMaxSize().background(palette.screen).safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Text(stringResource(R.string.multi_proxy_title), fontSize = 22.sp, color = palette.textPrimary)
        }
        Column(
            Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = palette.accent)
            Text(stringResource(R.string.multi_proxy_route), color = palette.textPrimary, fontSize = 18.sp)
            Text(when (route) {
                TelegramRoute.Direct -> stringResource(R.string.multi_proxy_direct)
                TelegramRoute.Subscription -> stringResource(R.string.multi_proxy_mihomo)
                is TelegramRoute.Proxy -> stringResource(R.string.multi_proxy_manual)
                null -> if (failed) stringResource(R.string.multi_proxy_unknown) else stringResource(R.string.multi_proxy_checking)
            }, color = palette.textPrimary)
            if (row?.networkSuspended == true)
                Text(stringResource(R.string.multi_proxy_paused),
                    color = palette.danger)
        }
        Text(
            stringResource(R.string.multi_proxy_description),
            color = palette.textSecondary, fontSize = 14.sp
        )
        Text(stringResource(R.string.multi_proxy_change), color = palette.textPrimary, fontSize = 18.sp)
        Text(stringResource(R.string.multi_proxy_change_note),
            color = palette.textSecondary, fontSize = 13.sp)
        listOf(
            TdSecondaryRoutePreference.FOLLOW_PRIMARY to stringResource(R.string.multi_proxy_follow),
            TdSecondaryRoutePreference.SHARED_MIHOMO to stringResource(R.string.multi_proxy_shared),
            TdSecondaryRoutePreference.EXPLICIT_DIRECT to stringResource(R.string.multi_proxy_direct)
        ).forEach { (preference, title) ->
            OutlinedButton(
                onClick = { desiredRoute = preference },
                enabled = !changing && row?.phase == com.beykus.beygram.data.tdlib.TdAccountAuthPhase.READY &&
                    row.routePreference != preference,
                modifier = Modifier.fillMaxWidth().semantics {
                    selected = row?.routePreference == preference
                }
            ) { Text(title) }
        }
        if (changing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (changeFailed) Text(
            stringResource(R.string.multi_proxy_failed),
            color = palette.danger, fontSize = 13.sp
        )
        Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.multi_proxy_manage)) }
    }
    desiredRoute?.let { preference ->
        AlertDialog(
            onDismissRequest = { if (!changing) desiredRoute = null },
            title = { Text(stringResource(R.string.multi_proxy_confirm_title)) },
            text = { Text(stringResource(R.string.multi_proxy_confirm_note)) },
            confirmButton = {
                TextButton(onClick = {
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
                }, enabled = !changing) { Text(stringResource(R.string.multi_proxy_reconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { desiredRoute = null }, enabled = !changing) { Text(stringResource(R.string.multi_account_cancel)) }
            }
        )
    }
}
