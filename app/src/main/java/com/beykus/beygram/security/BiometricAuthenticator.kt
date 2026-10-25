package com.beykus.beygram.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal enum class BiometricFailureKind { Cancelled, Failed, LockedOut, Unavailable }

internal sealed interface BiometricResult {
    data object Success : BiometricResult
    data class Failure(val kind: BiometricFailureKind) : BiometricResult
}

internal object BiometricSupport {
    fun canAuthenticate(context: Context): Boolean = runCatching {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)
}

/**
 * Thin wrapper around [BiometricPrompt]. Biometrics are only a convenience: every caller
 * also keeps the PIN path available, so a cancelled or unavailable sensor never locks anyone out.
 */
internal class BiometricAuthenticator(
    private val activity: FragmentActivity,
    private val onResult: (BiometricResult) -> Unit,
) {
    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(
                    BiometricResult.Failure(
                        when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED -> BiometricFailureKind.Cancelled

                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricFailureKind.LockedOut

                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> BiometricFailureKind.Unavailable

                            else -> BiometricFailureKind.Failed
                        }
                    )
                )
            }

            // A single unreadable finger is not an error: the system prompt stays open.
            override fun onAuthenticationFailed() {
                onResult(BiometricResult.Failure(BiometricFailureKind.Failed))
            }
        }
    )

    val available: Boolean get() = BiometricSupport.canAuthenticate(activity)

    fun authenticate(title: String, subtitle: String, negativeButton: String) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButton)
            .setConfirmationRequired(false)
            .build()
        // Some OEM implementations throw instead of delivering an error callback.
        runCatching { prompt.authenticate(info) }
            .onFailure { onResult(BiometricResult.Failure(BiometricFailureKind.Unavailable)) }
    }

    fun cancel() {
        prompt.cancelAuthentication()
    }
}

/**
 * Builds a prompt tied to the host FragmentActivity. The caller still decides whether
 * biometrics are enabled in settings and when to show the prompt.
 */
@Composable
internal fun rememberBiometricAuthenticator(
    onResult: (BiometricResult) -> Unit,
): BiometricAuthenticator? {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val latestResult = rememberUpdatedState(onResult)
    val authenticator = remember(activity) {
        activity?.let { host ->
            runCatching {
                BiometricAuthenticator(host) { result -> latestResult.value(result) }
            }.getOrNull()
        }
    }
    DisposableEffect(authenticator) {
        onDispose { authenticator?.cancel() }
    }
    return authenticator
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return null
}
