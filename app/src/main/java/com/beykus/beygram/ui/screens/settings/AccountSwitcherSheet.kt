package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.data.tdlib.TdSecondaryAccountController
import com.beykus.beygram.data.tdlib.TdSecondaryAccountsSnapshot
import com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference
import com.beykus.beygram.domain.model.initialsOf
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One switcher for every entry point. The sheet owns only transient UI state;
 * TDLib authorization, session lifetime and account selection stay in the
 * process-scoped controller so navigation cannot orphan a live client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSwitcherSheet(
    visible: Boolean,
    currentAccountId: String,
    legacyReady: Boolean,
    snapshot: TdSecondaryAccountsSnapshot,
    metadata: Map<String, AccountSwitcherMeta>,
    controller: TdSecondaryAccountController,
    initialAddMode: Boolean,
    onAddModeConsumed: () -> Unit,
    initialRouteAccountId: String?,
    onInitialRouteConsumed: () -> Unit,
    onSwitch: suspend (String) -> Result<Unit>,
    onAccountReady: (String) -> Unit,
    onAccountDeleted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var authAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var addMode by rememberSaveable { mutableStateOf(false) }
    var busyAccountId by remember { mutableStateOf<String?>(null) }
    var addBusy by remember { mutableStateOf(false) }
    var preparingAccountId by remember { mutableStateOf<String?>(null) }
    var authActionBusy by remember { mutableStateOf(false) }
    var switchInProgress by remember { mutableStateOf(false) }
    var autoSwitchStarted by remember(authAccountId) { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, AccountSwitcherError>>(emptyMap()) }
    var listError by remember { mutableStateOf<AccountSwitcherErrorReason?>(null) }
    var authError by remember { mutableStateOf<AccountSwitcherErrorReason?>(null) }
    var authSwitchError by remember { mutableStateOf<AccountSwitcherErrorReason?>(null) }
    var routeAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLogoutId by remember { mutableStateOf<String?>(null) }
    var confirmDiscardId by remember { mutableStateOf<String?>(null) }
    var confirmDisconnectId by remember { mutableStateOf<String?>(null) }
    var phone by remember(authAccountId) { mutableStateOf("") }
    var code by remember(authAccountId) { mutableStateOf("") }
    var password by remember(authAccountId) { mutableStateOf("") }

    fun setAccountError(accountId: String, reason: AccountSwitcherErrorReason) {
        errors = errors + (accountId to AccountSwitcherError(accountId, reason))
    }

    fun clearAccountError(accountId: String) {
        if (accountId in errors) errors = errors - accountId
    }

    fun closeAuth() {
        authAccountId = null
        addMode = false
        authError = null
        authSwitchError = null
        phone = ""
        code = ""
        password = ""
    }

    fun finishSwitch(accountId: String) {
        if (switchInProgress) return
        switchInProgress = true
        scope.launch {
            authError = null
            authSwitchError = null
            try {
                val result = onSwitch(accountId)
                val failure = result.exceptionOrNull()
                if (failure == null) {
                    onAccountReady(accountId)
                    onDismiss()
                } else {
                    authSwitchError = accountSwitcherErrorReason(failure)
                }
            } finally {
                switchInProgress = false
            }
        }
    }

    fun startAdd() {
        if (addBusy || busyAccountId != null) return
        scope.launch {
            addBusy = true
            listError = null
            authError = null
            try {
                val accountId = withContext(Dispatchers.IO) { controller.coordinator.reserveAccount() }
                authAccountId = accountId
                addMode = true
                clearAccountError(accountId)
                preparingAccountId = accountId
                try {
                    withContext(Dispatchers.IO) { controller.connectForSignIn(accountId) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    authError = accountSwitcherErrorReason(failure)
                    setAccountError(accountId, accountSwitcherErrorReason(failure))
                } finally {
                    preparingAccountId = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                listError = accountSwitcherErrorReason(failure)
            } finally {
                addBusy = false
            }
        }
    }

    fun openSignIn(accountId: String, isNew: Boolean) {
        authAccountId = accountId
        addMode = isNew
        authError = null
        authSwitchError = null
        clearAccountError(accountId)
        if (snapshot.accounts.firstOrNull { it.accountId == accountId }
                ?.connectedInThisProcess != true) {
            scope.launch {
                preparingAccountId = accountId
                try {
                    withContext(Dispatchers.IO) { controller.connectForSignIn(accountId) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    authError = accountSwitcherErrorReason(failure)
                    setAccountError(accountId, accountSwitcherErrorReason(failure))
                } finally {
                    preparingAccountId = null
                }
            }
        }
    }

    fun submitAuth(action: suspend () -> Unit) {
        if (authActionBusy) return
        scope.launch {
            authActionBusy = true
            authError = null
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                authError = accountSwitcherErrorReason(failure)
            } finally {
                authActionBusy = false
            }
        }
    }

    fun switchTo(accountId: String) {
        if (busyAccountId != null || addBusy) return
        scope.launch {
            busyAccountId = accountId
            clearAccountError(accountId)
            listError = null
            try {
                val result = onSwitch(accountId)
                val failure = result.exceptionOrNull()
                if (failure == null) {
                    onAccountReady(accountId)
                    onDismiss()
                } else {
                    setAccountError(accountId, accountSwitcherErrorReason(failure))
                }
            } finally {
                busyAccountId = null
            }
        }
    }

    fun logout(accountId: String) {
        if (busyAccountId != null || addBusy) return
        scope.launch {
            busyAccountId = accountId
            clearAccountError(accountId)
            try {
                withContext(Dispatchers.IO) {
                    controller.ensureReady(accountId)
                    controller.logoutAndDelete(accountId)
                }
                clearAccountError(accountId)
                onAccountDeleted(accountId)
                if (authAccountId == accountId) closeAuth()
                onDismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                setAccountError(accountId, accountSwitcherErrorReason(failure))
            } finally {
                busyAccountId = null
            }
        }
    }

    fun discard(accountId: String) {
        if (busyAccountId != null || addBusy) return
        scope.launch {
            busyAccountId = accountId
            clearAccountError(accountId)
            try {
                withContext(Dispatchers.IO) { controller.discardIncompleteAccount(accountId) }
                clearAccountError(accountId)
                onAccountDeleted(accountId)
                if (authAccountId == accountId) closeAuth()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                setAccountError(accountId, accountSwitcherErrorReason(failure))
            } finally {
                busyAccountId = null
            }
        }
    }

    fun disconnect(accountId: String) {
        if (busyAccountId != null || addBusy) return
        scope.launch {
            busyAccountId = accountId
            clearAccountError(accountId)
            try {
                withContext(Dispatchers.IO) { controller.coordinator.disconnect(accountId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                setAccountError(accountId, accountSwitcherErrorReason(failure))
            } finally {
                busyAccountId = null
            }
        }
    }

    LaunchedEffect(visible, initialAddMode) {
        if (visible && initialAddMode) {
            onAddModeConsumed()
            if (authAccountId == null) startAdd()
        }
    }

    LaunchedEffect(visible, initialRouteAccountId) {
        if (visible && initialRouteAccountId != null) {
            routeAccountId = initialRouteAccountId
            onInitialRouteConsumed()
        }
    }

    val rows = accountSwitcherRows(
        AccountSwitcherInput(
            currentAccountId = currentAccountId,
            legacyReady = legacyReady,
            legacyMeta = metadata[TdAccountStorageLayout.LEGACY_ACCOUNT_ID],
            accounts = snapshot.accounts,
            metadata = metadata,
            busyAccountId = busyAccountId,
            errors = errors
        )
    )
    val authRow = authAccountId?.let { id -> snapshot.accounts.firstOrNull { it.accountId == id } }
    val authStep = accountAuthStep(authRow?.phase, authRow?.connectedInThisProcess == true)
    val authTitle = authRow?.let { id ->
        metadata[id.accountId]?.identity?.title?.takeIf { it.isNotBlank() }
    }
    val authDeleteId = authAccountId?.takeIf { id ->
        rows.firstOrNull { it.accountId == id }?.canDelete == true
    }
    val authDeleteAction: (() -> Unit)? = if (authDeleteId != null) {
        { confirmDiscardId = authDeleteId }
    } else null

    LaunchedEffect(authAccountId, authStep) {
        val id = authAccountId
        if (id != null && authStep == AccountAuthStep.READY && !autoSwitchStarted) {
            autoSwitchStarted = true
            finishSwitch(id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (busyAccountId == null && !addBusy) {
                if (authAccountId != null) closeAuth() else onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = palette.surface
    ) {
        if (authAccountId != null) {
            AccountAuthContent(
                addMode = addMode,
                accountTitle = authTitle,
                step = authStep,
                busy = addBusy || authActionBusy || switchInProgress ||
                    preparingAccountId == authAccountId || busyAccountId == authAccountId,
                errorText = accountSwitcherErrorText(authError ?: authSwitchError),
                phone = phone,
                onPhoneChange = { phone = it.take(30) },
                code = code,
                onCodeChange = { code = it.filter(Char::isDigit).take(8) },
                password = password,
                onPasswordChange = { password = it },
                onBack = ::closeAuth,
                onPhoneSubmit = {
                    val id = authAccountId
                    val value = phone.trim()
                    if (id != null && value.isNotBlank()) submitAuth {
                        controller.coordinator.submitPhone(id, value)
                        phone = ""
                    }
                },
                onCodeSubmit = {
                    val id = authAccountId
                    val value = code
                    if (id != null && value.length in 4..8) submitAuth {
                        controller.coordinator.submitCode(id, value)
                        code = ""
                    }
                },
                onResendCode = {
                    val id = authAccountId
                    if (id != null) submitAuth { controller.coordinator.resendCode(id) }
                },
                onPasswordSubmit = {
                    val id = authAccountId
                    val value = password
                    if (id != null && value.isNotBlank()) submitAuth {
                        controller.coordinator.submitPassword(id, value)
                        password = ""
                    }
                },
                onRetryConnect = {
                    authAccountId?.let { id -> openSignIn(id, addMode) }
                },
                onRetrySwitch = {
                    authAccountId?.let { id ->
                        autoSwitchStarted = true
                        finishSwitch(id)
                    }
                },
                onConfigureRoute = { authAccountId?.let { routeAccountId = it } },
                onDelete = authDeleteAction,
                onLater = ::closeAuth
            )
        } else {
            AccountSwitcherContent(
                rows = rows,
                busy = addBusy || busyAccountId != null,
                errorText = accountSwitcherErrorText(listError),
                onRowClick = { row ->
                    when {
                        row.isCurrent -> onDismiss()
                        row.canSignIn -> openSignIn(row.accountId, isNew = false)
                        row.canSelect -> switchTo(row.accountId)
                    }
                },
                onAdd = ::startAdd,
                onContinue = { openSignIn(it, isNew = false) },
                onRetry = { switchTo(it) },
                onConfigureRoute = { routeAccountId = it },
                onDisconnect = { confirmDisconnectId = it },
                onLogout = { confirmLogoutId = it },
                onDeleteIncomplete = { confirmDiscardId = it }
            )
        }
    }

    confirmLogoutId?.let { accountId ->
        val title = rows.firstOrNull { it.accountId == accountId }?.title
            ?: stringResource(R.string.secondary_accounts_account_label, 2)
        AlertDialog(
            onDismissRequest = { if (busyAccountId == null) confirmLogoutId = null },
            title = { Text(stringResource(R.string.multi_account_logout_title)) },
            text = {
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.multi_account_logout_message))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = busyAccountId == null,
                    onClick = {
                        confirmLogoutId = null
                        logout(accountId)
                    }
                ) { Text(stringResource(R.string.multi_account_logout_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogoutId = null }, enabled = busyAccountId == null) {
                    Text(stringResource(R.string.multi_account_cancel))
                }
            }
        )
    }

    confirmDiscardId?.let { accountId ->
        AlertDialog(
            onDismissRequest = { if (busyAccountId == null) confirmDiscardId = null },
            title = { Text(stringResource(R.string.account_switcher_delete_title)) },
            text = { Text(stringResource(R.string.account_switcher_delete_message)) },
            confirmButton = {
                TextButton(
                    enabled = busyAccountId == null,
                    onClick = {
                        confirmDiscardId = null
                        discard(accountId)
                    }
                ) { Text(stringResource(R.string.account_switcher_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardId = null }, enabled = busyAccountId == null) {
                    Text(stringResource(R.string.multi_account_cancel))
                }
            }
        )
    }

    confirmDisconnectId?.let { accountId ->
        AlertDialog(
            onDismissRequest = { if (busyAccountId == null) confirmDisconnectId = null },
            title = { Text(stringResource(R.string.account_switcher_disconnect_title)) },
            text = { Text(stringResource(R.string.account_switcher_disconnect_message)) },
            confirmButton = {
                TextButton(
                    enabled = busyAccountId == null,
                    onClick = {
                        confirmDisconnectId = null
                        disconnect(accountId)
                    }
                ) { Text(stringResource(R.string.account_switcher_disconnect_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnectId = null }, enabled = busyAccountId == null) {
                    Text(stringResource(R.string.multi_account_cancel))
                }
            }
        )
    }

    routeAccountId?.let { accountId ->
        val row = rows.firstOrNull { it.accountId == accountId }
        AccountRouteDialog(
            accountTitle = row?.title,
            current = controller.preferredRoute(accountId),
            busy = busyAccountId == accountId,
            onDismiss = { routeAccountId = null },
            onApply = { preference ->
                scope.launch {
                    busyAccountId = accountId
                    clearAccountError(accountId)
                    try {
                        withContext(Dispatchers.IO) { controller.useRoute(accountId, preference) }
                        routeAccountId = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        setAccountError(accountId, accountSwitcherErrorReason(failure))
                    } finally {
                        busyAccountId = null
                    }
                }
            }
        )
    }
}

/** Compact account list shared by the bottom sheet and Compose UI tests. */
@Composable
internal fun AccountSwitcherContent(
    rows: List<AccountSwitcherRow>,
    busy: Boolean,
    errorText: String?,
    onRowClick: (AccountSwitcherRow) -> Unit,
    onAdd: () -> Unit,
    onContinue: (String) -> Unit,
    onRetry: (String) -> Unit,
    onConfigureRoute: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onLogout: (String) -> Unit,
    onDeleteIncomplete: (String) -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.account_switcher_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        }
        if (errorText != null) {
            Surface(
                color = palette.warningSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = palette.warningText, modifier = Modifier.size(20.dp))
                    Text(
                        errorText,
                        color = palette.warningText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(rows, key = { it.accountId }) { row ->
                AccountSwitcherRowItem(
                    row = row,
                    enabled = !busy,
                    onClick = { onRowClick(row) },
                    onContinue = onContinue,
                    onRetry = onRetry,
                    onConfigureRoute = onConfigureRoute,
                    onDisconnect = onDisconnect,
                    onLogout = onLogout,
                    onDeleteIncomplete = onDeleteIncomplete
                )
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 78.dp))
            }
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(enabled = !busy, role = Role.Button, onClick = onAdd)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = palette.accentSoft, modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = palette.accent, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            stringResource(R.string.account_switcher_add),
                            color = palette.accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.account_switcher_add_note),
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountSwitcherRowItem(
    row: AccountSwitcherRow,
    enabled: Boolean,
    onClick: () -> Unit,
    onContinue: (String) -> Unit,
    onRetry: (String) -> Unit,
    onConfigureRoute: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onLogout: (String) -> Unit,
    onDeleteIncomplete: (String) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val title = row.title ?: if (row.isPrimary) {
        stringResource(R.string.secondary_accounts_primary)
    } else {
        stringResource(R.string.secondary_accounts_account_label, row.labelNumber)
    }
    val identifier = when {
        row.username.isNotBlank() -> stringResource(R.string.account_switcher_identifier_username, row.username)
        row.phone.isNotBlank() -> row.phone
        else -> null
    }
    val statusText = accountSwitcherStatusText(row)
    val statusColor = when (row.status) {
        AccountSwitcherStatus.ERROR,
        AccountSwitcherStatus.UNSUPPORTED,
        AccountSwitcherStatus.ROUTE_PAUSED,
        AccountSwitcherStatus.LOGGING_OUT -> palette.danger
        AccountSwitcherStatus.OFFLINE -> palette.warningText
        AccountSwitcherStatus.CURRENT -> palette.accent
        else -> palette.textSecondary
    }
    val clickable = enabled && (row.canSelect || row.canSignIn)
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { selected = row.isCurrent }
            .clickable(enabled = clickable, role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val photo = row.meta?.identity?.photo
        val observeFile = row.meta?.observeFile
        val downloadFile = row.meta?.downloadFile
        if (photo != null && observeFile != null && downloadFile != null) {
            TelegramAvatar(
                initialsOf(title), palette.accent, 46.dp, photo, observeFile, downloadFile
            )
        } else {
            Surface(shape = CircleShape, color = palette.accentSoft, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        initialsOf(title).ifEmpty { row.labelNumber.toString() },
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                statusText,
                color = statusColor,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (identifier != null) {
                Text(
                    identifier,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (row.unread > 0) {
            Surface(shape = CircleShape, color = palette.accent) {
                Text(
                    if (row.unread > 99) "99+" else row.unread.toString(),
                    color = palette.onAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                        .semantics {
                            contentDescription = context.resources.getQuantityString(
                                R.plurals.multi_account_unread, row.unread, row.unread
                            )
                        }
                )
            }
        }
        if (row.busy) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
                strokeWidth = 2.dp
            )
        } else if (row.isCurrent) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.account_switcher_current_description),
                tint = palette.accent,
                modifier = Modifier.padding(start = 6.dp).size(20.dp)
            )
        }
        if (row.canContinueMenu) {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.account_switcher_menu_description, title),
                        tint = palette.textSecondary
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (row.canSignIn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_switcher_continue)) },
                            onClick = {
                                menuOpen = false
                                onContinue(row.accountId)
                            }
                        )
                    }
                    if (row.status == AccountSwitcherStatus.ERROR && row.canSelect) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_switcher_retry)) },
                            onClick = {
                                menuOpen = false
                                onRetry(row.accountId)
                            }
                        )
                    }
                    if (row.canConfigureRoute) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_switcher_configure)) },
                            onClick = {
                                menuOpen = false
                                onConfigureRoute(row.accountId)
                            }
                        )
                    }
                    if (row.canDisconnect) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_switcher_disconnect)) },
                            onClick = {
                                menuOpen = false
                                onDisconnect(row.accountId)
                            }
                        )
                    }
                    if (row.canLogout) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_switcher_logout)) },
                            onClick = {
                                menuOpen = false
                                onLogout(row.accountId)
                            }
                        )
                    }
                    if (row.canDelete) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_switcher_delete_incomplete)) },
                            onClick = {
                                menuOpen = false
                                onDeleteIncomplete(row.accountId)
                            }
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** The overflow is shown only when there is at least one real action. */
private val AccountSwitcherRow.canContinueMenu: Boolean
    get() = canSignIn || canConfigureRoute || canDisconnect || canLogout || canDelete ||
        (status == AccountSwitcherStatus.ERROR && canSelect)

