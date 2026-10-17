package com.beykus.beygram.ui.screens.start

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdLibReadiness
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve

/** Branded startup while TDLib restores authorization; login is shown only after its response. */
@Composable
fun StartScreen(
    tdLibReadiness: TdLibReadiness,
    authState: AuthState,
    onProxySettings: () -> Unit,
    onStartClick: () -> Unit
) {
    val palette = beyGramPalette()
    val transition = rememberInfiniteTransition(label = "startup logo")
    val scale by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo breathing"
    )
    val restoring = tdLibReadiness.canCreateClient &&
        (authState == AuthState.Idle || authState == AuthState.Loading || authState == AuthState.Ready)
    Box(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.app_logo), stringResource(R.string.app_name),
                Modifier.size(184.dp).graphicsLayer { scaleX = scale; scaleY = scale })
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.app_name), color = palette.textPrimary,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            if (restoring) {
                CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
            } else {
                val failure = (authState as? AuthState.Error)?.text
                if (failure != null) Text(failure.resolve(), color = palette.warningText)
                if (!tdLibReadiness.canCreateClient) Text(tdLibReadiness.issues.map { it.resolve() }.joinToString("\n"), color = palette.warningText)
                if (tdLibReadiness.canCreateClient) Button(onClick = onStartClick) { Text(stringResource(R.string.auth_sign_in)) }
                TextButton(onClick = onProxySettings) { Text(stringResource(R.string.start_proxy_settings)) }
            }
        }
    }
}
