package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.DeviceSession
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Sessions are Telegram authorizations returned by TDLib, not local Android processes.
 * Keep the current session separate; never offer to terminate it via the other-devices action.
 */
@Composable
fun ActiveSessionsScreen(
    onBack: () -> Unit,
    load: suspend () -> Result<List<DeviceSession>>,
    terminate: suspend (Long) -> Result<Unit>,
    terminateAll: suspend () -> Result<Unit>
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Long?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        load().fold(
            { sessions = it.sortedWith(compareByDescending<DeviceSession> { s -> s.current }.thenByDescending { s -> s.lastActive }) },
            { error = it.userMessage(context, R.string.feature_action_failed) }
        )
        loading = false
    }

    val current = sessions.firstOrNull { it.current }
    val others = sessions.filterNot { it.current }
    fun closeDialog() {
        target = null
        confirmAll = false
        actionError = null
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileToolbar(stringResource(R.string.feature_sessions), onBack) {
            IconButton(enabled = !loading && !busy, onClick = { refresh++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = palette.textPrimary)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.sessions_intro),
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            error?.let { message ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(enabled = !loading, onClick = { refresh++ }) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }
                }
            }
            if (!loading && sessions.isEmpty() && error == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.sessions_empty), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.common_refresh)) }
                    }
                }
            }
            current?.let { session ->
                item { SessionSectionTitle(stringResource(R.string.sessions_current), palette.textSecondary) }
                item { DeviceSessionRow(session, current = true, onTerminate = {}, enabled = !busy) }
            }
            if (others.isNotEmpty()) {
                item { SessionSectionTitle(stringResource(R.string.sessions_other_count, others.size), palette.textSecondary) }
                items(others, key = { it.id }) { session ->
                    DeviceSessionRow(session, current = false, onTerminate = { target = session.id; actionError = null }, enabled = !busy)
                }
                item {
                    OutlinedButton(
                        onClick = { confirmAll = true; actionError = null },
                        enabled = !busy && !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.feature_terminate_other_sessions), color = palette.danger)
                    }
                }
            }
        }
    }

    if (target != null || confirmAll) {
        val chosen = others.firstOrNull { it.id == target }
        AlertDialog(
            onDismissRequest = { if (!busy) closeDialog() },
            title = { Text(stringResource(if (confirmAll) R.string.feature_terminate_other_sessions else R.string.feature_terminate_session)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!confirmAll) Text(chosen?.device?.ifBlank { chosen.application } ?: stringResource(R.string.sessions_unknown_device), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (confirmAll) R.string.feature_terminate_all_warning else R.string.feature_terminate_warning))
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val allOthers = confirmAll
                    val id = target
                    if (!allOthers && id == null) return@TextButton
                    scope.launch {
                        busy = true
                        actionError = null
                        try {
                            val result = if (allOthers) terminateAll() else terminate(requireNotNull(id))
                            result.fold(
                                { closeDialog(); refresh++ },
                                { actionError = it.userMessage(context, R.string.feature_action_failed) }
                            )
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.feature_confirm), color = palette.danger) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = ::closeDialog) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun SessionSectionTitle(title: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        title,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun DeviceSessionRow(
    session: DeviceSession,
    current: Boolean,
    onTerminate: () -> Unit,
    enabled: Boolean
) {
    val palette = beyGramPalette()
    val unknown = stringResource(R.string.sessions_unknown_device)
    val title = session.device.ifBlank { session.application.ifBlank { unknown } }
    val detail = listOf(session.application.takeIf { it.isNotBlank() && it != title }, session.location.takeIf { it.isNotBlank() }, session.ip.takeIf { it.isNotBlank() })
        .filterNotNull().joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).background(palette.accentSoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = palette.accentInk)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (current) Text(stringResource(R.string.feature_current_device), color = palette.accentInk, style = MaterialTheme.typography.labelMedium)
                if (detail.isNotBlank()) Text(detail, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                if (!current && session.lastActive > 0) {
                    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.lastActive * 1000L))
                    Text(stringResource(R.string.sessions_last_active, date), color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (!current) {
                    TextButton(onClick = onTerminate, enabled = enabled, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.feature_terminate_session), color = palette.danger)
                    }
                }
            }
        }
    }
}
