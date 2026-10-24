package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.beykus.beygram.R
import com.beykus.beygram.security.AppLockManager
import com.beykus.beygram.security.AppLockPinCheck
import com.beykus.beygram.security.AppLockPolicy
import com.beykus.beygram.security.AppLockStore
import com.beykus.beygram.security.AppLockTimeout
import com.beykus.beygram.security.BiometricFailureKind
import com.beykus.beygram.security.BiometricResult
import com.beykus.beygram.security.BiometricSupport
import com.beykus.beygram.security.PinField
import com.beykus.beygram.security.rememberBiometricAuthenticator
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings for the optional local lock. Every action that weakens protection (turning the
 * lock off or replacing the PIN) first proves knowledge of the current PIN or a biometric.
 */
@Composable
internal fun AppLockSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val manager = remember(context) { AppLockManager.get(context.applicationContext) }
    val store = manager.store
    val settings by store.settings.collectAsStateWithLifecycle()
    val locked by manager.locked.collectAsStateWithLifecycle()
    val biometricAvailable = remember(context) { BiometricSupport.canAuthenticate(context) }

    var setupForEnable by remember { mutableStateOf(false) }
    var setupAfterConfirmation by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var confirmChangePin by remember { mutableStateOf(false) }

    // A lock must not leave a PIN setup/confirmation dialog (and its in-memory PIN) open.
    LaunchedEffect(locked) {
        if (locked) {
            setupForEnable = false
            setupAfterConfirmation = false
            confirmDisable = false
            confirmChangePin = false
        }
    }

    BackHandler(onBack = onBack)

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
    ) {
        ProfileToolbar(stringResource(R.string.app_lock_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.app_lock_intro),
                color = palette.textSecondary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )

            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.app_lock_enable_title),
                    hint = stringResource(R.string.app_lock_enable_hint),
                    checked = settings.isActive,
                    onChange = { checked ->
                        if (checked) setupForEnable = true else confirmDisable = true
                    },
                )
                if (settings.isActive) {
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(horizontal = 16.dp))
                    ActionRow(
                        title = stringResource(R.string.app_lock_change_pin),
                        hint = stringResource(R.string.app_lock_change_pin_hint),
                        onClick = { confirmChangePin = true },
                    )
                }
            }

            if (settings.isActive) {
                Text(
                    stringResource(R.string.app_lock_timeout_title),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp).semantics { heading() },
                )
                SettingsCard {
                    AppLockTimeout.entries.forEachIndexed { index, timeout ->
                        if (index > 0) {
                            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        TimeoutRow(
                            timeout = timeout,
                            selected = settings.timeout == timeout,
                            onSelect = { store.updateTimeout(timeout) },
                        )
                    }
                }

                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.app_lock_biometric_title),
                        hint = stringResource(
                            if (biometricAvailable) R.string.app_lock_biometric_hint
                            else R.string.app_lock_biometric_unavailable
                        ),
                        checked = biometricAvailable && settings.biometricEnabled,
                        enabled = biometricAvailable,
                        onChange = { store.updateBiometricEnabled(it) },
                    )
                }
            }

            Text(
                stringResource(R.string.app_lock_screen_section),
                color = palette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp).semantics { heading() },
            )
            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.app_lock_hide_recents_title),
                    hint = stringResource(R.string.app_lock_hide_recents_hint),
                    checked = settings.isActive || settings.hideRecents,
                    enabled = !settings.isActive,
                    onChange = store::updateHideRecents,
                )
            }
            if (settings.isActive) {
                Text(
                    stringResource(R.string.app_lock_hide_recents_locked_note),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            Text(
                stringResource(R.string.app_lock_note_local),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.app_lock_note_recovery),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (setupForEnable || setupAfterConfirmation) {
        SetupPinDialog(
            store = store,
            onDone = {
                setupForEnable = false
                setupAfterConfirmation = false
            },
            onDismiss = {
                setupForEnable = false
                setupAfterConfirmation = false
            },
        )
    }

    if (confirmChangePin) {
        ConfirmIdentityDialog(
            store = store,
            title = stringResource(R.string.app_lock_confirm_title),
            message = stringResource(R.string.app_lock_confirm_change_hint),
            onConfirmed = {
                confirmChangePin = false
                setupAfterConfirmation = true
            },
            onDismiss = { confirmChangePin = false },
        )
    }

    if (confirmDisable) {
        ConfirmIdentityDialog(
            store = store,
            title = stringResource(R.string.app_lock_confirm_disable_title),
            message = stringResource(R.string.app_lock_confirm_disable_hint),
            onConfirmed = {
                confirmDisable = false
                store.disable()
            },
            onDismiss = { confirmDisable = false },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ActionRow(title: String, hint: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TimeoutRow(timeout: AppLockTimeout, selected: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            stringResource(timeout.labelRes()),
            color = palette.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun AppLockTimeout.labelRes(): Int = when (this) {
    AppLockTimeout.Immediately -> R.string.app_lock_timeout_immediately
    AppLockTimeout.OneMinute -> R.string.app_lock_timeout_one_minute
    AppLockTimeout.FiveMinutes -> R.string.app_lock_timeout_five_minutes
}

@Composable
private fun SetupPinDialog(store: AppLockStore, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            pin = ""
            confirmation = ""
            errorText = null
        }
    }

    fun save() {
        if (saving) return
        if (!AppLockPolicy.isValidPin(pin)) {
            errorText = context.getString(R.string.app_lock_pin_invalid)
            return
        }
        if (pin != confirmation) {
            errorText = context.getString(R.string.app_lock_pin_mismatch)
            return
        }
        saving = true
        errorText = null
        scope.launch {
            try {
                store.setPin(pin).onSuccess { onDone() }.onFailure {
                    errorText = context.getString(R.string.app_lock_save_failed)
                }
            } finally {
                saving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.accent) },
        title = { Text(stringResource(R.string.app_lock_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.app_lock_setup_hint), color = palette.textSecondary)
                PinField(
                    value = pin,
                    onValueChange = { pin = it; errorText = null },
                    label = stringResource(R.string.app_lock_new_pin),
                    enabled = !saving,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                )
                PinField(
                    value = confirmation,
                    onValueChange = { confirmation = it; errorText = null },
                    label = stringResource(R.string.app_lock_confirm_pin),
                    enabled = !saving,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    onDone = ::save,
                )
                errorText?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && pin.isNotEmpty() && confirmation.isNotEmpty(), onClick = ::save) {
                Text(stringResource(if (saving) R.string.common_saving else R.string.app_lock_set_pin))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ConfirmIdentityDialog(
    store: AppLockStore,
    title: String,
    message: String,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lockoutMillis by remember { mutableLongStateOf(store.lockoutRemainingMillis()) }

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            pin = ""
            errorText = null
        }
    }

    val biometric = rememberBiometricAuthenticator { result ->
        when (result) {
            BiometricResult.Success -> {
                store.clearFailedAttempts()
                onConfirmed()
            }
            is BiometricResult.Failure -> when (result.kind) {
                BiometricFailureKind.Cancelled -> errorText = null
                BiometricFailureKind.Failed ->
                    errorText = context.getString(R.string.app_lock_biometric_failed)
                BiometricFailureKind.LockedOut ->
                    errorText = context.getString(R.string.app_lock_biometric_locked)
                BiometricFailureKind.Unavailable ->
                    errorText = context.getString(R.string.app_lock_biometric_unavailable_now)
            }
        }
    }
    val biometricAvailable = settings.biometricEnabled && biometric?.available == true

    LaunchedEffect(Unit) {
        while (true) {
            lockoutMillis = store.lockoutRemainingMillis()
            delay(1_000L)
        }
    }

    fun verify() {
        if (checking || lockoutMillis > 0L) return
        if (!AppLockPolicy.isValidPin(pin)) {
            errorText = context.getString(R.string.app_lock_pin_invalid)
            return
        }
        biometric?.cancel()
        checking = true
        errorText = null
        scope.launch {
            try {
                when (val result = store.verifyPin(pin)) {
                    AppLockPinCheck.Success -> onConfirmed()
                    is AppLockPinCheck.Failure -> {
                        pin = ""
                        lockoutMillis = result.lockoutMillis
                        errorText = when {
                            result.lockoutMillis > 0L -> null
                            result.attemptsLeft > 0 ->
                                context.getString(R.string.app_lock_wrong_pin, result.attemptsLeft)
                            else -> context.getString(R.string.app_lock_wrong_pin_last)
                        }
                    }
                    is AppLockPinCheck.LockedOut -> {
                        pin = ""
                        lockoutMillis = result.remainingMillis
                    }
                }
            } finally {
                checking = false
            }
        }
    }

    val shownError = if (lockoutMillis > 0L) context.getString(
        R.string.app_lock_locked_out,
        ((lockoutMillis + 999L) / 1_000L).coerceAtLeast(1L),
    ) else errorText

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        icon = { Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = palette.accent) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, color = palette.textSecondary)
                PinField(
                    value = pin,
                    onValueChange = { pin = it; errorText = null },
                    label = stringResource(R.string.app_lock_pin_label),
                    enabled = !checking && lockoutMillis == 0L,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    onDone = ::verify,
                )
                if (biometricAvailable) {
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            errorText = null
                            biometric?.authenticate(
                                context.getString(R.string.app_lock_biometric_prompt_title),
                                context.getString(R.string.app_lock_biometric_prompt_subtitle),
                                context.getString(R.string.app_lock_use_pin),
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_lock_biometric_button), color = palette.accent)
                    }
                }
                shownError?.let {
                    Text(
                        it,
                        color = palette.danger,
                        fontSize = 13.sp,
                        // Do not re-announce the lockout countdown every second.
                        modifier = if (lockoutMillis > 0L) Modifier else Modifier.errorAnnouncement(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !checking && lockoutMillis == 0L && pin.isNotEmpty(),
                onClick = ::verify,
            ) {
                Text(stringResource(if (checking) R.string.app_lock_checking else R.string.app_lock_confirm_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !checking, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
