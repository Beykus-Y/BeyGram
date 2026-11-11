package com.beykus.beygram.ui.screens.settings.yandex

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.yandex.YandexDeviceCode
import com.beykus.beygram.data.yandex.YandexMusicProvider
import com.beykus.beygram.data.yandex.YandexSignInPoll
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.screens.settings.ProviderSettingsHost
import com.beykus.beygram.ui.screens.settings.SettingsGroup
import com.beykus.beygram.ui.screens.settings.SettingsLink
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import com.beykus.beygram.ui.screens.settings.SettingsToggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings UI of the Yandex Music source. DEV build only; loaded by name from ProviderSettingsHosts. */
class YandexSettingsHost : ProviderSettingsHost {

    @Composable
    override fun ExperimentsEntries(onOpenSignIn: () -> Unit) {
        val context = LocalContext.current
        remember(context) { MusicProviders.yandex(context) } ?: return
        SettingsGroup(stringResource(R.string.yandex_settings_group)) {
            SettingsLink(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.yandex_settings_entry),
                onClick = onOpenSignIn
            )
        }
    }

    @Composable
    override fun SignInScreen(onBack: () -> Unit) {
        YandexSignInScreen(onBack)
    }
}

@Composable
private fun YandexSignInScreen(onBack: () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val provider = remember(context) { MusicProviders.yandex(context) as? YandexMusicProvider } ?: return
    val state by provider.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var consented by rememberSaveable { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<YandexDeviceCode?>(null) }
    var failure by remember { mutableStateOf<MusicProviderFailure?>(null) }

    // Polls the token endpoint at the service's interval until the user confirms, the code expires, or it fails.
    LaunchedEffect(code) {
        val current = code ?: return@LaunchedEffect
        var intervalMs = current.intervalSec * 1_000L
        val deadline = System.currentTimeMillis() + current.expiresInSec * 1_000L
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            when (val poll = provider.pollSignIn(current)) {
                YandexSignInPoll.Pending -> Unit
                YandexSignInPoll.SlowDown -> intervalMs += SLOW_DOWN_STEP_MS
                is YandexSignInPoll.Signed -> {
                    code = null
                    return@LaunchedEffect
                }
                is YandexSignInPoll.Failed -> {
                    failure = poll.failure
                    code = null
                    return@LaunchedEffect
                }
            }
        }
        failure = MusicProviderFailure.SessionExpired
        code = null
    }

    fun startSignIn() {
        failure = null
        starting = true
        scope.launch {
            when (val result = provider.startSignIn()) {
                is com.beykus.beygram.data.music.provider.ProviderResult.Success -> code = result.value
                is com.beykus.beygram.data.music.provider.ProviderResult.Failure -> failure = result.failure
            }
            starting = false
        }
    }

    val signedIn = state as? MusicProviderState.SignedIn
    val currentCode = code

    Column(
        Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileToolbar(stringResource(R.string.yandex_signin_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.yandex_signin_intro),
                color = palette.textSecondary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
            )
            when {
                signedIn != null -> {
                    val account = signedIn.account
                    SettingsGroup(stringResource(R.string.yandex_account_group)) {
                        Text(
                            account.displayName.ifBlank { stringResource(R.string.yandex_account_unnamed) },
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Text(
                            stringResource(
                                if (account.hasSubscription) R.string.yandex_account_subscription_on
                                else R.string.yandex_account_subscription_off
                            ),
                            color = palette.textSecondary, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                        )
                        SettingsRowDivider()
                        SettingsLink(
                            icon = Icons.Rounded.SwapHoriz,
                            title = stringResource(R.string.yandex_account_refresh),
                            onClick = { scope.launch { provider.refreshAccount() } }
                        )
                        SettingsRowDivider()
                        SettingsLink(
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.yandex_account_sign_out),
                            onClick = { scope.launch { provider.signOut() } }
                        )
                    }
                }
                currentCode != null -> {
                    SettingsGroup(stringResource(R.string.yandex_signin_code_title)) {
                        Text(
                            currentCode.userCode,
                            fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                        Text(
                            stringResource(R.string.yandex_signin_waiting),
                            color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                        SettingsRowDivider()
                        SettingsLink(
                            icon = Icons.Rounded.GraphicEq,
                            title = stringResource(R.string.yandex_signin_open),
                            onClick = { uriHandler.openUri(currentCode.verificationUrl) }
                        )
                    }
                }
                else -> {
                    SettingsGroup(stringResource(R.string.yandex_signin_group)) {
                        Text(
                            stringResource(R.string.yandex_signin_warning),
                            color = palette.textSecondary, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        SettingsRowDivider()
                        SettingsToggle(
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.yandex_signin_consent),
                            checked = consented,
                            onCheckedChange = { consented = it }
                        )
                        if (consented) {
                            SettingsRowDivider()
                            SettingsLink(
                                icon = Icons.Rounded.GraphicEq,
                                title = stringResource(
                                    if (failure == null) R.string.yandex_signin_start else R.string.yandex_signin_retry
                                ),
                                onClick = { if (!starting) startSignIn() }
                            )
                        }
                    }
                    failure?.let { reason ->
                        Text(
                            stringResource(failureMessage(reason)),
                            color = palette.textSecondary, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun failureMessage(failure: MusicProviderFailure): Int = when (failure) {
    MusicProviderFailure.Network -> R.string.yandex_error_network
    MusicProviderFailure.Unavailable -> R.string.yandex_error_unavailable
    else -> R.string.yandex_error_generic
}

private const val SLOW_DOWN_STEP_MS = 5_000L