@Composable
private fun AccountAuthContent(
    addMode: Boolean,
    accountTitle: String?,
    step: AccountAuthStep,
    busy: Boolean,
    errorText: String?,
    phone: String,
    onPhoneChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onBack: () -> Unit,
    onPhoneSubmit: () -> Unit,
    onCodeSubmit: () -> Unit,
    onResendCode: () -> Unit,
    onPasswordSubmit: () -> Unit,
    onRetryConnect: () -> Unit,
    onRetrySwitch: () -> Unit,
    onConfigureRoute: () -> Unit,
    onDelete: (() -> Unit)?,
    onLater: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Text(
                stringResource(
                    if (addMode) R.string.account_switcher_auth_add_title
                    else R.string.account_switcher_auth_continue_title
                ),
                color = palette.textPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (accountTitle != null) {
            Text(
                accountTitle,
                color = palette.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        when (step) {
            AccountAuthStep.PREPARING -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.account_switcher_auth_preparing),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            AccountAuthStep.PHONE -> {
                Text(
                    stringResource(R.string.auth_phone_subtitle),
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text(stringResource(R.string.auth_phone_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.account_switcher_auth_phone_hint),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = onPhoneSubmit,
                    enabled = !busy && phone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(stringResource(R.string.auth_phone_get_code)) }
            }
            AccountAuthStep.CODE -> {
                Text(
                    stringResource(R.string.account_switcher_auth_code_hint),
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(R.string.auth_code_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Button(
                    onClick = onCodeSubmit,
                    enabled = !busy && code.length in 4..8,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(stringResource(R.string.auth_sign_in)) }
                TextButton(onClick = onResendCode, enabled = !busy) {
                    Text(stringResource(R.string.auth_code_resend))
                }
            }
            AccountAuthStep.PASSWORD -> {
                Text(
                    stringResource(R.string.account_switcher_auth_password_hint),
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Button(
                    onClick = onPasswordSubmit,
                    enabled = !busy && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(stringResource(R.string.auth_sign_in)) }
            }
            AccountAuthStep.READY -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.account_switcher_auth_switch_progress),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (!busy) {
                    Button(onClick = onRetrySwitch, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            AccountAuthStep.UNSUPPORTED -> Text(
                stringResource(R.string.account_switcher_auth_unsupported),
                color = palette.textSecondary,
                fontSize = 14.sp
            )
            AccountAuthStep.CLOSED -> {
                Text(
                    stringResource(R.string.account_switcher_auth_closed),
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
                Button(onClick = onRetryConnect, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(stringResource(R.string.account_switcher_auth_retry_connect))
                }
            }
        }
        if (errorText != null) {
            Text(
                errorText,
                color = palette.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLater, enabled = !busy) {
                Text(stringResource(R.string.account_switcher_auth_later))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onConfigureRoute, enabled = !busy) {
                Text(stringResource(R.string.account_switcher_configure))
            }
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(stringResource(R.string.account_switcher_delete_incomplete), color = palette.danger)
            }
        }
    }
}

@Composable
private fun AccountRouteDialog(
    accountTitle: String?,
    current: TdSecondaryRoutePreference,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (TdSecondaryRoutePreference) -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.account_switcher_route_title)) },
        text = {
            Column {
                if (accountTitle != null) {
                    Text(accountTitle, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    stringResource(R.string.account_switcher_route_message),
                    fontSize = 13.sp
                )
                listOf(
                    TdSecondaryRoutePreference.FOLLOW_PRIMARY to stringResource(R.string.multi_proxy_follow),
                    TdSecondaryRoutePreference.SHARED_MIHOMO to stringResource(R.string.multi_proxy_shared),
                    TdSecondaryRoutePreference.EXPLICIT_DIRECT to stringResource(R.string.multi_proxy_direct)
                ).forEach { (preference, title) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !busy, role = Role.RadioButton) { selected = preference }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == preference,
                            onClick = { selected = preference },
                            enabled = !busy
                        )
                        Text(title, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onApply(selected) }) {
                Text(stringResource(R.string.account_switcher_route_apply))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.multi_account_cancel))
            }
        }
    )
}

