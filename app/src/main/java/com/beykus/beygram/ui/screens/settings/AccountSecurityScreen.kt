package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.CloudPasswordState
import com.beykus.beygram.domain.model.NewPasswordValidation
import com.beykus.beygram.domain.model.PhoneChangeCode
import com.beykus.beygram.domain.model.isValidRecoveryEmail
import com.beykus.beygram.domain.model.matchesDeletionConfirmation
import com.beykus.beygram.domain.model.normalizeRecoveryEmail
import com.beykus.beygram.domain.model.validateNewPassword
import com.beykus.beygram.domain.repository.AccountSecurityRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AccountSecurityDialog {
    SetPassword,
    ChangePassword,
    DisablePassword,
    AddRecoveryEmail,
    ChangeRecoveryEmail,
    RemoveRecoveryEmail,
    ChangePhone,
    DeleteAccount
}

/**
 * Server-side account security for the selected Telegram account.
 *
 * The screen never keeps a password in saved state, never logs it, and treats every destructive
 * operation as successful only after TDLib returns. Deleting the server account is separate from
 * deleting a local session: the app starts [requestAccountDeletion] in its process scope and performs local
 * cleanup only after the reported successful server response.
 */
@Composable
fun AccountSecurityScreen(
    accountId: String,
    repository: AccountSecurityRepository,
    currentPhone: String,
    isPrimaryAccount: Boolean,
    deletionRunning: Boolean,
    requestAccountDeletion: (password: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onBack: () -> Unit
) {    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(accountId) { mutableStateOf<CloudPasswordState?>(null) }
    var loading by remember(accountId) { mutableStateOf(true) }
    var loadError by remember(accountId) { mutableStateOf<String?>(null) }
    var actionError by remember(accountId) { mutableStateOf<String?>(null) }
    var notice by remember(accountId) { mutableStateOf<String?>(null) }
    var busy by remember(accountId) { mutableStateOf(false) }
    var dialog by remember(accountId) { mutableStateOf<AccountSecurityDialog?>(null) }
    var refresh by remember(accountId) { mutableIntStateOf(0) }
    var deletedOnServer by remember(accountId) { mutableStateOf(false) }
    // Non-null while a phone-number change waits for its code; the dialog switches to the code step.
    var phoneChange by remember(accountId) { mutableStateOf<PhoneChangeCode?>(null) }
    // The app may still be deleting after this Activity is recreated. Never offer a
    // second destructive request or allow leaving while that process-owned job runs.
    val effectiveBusy = busy || deletionRunning

    BackHandler { if (!effectiveBusy) onBack() }

    LaunchedEffect(accountId, repository, refresh) {
        loading = true
        loadError = null
        try {
            repository.cloudPasswordState().fold(
                { loaded -> state = loaded },
                { failure ->
                    loadError = failure.userMessage(
                        context,
                        R.string.account_security_load_failed
                    )
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.userMessage(context, R.string.account_security_load_failed)
        } finally {
            loading = false
        }
    }

    fun closeDialog() {
        if (!effectiveBusy) {
            dialog = null
            actionError = null
            phoneChange = null
        }
    }

    fun applyStateResult(result: Result<CloudPasswordState>, successText: String?) {
        result.fold(
            { updated ->
                state = updated
                dialog = null
                actionError = null
                if (successText != null) notice = successText
            },
            { failure ->
                actionError = failure.userMessage(context, R.string.account_security_action_failed)
            }
        )
    }

    fun runStateAction(
        successText: String?,
        action: suspend () -> Result<CloudPasswordState>
    ) {
        if (effectiveBusy) return
        busy = true
        actionError = null
        notice = null
        scope.launch {
            try {
                applyStateResult(action(), successText)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                actionError = failure.userMessage(context, R.string.account_security_action_failed)
            } finally {
                busy = false
            }
        }
    }

    fun submitPassword(current: String, newPassword: String, hint: String, disable: Boolean) {
        val success = context.getString(
            when {
                disable -> R.string.account_security_2fa_disable_success
                state?.hasPassword == true -> R.string.account_security_2fa_change_success
                else -> R.string.account_security_2fa_set_success
            }
        )
        runStateAction(success) {
            repository.setCloudPassword(
                currentPassword = current,
                newPassword = if (disable) "" else newPassword,
                hint = if (disable) "" else hint
            )
        }
    }

    fun submitRecoveryEmail(currentPassword: String, email: String, remove: Boolean) {
        if (effectiveBusy) return
        busy = true
        actionError = null
        notice = null
        scope.launch {
            try {
                val result = if (remove) {
                    repository.setRecoveryEmail(currentPassword, "")
                } else {
                    repository.setRecoveryEmail(currentPassword, normalizeRecoveryEmail(email))
                }
                result.fold(
                    { updated ->
                        state = updated
                        dialog = null
                        actionError = null
                        notice = when {
                            remove -> context.getString(R.string.account_security_email_removed)
                            updated.pendingRecoveryEmailPattern != null -> context.getString(
                                R.string.account_security_email_sent,
                                updated.pendingRecoveryEmailPattern
                            )
                            else -> context.getString(R.string.account_security_email_updated)
                        }
                    },
                    { failure ->
                        actionError = failure.userMessage(
                            context,
                            R.string.account_security_action_failed
                        )
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                actionError = failure.userMessage(context, R.string.account_security_action_failed)
            } finally {
                busy = false
            }
        }
    }

    fun confirmRecoveryEmailCode(code: String) {
        runStateAction(context.getString(R.string.account_security_email_updated)) {
            repository.confirmRecoveryEmail(code)
        }
    }

    fun resendRecoveryEmailCode() {
        runStateAction(null) {
            repository.resendRecoveryEmailCode().also { result ->
                result.onSuccess { updated ->
                    notice = updated.pendingRecoveryEmailPattern?.let {
                        context.getString(R.string.account_security_email_sent, it)
                    }
                }
            }
        }
    }

    fun cancelRecoveryEmailChange() {
        runStateAction(null) { repository.cancelRecoveryEmailChange() }
    }

    fun sendPhoneCode(phone: String) {
        if (effectiveBusy) return
        busy = true
        actionError = null
        notice = null
        scope.launch {
            try {
                repository.sendPhoneChangeCode(phone).fold(
                    { sent -> phoneChange = sent },
                    { failure ->
                        actionError = failure.userMessage(context, R.string.account_security_action_failed)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                actionError = failure.userMessage(context, R.string.account_security_action_failed)
            } finally {
                busy = false
            }
        }
    }

    fun resendPhoneCode() {
        if (effectiveBusy) return
        busy = true
        actionError = null
        scope.launch {
            try {
                repository.resendPhoneChangeCode().fold(
                    { resent -> phoneChange = resent },
                    { failure ->
                        actionError = failure.userMessage(context, R.string.account_security_action_failed)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                actionError = failure.userMessage(context, R.string.account_security_action_failed)
            } finally {
                busy = false
            }
        }
    }

    fun confirmPhoneCode(code: String) {
        if (effectiveBusy) return
        busy = true
        actionError = null
        notice = null
        scope.launch {
            try {
                repository.checkPhoneChangeCode(code).fold(
                    {
                        phoneChange = null
                        dialog = null
                        notice = context.getString(R.string.account_security_phone_changed)
                    },
                    { failure ->
                        actionError = failure.userMessage(context, R.string.account_security_action_failed)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                actionError = failure.userMessage(context, R.string.account_security_action_failed)
            } finally {
                busy = false
            }
        }
    }

    fun deleteAccount(password: String) {
        if (effectiveBusy) return
        busy = true
        actionError = null
        notice = null
        // The app owns this request in its process scope: an authorization update may
        // dispose this screen before TDLib delivers the result, and cleanup must survive.
        requestAccountDeletion(password) { result ->
            result.fold(
                {
                    dialog = null
                    deletedOnServer = true
                },
                { failure ->
                    actionError = failure.userMessage(
                        context,
                        R.string.account_security_action_failed
                    )
                }
            )
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
    ) {
        ProfileToolbar(stringResource(R.string.account_security_title), onBack = {
            if (!effectiveBusy) onBack()
        })
        if (loading || effectiveBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.account_security_intro),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            loadError?.let { message ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = palette.warningSurface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(message, color = palette.warningText)
                            TextButton(enabled = !loading, onClick = { refresh++ }) {
                                Text(stringResource(R.string.common_retry), color = palette.accentInk)
                            }
                        }
                    }
                }
            }
            actionError?.let { message ->
                item {
                    Text(
                        message,
                        color = palette.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp).errorAnnouncement()
                    )
                }
            }
            notice?.let { message ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = palette.successSurface) {
                        Text(
                            message,
                            color = palette.successText,
                            modifier = Modifier.fillMaxWidth().padding(14.dp)
                        )
                    }
                }
            }
            if (deletedOnServer) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = palette.successSurface) {
                        Text(
                            stringResource(R.string.account_security_deleted_on_server),
                            color = palette.successText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            } else if (deletionRunning) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = palette.warningSurface) {
                        Text(
                            stringResource(R.string.account_security_delete_busy),
                            color = palette.warningText,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
            state?.let { passwordState ->
                item {
                    PasswordCard(
                        state = passwordState,
                        busy = effectiveBusy,
                        onSet = { dialog = AccountSecurityDialog.SetPassword; actionError = null },
                        onChange = { dialog = AccountSecurityDialog.ChangePassword; actionError = null },
                        onDisable = { dialog = AccountSecurityDialog.DisablePassword; actionError = null }
                    )
                }
                passwordState.pendingRecoveryEmailPattern?.let { pattern ->
                    item {
                        PendingRecoveryEmailCard(
                            pattern = pattern,
                            busy = effectiveBusy,
                            onConfirm = { confirmRecoveryEmailCode(it) },
                            onResend = { resendRecoveryEmailCode() },
                            onCancel = { cancelRecoveryEmailChange() }
                        )
                    }
                }
                item {
                    RecoveryEmailCard(
                        state = passwordState,
                        busy = effectiveBusy,
                        onAdd = { dialog = AccountSecurityDialog.AddRecoveryEmail; actionError = null },
                        onChange = { dialog = AccountSecurityDialog.ChangeRecoveryEmail; actionError = null },
                        onRemove = { dialog = AccountSecurityDialog.RemoveRecoveryEmail; actionError = null }
                    )
                }
            }
            item {
                PhoneNumberCard(
                    currentPhone = currentPhone,
                    busy = effectiveBusy,
                    onChange = { dialog = AccountSecurityDialog.ChangePhone; actionError = null }
                )
            }
            item {
                DeleteAccountCard(
                    deletedOnServer = deletedOnServer,
                    isPrimaryAccount = isPrimaryAccount,
                    busy = effectiveBusy,
                    onDelete = { dialog = AccountSecurityDialog.DeleteAccount; actionError = null }
                )
            }
        }
    }

    when (dialog) {
        AccountSecurityDialog.SetPassword -> PasswordDialog(
            hasPassword = false,
            disable = false,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onConfirm = { current, newPassword, hint ->
                submitPassword(current, newPassword, hint, disable = false)
            }
        )
        AccountSecurityDialog.ChangePassword -> PasswordDialog(
            hasPassword = true,
            disable = false,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onConfirm = { current, newPassword, hint ->
                submitPassword(current, newPassword, hint, disable = false)
            }
        )
        AccountSecurityDialog.DisablePassword -> PasswordDialog(
            hasPassword = true,
            disable = true,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onConfirm = { current, _, _ ->
                submitPassword(current, "", "", disable = true)
            }
        )
        AccountSecurityDialog.AddRecoveryEmail -> RecoveryEmailDialog(
            hasRecoveryEmailAddress = false,
            remove = false,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onConfirm = { password, email -> submitRecoveryEmail(password, email, remove = false) }
        )
        AccountSecurityDialog.ChangeRecoveryEmail -> RecoveryEmailDialog(
            hasRecoveryEmailAddress = true,
            remove = false,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onConfirm = { password, email -> submitRecoveryEmail(password, email, remove = false) }
        )
        AccountSecurityDialog.RemoveRecoveryEmail -> RecoveryEmailDialog(
            hasRecoveryEmailAddress = true,
            remove = true,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onConfirm = { password, _ -> submitRecoveryEmail(password, "", remove = true) }
        )
        AccountSecurityDialog.ChangePhone -> PhoneNumberChangeDialog(
            pending = phoneChange,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onRequestCode = { sendPhoneCode(it) },
            onResend = { resendPhoneCode() },
            onConfirm = { confirmPhoneCode(it) }
        )
        AccountSecurityDialog.DeleteAccount -> DeleteAccountDialog(
            hasPassword = state?.hasPassword == true,
            isPrimaryAccount = isPrimaryAccount,
            busy = effectiveBusy,
            error = actionError,
            onDismiss = { closeDialog() },
            onConfirm = { deleteAccount(it) }
        )
        null -> Unit
    }
}

@Composable
private fun SecurityCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = beyGramPalette()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color = palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp).semantics { heading() }
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun PasswordCard(
    state: CloudPasswordState,
    busy: Boolean,
    onSet: () -> Unit,
    onChange: () -> Unit,
    onDisable: () -> Unit
) {
    val palette = beyGramPalette()
    SecurityCard(stringResource(R.string.account_security_2fa_title)) {
        Text(
            stringResource(
                if (state.hasPassword) R.string.account_security_2fa_enabled
                else R.string.account_security_2fa_disabled
            ),
            color = if (state.hasPassword) palette.successText else palette.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Text(
            stringResource(R.string.account_security_2fa_description),
            color = palette.textSecondary,
            fontSize = 14.sp
        )
        if (state.hasPassword && state.passwordHint.isNotBlank()) {
            Text(
                stringResource(R.string.account_security_2fa_hint, state.passwordHint),
                color = palette.textSecondary,
                fontSize = 13.sp
            )
        }
        Button(
            onClick = if (state.hasPassword) onChange else onSet,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    if (state.hasPassword) R.string.account_security_2fa_change
                    else R.string.account_security_2fa_set
                )
            )
        }
        if (state.hasPassword) {
            TextButton(onClick = onDisable, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_security_2fa_disable), color = palette.danger)
            }
        }
    }
}

@Composable
private fun RecoveryEmailCard(
    state: CloudPasswordState,
    busy: Boolean,
    onAdd: () -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = beyGramPalette()
    val pending = state.pendingRecoveryEmailPattern != null
    SecurityCard(stringResource(R.string.account_security_email_title)) {
        Text(
            stringResource(
                if (state.hasRecoveryEmailAddress) R.string.account_security_email_configured
                else R.string.account_security_email_not_configured
            ),
            color = if (state.hasRecoveryEmailAddress) palette.successText else palette.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Text(
            stringResource(R.string.account_security_email_description),
            color = palette.textSecondary,
            fontSize = 14.sp
        )
        if (!state.hasPassword) {
            Text(
                stringResource(R.string.account_security_email_requires_password),
                color = palette.warningText,
                fontSize = 13.sp
            )
        } else {
            Button(
                onClick = if (state.hasRecoveryEmailAddress) onChange else onAdd,
                enabled = !busy && !pending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (state.hasRecoveryEmailAddress) R.string.account_security_email_change
                        else R.string.account_security_email_add
                    )
                )
            }
            if (state.hasRecoveryEmailAddress) {
                TextButton(
                    onClick = onRemove,
                    enabled = !busy && !pending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.account_security_email_remove), color = palette.danger)
                }
            }
        }
    }
}

