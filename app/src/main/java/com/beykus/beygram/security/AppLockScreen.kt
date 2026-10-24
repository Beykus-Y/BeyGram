package com.beykus.beygram.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modal, full-screen window used while the app is locked. A separate window keeps the chat
 * composition (and its in-flight coroutines) alive while taking input focus away from it.
 */
@Composable
internal fun AppLockDialog(manager: AppLockManager) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
    ) {
        AppLockScreen(manager)
    }
}

/**
 * Full-screen gate shown instead of the chat UI. It deliberately contains no account or chat
 * identity, so a locked device reveals only that BeyGram is protected.
 */
@Composable
internal fun AppLockScreen(manager: AppLockManager) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val store = manager.store
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Never rememberSaveable: a PIN must not be written into the Activity saved-state Bundle.
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var autoPrompted by remember { mutableStateOf(false) }
    var lockoutMillis by remember { mutableLongStateOf(store.lockoutRemainingMillis()) }

    // Do not keep a typed PIN in a stopped composition.
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            pin = ""
            errorText = null
        }
    }

    val biometricEnabled = settings.biometricEnabled
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val biometric = rememberBiometricAuthenticator { result ->
        when (result) {
            BiometricResult.Success -> {
                store.clearFailedAttempts()
                manager.unlock()
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
    val biometricAvailable = biometricEnabled && biometric?.available == true

    LaunchedEffect(Unit) {
        while (true) {
            lockoutMillis = store.lockoutRemainingMillis()
            delay(1_000L)
        }
    }

    // Offer biometrics once when the lock appears. Cancelling is silent; the PIN field stays.
    LaunchedEffect(biometric, biometricAvailable, settings.isActive) {
        if (settings.isActive && !autoPrompted && biometricAvailable) {
            autoPrompted = true
            delay(350L)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                biometric?.authenticate(
                    context.getString(R.string.app_lock_biometric_prompt_title),
                    context.getString(R.string.app_lock_biometric_prompt_subtitle),
                    context.getString(R.string.app_lock_use_pin),
                )
            }
        }
    }

    val lockedOut = lockoutMillis > 0L
    val shownError = when {
        lockedOut -> context.getString(
            R.string.app_lock_locked_out,
            ((lockoutMillis + 999L) / 1_000L).coerceAtLeast(1L),
        )
        else -> errorText
    }

    fun submitPin() {
        if (checking || lockedOut) return
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
                    AppLockPinCheck.Success -> {
                        pin = ""
                        manager.unlock()
                    }
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

    if (!settings.isActive) {
        // Defensive: disabling the lock elsewhere must never leave an unusable gate on screen.
        LaunchedEffect(Unit) { manager.unlock() }
        return
    }

    Box(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier.size(76.dp).background(palette.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.app_lock_lock_icon_description),
                    tint = palette.accent,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.app_lock_locked_title),
                color = palette.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_lock_locked_hint),
                color = palette.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin = value.filter { it in '0'..'9' }.take(AppLockPolicy.MAX_PIN_LENGTH)
                    if (!lockedOut) errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking,
                label = { Text(stringResource(R.string.app_lock_pin_label)) },
                singleLine = true,
                isError = shownError != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submitPin() }),
                shape = RoundedCornerShape(14.dp),
                colors = beyGramTextFieldColors(),
            )
            if (shownError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    shownError,
                    color = palette.danger,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    // The countdown changes every second; announcing it again and again would
                    // flood TalkBack. Static PIN errors are announced once.
                    modifier = if (lockedOut) Modifier.fillMaxWidth()
                    else Modifier.fillMaxWidth().errorAnnouncement(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = ::submitPin,
                enabled = !checking && !lockedOut && pin.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.disabled,
                    disabledContentColor = palette.textSecondary,
                ),
            ) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.onAccent)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.app_lock_checking))
                } else {
                    Text(stringResource(R.string.app_lock_unlock))
                }
            }
            if (biometricAvailable) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        errorText = null
                        biometric?.authenticate(
                            context.getString(R.string.app_lock_biometric_prompt_title),
                            context.getString(R.string.app_lock_biometric_prompt_subtitle),
                            context.getString(R.string.app_lock_use_pin),
                        )
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.app_lock_biometric_button), color = palette.accent)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.app_lock_note_local),
                color = palette.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