@Composable
private fun accountSwitcherStatusText(row: AccountSwitcherRow): String = when (row.status) {
    AccountSwitcherStatus.CURRENT -> stringResource(R.string.account_switcher_status_current)
    AccountSwitcherStatus.READY -> stringResource(R.string.account_switcher_status_ready)
    AccountSwitcherStatus.SWITCHING -> stringResource(R.string.account_switcher_status_switching)
    AccountSwitcherStatus.CONNECTING -> stringResource(R.string.account_switcher_status_connecting)
    AccountSwitcherStatus.RESTORING -> stringResource(R.string.account_switcher_status_restoring)
    AccountSwitcherStatus.OFFLINE -> stringResource(R.string.account_switcher_status_offline)
    AccountSwitcherStatus.ROUTE_PAUSED -> stringResource(R.string.account_switcher_status_route_paused)
    AccountSwitcherStatus.AWAITING_PHONE -> stringResource(R.string.account_switcher_status_phone)
    AccountSwitcherStatus.AWAITING_CODE -> stringResource(R.string.account_switcher_status_code)
    AccountSwitcherStatus.AWAITING_PASSWORD -> stringResource(R.string.account_switcher_status_password)
    AccountSwitcherStatus.DISCONNECTED -> stringResource(R.string.account_switcher_status_disconnected)
    AccountSwitcherStatus.UNSIGNED -> stringResource(R.string.account_switcher_status_unsigned)
    AccountSwitcherStatus.LOGGING_OUT -> stringResource(R.string.account_switcher_status_logging_out)
    AccountSwitcherStatus.UNSUPPORTED -> stringResource(R.string.account_switcher_status_unsupported)
    AccountSwitcherStatus.PRIMARY_SIGNED_OUT -> stringResource(R.string.account_switcher_status_primary_signed_out)
    AccountSwitcherStatus.ERROR -> accountSwitcherErrorText(row.error?.reason)
        ?: stringResource(R.string.account_switcher_error_generic)
}