@Composable
private fun PendingRecoveryEmailCard(
    pattern: String,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = beyGramPalette()
    var code by remember(pattern) { mutableStateOf("") }
    SecurityCard(stringResource(R.string.account_security_email_pending_title)) {
        Text(
            stringResource(R.string.account_security_email_pending_body, pattern),
            color = palette.textSecondary,
            fontSize = 14.sp
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isLetterOrDigit).take(12) },
            label = { Text(stringResource(R.string.account_security_email_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onConfirm(code) },
            enabled = !busy && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.account_security_email_confirm))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = onResend, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_security_email_resend))
            }
            TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_security_email_cancel_change), color = palette.danger)
            }
        }
    }
}

@Composable
private fun PhoneNumberCard(currentPhone: String, busy: Boolean, onChange: () -> Unit) {
    val palette = beyGramPalette()
    SecurityCard(stringResource(R.string.account_security_phone_title)) {
        if (currentPhone.isNotBlank()) {
            Text(
                stringResource(R.string.account_security_phone_current, currentPhone),
                color = palette.textPrimary,
                fontSize = 14.sp
            )
        }
        Text(
            stringResource(R.string.account_security_phone_description),
            color = palette.textSecondary,
            fontSize = 14.sp
        )
        Button(
            onClick = onChange,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.account_security_phone_change))
        }
    }
}

