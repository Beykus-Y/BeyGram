package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdAccountAuthPhase
import com.beykus.beygram.data.tdlib.TdSecondaryAccountController
import com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Experimental secondary *authentication* management. Not a Telegram chat
 * switcher: main chats/settings/notifications still belong to the legacy repo.
 */
@Composable
internal fun SecondaryAccountsScreen(
    controller: TdSecondaryAccountController,
    onBack: () -> Unit,
    onOpenChats: (String) -> Unit
) {
    val palette = beyGramPalette()
    val coordinator = controller.coordinator
    val snapshot by coordinator.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var routePreference by rememberSaveable { mutableStateOf(TdSecondaryRoutePreference.FOLLOW_PRIMARY) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun runAction(action: suspend () -> Unit) {
        if (working) return
        scope.launch {
            working = true
            error = false
            try { withContext(Dispatchers.IO) { action() } }
            catch (_: Exception) { error = true }
            finally { working = false }
        }
    }

    LaunchedEffect(controller) {
        withContext(Dispatchers.IO) {
            coordinator.refresh()
            controller.checkRoutesNow()
        }
    }

    val selected = snapshot.accounts.firstOrNull { it.accountId == selectedId }
    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = palette.textPrimary) }
            Text(stringResource(R.string.secondary_accounts_title),
                fontSize = 25.sp, fontWeight = FontWeight.Bold,
                color = palette.textPrimary, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.secondary_accounts_beta_warning),
            color = palette.textSecondary, fontSize = 14.sp,
            modifier = Modifier.background(palette.surface, RoundedCornerShape(16.dp)).padding(16.dp))
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.secondary_accounts_primary), color = palette.textPrimary,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.secondary_accounts_primary_status),
            color = palette.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.secondary_accounts_others),
            color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        snapshot.accounts.forEachIndexed { index, row ->
            val phase = row.phase
            val summary = when {
                row.networkSuspended -> stringResource(R.string.secondary_accounts_route_paused)
                phase == TdAccountAuthPhase.READY -> stringResource(R.string.secondary_accounts_authorized)
                phase == TdAccountAuthPhase.CLOSED || phase == null -> stringResource(R.string.secondary_accounts_offline)
                else -> stringResource(R.string.secondary_accounts_signing_in)
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(palette.surface, RoundedCornerShape(14.dp))
                    .clickable {
                        // Clear secrets synchronously, before the new row can
                        // receive its first tap on Submit. Never reuse another
                        // account's phone/code/2FA password.
                        phone = ""
                        code = ""
                        password = ""
                        selectedId = row.accountId
                        error = false
                    }
                    .padding(14.dp)
            ) {
                Text(stringResource(R.string.secondary_accounts_account_label, index + 2) +
                    if (selectedId == row.accountId) " ✓" else "",
                    color = palette.textPrimary, fontWeight = FontWeight.Medium)
                Text(summary, color = palette.textSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                if (!working) scope.launch {
                    working = true
                    error = false
                    try {
                        val reserved = withContext(Dispatchers.IO) { coordinator.reserveAccount() }
                        phone = ""
                        code = ""
                        password = ""
                        selectedId = reserved
                    } catch (_: Exception) { error = true }
                    finally { working = false }
                }
            },
            enabled = !working && !snapshot.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.secondary_accounts_add)) }

        if (selected != null) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.secondary_accounts_selected),
                color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (!selected.connectedInThisProcess) {
                Text(stringResource(R.string.secondary_accounts_route),
                    color = palette.textSecondary, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = routePreference == TdSecondaryRoutePreference.FOLLOW_PRIMARY,
                        onClick = { routePreference = TdSecondaryRoutePreference.FOLLOW_PRIMARY },
                        label = { Text(stringResource(R.string.secondary_accounts_follow_route)) }
                    )
                    FilterChip(
                        selected = routePreference == TdSecondaryRoutePreference.SHARED_MIHOMO,
                        onClick = { routePreference = TdSecondaryRoutePreference.SHARED_MIHOMO },
                        label = { Text("Mihomo") }
                    )
                }
                FilterChip(
                    selected = routePreference == TdSecondaryRoutePreference.EXPLICIT_DIRECT,
                    onClick = { routePreference = TdSecondaryRoutePreference.EXPLICIT_DIRECT },
                    label = { Text(stringResource(R.string.secondary_accounts_explicit_direct)) }
                )
                Button(
                    onClick = { runAction { coordinator.connect(selected.accountId, routePreference) } },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (working) CircularProgressIndicator()
                    else Text(stringResource(R.string.secondary_accounts_connect))
                }
            } else {
                if (selected.networkSuspended) {
                    Text(stringResource(R.string.secondary_accounts_route_paused),
                        color = palette.danger, modifier = Modifier.padding(vertical = 10.dp))
                }
                when (selected.phase) {
                    TdAccountAuthPhase.WAIT_PHONE -> {
                        OutlinedTextField(value = phone, onValueChange = { phone = it.take(30) },
                            label = { Text(stringResource(R.string.auth_phone_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val submitted = phone
                            phone = ""
                            runAction { coordinator.submitPhone(selected.accountId, submitted) }
                        }, enabled = !working && phone.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.auth_phone_get_code))
                        }
                    }
                    TdAccountAuthPhase.WAIT_CODE -> {
                        OutlinedTextField(value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(8) },
                            label = { Text(stringResource(R.string.auth_code_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val submitted = code
                            code = ""
                            runAction { coordinator.submitCode(selected.accountId, submitted) }
                        }, enabled = !working && code.length in 4..8, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.auth_sign_in))
                        }
                        TextButton(onClick = { runAction { coordinator.resendCode(selected.accountId) } },
                            enabled = !working) { Text(stringResource(R.string.auth_code_resend)) }
                    }
                    TdAccountAuthPhase.WAIT_PASSWORD -> {
                        OutlinedTextField(value = password, onValueChange = { password = it },
                            label = { Text(stringResource(R.string.auth_password_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val submitted = password
                            password = ""
                            runAction { coordinator.submitPassword(selected.accountId, submitted) }
                        }, enabled = !working && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.auth_sign_in))
                        }
                    }
                    TdAccountAuthPhase.READY -> {
                        Text(stringResource(R.string.secondary_accounts_ready_no_chats),
                            color = palette.textSecondary, modifier = Modifier.padding(vertical = 14.dp))
                        if (!selected.networkSuspended) Button(
                            onClick = { onOpenChats(selected.accountId) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.secondary_chats_open)) }
                    }
                    TdAccountAuthPhase.OTHER -> Text(stringResource(R.string.secondary_accounts_other_auth),
                        color = palette.textSecondary, modifier = Modifier.padding(vertical = 14.dp))
                    else -> Text(stringResource(R.string.secondary_accounts_connecting),
                        color = palette.textSecondary, modifier = Modifier.padding(vertical = 14.dp))
                }
                OutlinedButton(
                    onClick = { runAction { coordinator.disconnect(selected.accountId) } },
                    enabled = !working, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.secondary_accounts_disconnect)) }
            }
        }
        if (error) Text(stringResource(R.string.secondary_accounts_action_error),
            color = palette.danger, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(32.dp))
    }
}