@Composable
internal fun accountSwitcherErrorText(reason: AccountSwitcherErrorReason?): String? = when (reason) {
    null -> null
    AccountSwitcherErrorReason.ROUTE_UNAVAILABLE -> stringResource(R.string.account_switcher_error_route)
    AccountSwitcherErrorReason.ROUTE_PAUSED -> stringResource(R.string.account_switcher_error_route_paused)
    AccountSwitcherErrorReason.SESSION_LIMIT -> stringResource(R.string.account_switcher_error_session_limit)
    AccountSwitcherErrorReason.ACCOUNT_LIMIT -> stringResource(R.string.account_switcher_error_account_limit)
    AccountSwitcherErrorReason.TIMEOUT -> stringResource(R.string.account_switcher_error_timeout)
    AccountSwitcherErrorReason.NOT_AUTHORIZED -> stringResource(R.string.account_switcher_error_not_authorized)
    AccountSwitcherErrorReason.AUTH_INVALID_PHONE -> stringResource(R.string.account_switcher_error_auth_phone)
    AccountSwitcherErrorReason.AUTH_INVALID_CODE -> stringResource(R.string.account_switcher_error_auth_code)
    AccountSwitcherErrorReason.AUTH_CODE_EXPIRED -> stringResource(R.string.account_switcher_error_auth_code_expired)
    AccountSwitcherErrorReason.AUTH_INVALID_PASSWORD -> stringResource(R.string.account_switcher_error_auth_password)
    AccountSwitcherErrorReason.AUTH_FLOOD_WAIT -> stringResource(R.string.account_switcher_error_auth_flood)
    AccountSwitcherErrorReason.AUTH_FAILED -> stringResource(R.string.account_switcher_error_auth_failed)
    AccountSwitcherErrorReason.GENERIC -> stringResource(R.string.account_switcher_error_generic)
}
