package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.TelegramAvatar
import kotlinx.coroutines.flow.StateFlow
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
/** Snapshot produced by the account that owns the TDLib file IDs. */
internal data class AccountPickerMeta(
    val title: String,
    val unread: Int,
    val photo: AvatarPhoto?,
    val observeFile: (Int) -> StateFlow<MediaFileState>,
    val downloadFile: (Int) -> Unit
)

@Composable
internal fun AccountPickerScreen(
    state: TdSecondaryAccountsSnapshot,
    selectedId: String,
    metadata: Map<String, AccountPickerMeta> = emptyMap(),
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
        @Composable
        fun AccountRow(accountId: String, fallback: String, ready: Boolean) {
            val item = metadata[accountId]
            OutlinedButton(
                onClick = { if (ready) onSelect(accountId) else onManage() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (item != null) {
                        TelegramAvatar(
                            item.title.take(2).uppercase(),
                            Color(0xFF588EBC),
                            42.dp, item.photo, item.observeFile, item.downloadFile
                        )
                    } else {
                        Surface(shape = CircleShape, color = palette.accentSoft, modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(fallback.take(1), color = palette.textPrimary)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item?.title?.takeIf { it.isNotBlank() } ?: fallback,
                            color = palette.textPrimary)
                        if (!ready) Text(stringResource(R.string.secondary_accounts_switch_offline),
                            fontSize = 12.sp, color = palette.textSecondary)
                    }
                    if ((item?.unread ?: 0) > 0) {
                        Surface(shape = CircleShape, color = palette.accent) {
                            Text((item?.unread ?: 0).coerceAtMost(99).toString(),
                                color = palette.onAccent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    if (accountId == selectedId) Text(" ✓", color = palette.accent)
                }
            }
        }
        AccountRow(TdAccountStorageLayout.LEGACY_ACCOUNT_ID,
            stringResource(R.string.secondary_accounts_primary), ready = true)
        state.accounts.forEachIndexed { index, account ->
            val ready = account.connectedInThisProcess &&
                account.phase == TdAccountAuthPhase.READY && !account.networkSuspended
            AccountRow(account.accountId, stringResource(R.string.secondary_accounts_account_label, index + 2), ready)
        }
        Text(stringResource(R.string.secondary_accounts_switch_note),
            color = palette.textSecondary, fontSize = 13.sp)
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.secondary_accounts_add))
        }
    }
}
