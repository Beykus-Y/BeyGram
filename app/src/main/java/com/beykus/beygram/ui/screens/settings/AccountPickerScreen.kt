package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdAccountAuthPhase
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.data.tdlib.TdSecondaryAccountsSnapshot
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Only authorized, actually running TDLib sessions can be selected.
 * Selecting a stored-but-closed account routes to the connection manager
 * rather than showing legacy chat data under a second account label.
 */
@Composable
internal fun AccountPickerScreen(
    state: TdSecondaryAccountsSnapshot,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxSize().background(palette.screen)
        .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = palette.textPrimary)
            }
            Text(stringResource(R.string.secondary_accounts_switch),
                color = palette.textPrimary, fontWeight = FontWeight.Bold,
                fontSize = 23.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
        }
        Button(
            onClick = { onSelect(TdAccountStorageLayout.LEGACY_ACCOUNT_ID) },
            enabled = selectedId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.secondary_accounts_primary) +
                if (selectedId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) " ✓" else "")
        }
        state.accounts.forEachIndexed { index, account ->
            val ready = account.connectedInThisProcess &&
                account.phase == TdAccountAuthPhase.READY && !account.networkSuspended
            OutlinedButton(
                onClick = { if (ready) onSelect(account.accountId) else onManage() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.secondary_accounts_account_label, index + 2) +
                        if (selectedId == account.accountId) " ✓" else "")
                    if (!ready) {
                        Text(stringResource(R.string.secondary_accounts_switch_offline),
                            fontSize = 12.sp)
                    }
                }
            }
        }
        Text(stringResource(R.string.secondary_accounts_switch_note),
            color = palette.textSecondary, fontSize = 13.sp)
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.secondary_accounts_add))
        }
    }
}