@Composable
private fun DeleteAccountCard(
    deletedOnServer: Boolean,
    isPrimaryAccount: Boolean,
    busy: Boolean,
    onDelete: () -> Unit
) {
    val palette = beyGramPalette()
    SecurityCard(stringResource(R.string.account_security_delete_title)) {
        Text(
            stringResource(R.string.account_security_delete_description),
            color = palette.textSecondary,
            fontSize = 14.sp
        )
        Text(
            stringResource(
                if (isPrimaryAccount) R.string.account_security_delete_local_note_primary
                else R.string.account_security_delete_local_note_secondary
            ),
            color = palette.textSecondary,
            fontSize = 13.sp
        )
        if (!deletedOnServer) {
            Button(
                onClick = onDelete,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.danger,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.account_security_delete_action))
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    hasPassword: Boolean,
    disable: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (current: String, newPassword: String, hint: String) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val title = when {
        disable -> R.string.account_security_2fa_disable
        hasPassword -> R.string.account_security_dialog_change_password
        else -> R.string.account_security_dialog_set_password
    }

    fun confirm() {
        localError = null
        if (hasPassword && current.isEmpty()) {
            localError = context.getString(R.string.account_security_current_password_required)
            return
        }
        if (disable) {
            onConfirm(current, "", "")
            return
        }
        when (validateNewPassword(newPassword, confirmation)) {
            NewPasswordValidation.Empty ->
                localError = context.getString(R.string.account_security_password_empty)
            NewPasswordValidation.TooShort ->
                localError = context.getString(R.string.account_security_password_short)
            NewPasswordValidation.Mismatch ->
                localError = context.getString(R.string.account_security_password_mismatch)
            NewPasswordValidation.Valid ->
                onConfirm(current, newPassword, hint)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (disable) {
                    Text(
                        stringResource(R.string.account_security_disable_warning),
                        color = palette.warningText
                    )
                }
                if (hasPassword) {
                    SecretField(
                        value = current,
                        onValueChange = { current = it },
                        label = stringResource(R.string.account_security_current_password)
                    )
                }
                if (!disable) {
                    SecretField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = stringResource(R.string.account_security_new_password)
                    )
                    SecretField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = stringResource(R.string.account_security_confirm_password)
                    )
                    Text(
                        stringResource(R.string.account_security_password_rule),
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = hint,
                        onValueChange = { hint = it.take(120) },
                        label = { Text(stringResource(R.string.account_security_password_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.account_security_password_hint_public),
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                localError?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                }
                error?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { confirm() }) {
                Text(
                    stringResource(if (disable) R.string.account_security_2fa_disable else R.string.common_save),
                    color = if (disable) palette.danger else palette.accentInk
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun RecoveryEmailDialog(
    hasRecoveryEmailAddress: Boolean,
    remove: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String, email: String) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val title = when {
        remove -> R.string.account_security_email_remove
        hasRecoveryEmailAddress -> R.string.account_security_email_change
        else -> R.string.account_security_email_add
    }

    fun confirm() {
        localError = null
        if (password.isEmpty()) {
            localError = context.getString(R.string.account_security_current_password_required)
            return
        }
        if (!remove && !isValidRecoveryEmail(email)) {
            localError = context.getString(R.string.account_security_email_invalid)
            return
        }
        onConfirm(password, email)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (remove) {
                    Text(
                        stringResource(R.string.account_security_email_remove_warning),
                        color = palette.warningText
                    )
                } else {
                    Text(
                        stringResource(R.string.account_security_email_description),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim().take(254) },
                        label = { Text(stringResource(R.string.account_security_email_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SecretField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.account_security_current_password)
                )
                localError?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                }
                error?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { confirm() }) {
                Text(
                    stringResource(
                        if (remove) R.string.account_security_email_remove_confirm
                        else R.string.common_save
                    ),
                    color = if (remove) palette.danger else palette.accentInk
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun DeleteAccountDialog(
    hasPassword: Boolean,
    isPrimaryAccount: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    val palette = beyGramPalette()
    var word by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val expectedWord = stringResource(R.string.account_security_delete_confirm_word)
    val confirmed = matchesDeletionConfirmation(word, expectedWord) &&
        (!hasPassword || password.isNotEmpty())

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.account_security_delete_dialog_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.account_security_delete_warning),
                    color = palette.danger,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(
                        if (isPrimaryAccount) R.string.account_security_delete_local_note_primary
                        else R.string.account_security_delete_local_note_secondary
                    ),
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
                Text(
                    stringResource(R.string.account_security_delete_type_word, expectedWord),
                    color = palette.textPrimary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it.take(24) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (word.isNotBlank() && !matchesDeletionConfirmation(word, expectedWord)) {
                    Text(
                        stringResource(R.string.account_security_delete_word_mismatch),
                        color = palette.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.errorAnnouncement()
                    )
                }
                if (hasPassword) {
                    SecretField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.account_security_current_password)
                    )
                }
                error?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.danger)
                    Text(
                        stringResource(R.string.account_security_delete_busy),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && confirmed, onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.account_security_delete_confirm), color = palette.danger)
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
