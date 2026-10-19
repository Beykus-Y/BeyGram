package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.DeviceSession
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ActiveSessionsScreen(
    onBack: () -> Unit, load: suspend () -> Result<List<DeviceSession>>,
    terminate: suspend (Long) -> Result<Unit>, terminateAll: suspend () -> Result<Unit>
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Long?>(null) }
    var all by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        load().fold({ sessions = it.sortedWith(compareByDescending<DeviceSession> { s -> s.current }.thenByDescending { s -> s.lastActive }); error = null },
            { error = it.localizedMessage ?: it.message })
        loading = false
    }
    Column {
        ProfileToolbar(stringResource(R.string.feature_sessions), onBack) {
            TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.common_refresh)) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = palette.danger, modifier = Modifier.padding(16.dp)) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sessions, key = { it.id }) { session ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(session.device.ifBlank { session.application }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (session.current) Text(stringResource(R.string.feature_current_device), color = palette.accent)
                        }
                        Text(listOf(session.application, session.location, session.ip).filter { it.isNotBlank() }.joinToString(" · "),
                            color = palette.textSecondary)
                        if (session.lastActive > 0) Text(DateFormat.getDateTimeInstance().format(Date(session.lastActive * 1000L)),
                            color = palette.textSecondary)
                        if (!session.current) TextButton(onClick = { target = session.id }) {
                            Text(stringResource(R.string.feature_terminate_session), color = palette.danger)
                        }
                    }
                }
            }
            if (sessions.any { !it.current }) item {
                OutlinedButton(onClick = { all = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feature_terminate_other_sessions), color = palette.danger)
                }
            }
        }
    }
    if (target != null || all) AlertDialog(
        onDismissRequest = { if (!busy) { target = null; all = false } },
        title = { Text(stringResource(R.string.feature_terminate_session)) },
        text = { Text(stringResource(if (all) R.string.feature_terminate_all_warning else R.string.feature_terminate_warning)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    val result = if (all) terminateAll() else terminate(target!!)
                    busy = false
                    result.fold({ target = null; all = false; refresh++ }, { error = it.localizedMessage ?: it.message })
                }
            }) { Text(stringResource(R.string.feature_confirm), color = palette.danger) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { target = null; all = false }) { Text(stringResource(R.string.common_cancel)) } }
    )
}
