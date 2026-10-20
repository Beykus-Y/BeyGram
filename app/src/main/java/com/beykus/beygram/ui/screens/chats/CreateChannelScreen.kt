package com.beykus.beygram.ui.screens.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/** Creates a broadcast channel; never creates it until the explicit button is pressed. */
@Composable
fun CreateChannelScreen(onBack: () -> Unit, create: suspend (String, String) -> Result<Long>, onCreated: (Long) -> Unit) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(busy) { /* Keep creation request visible until the server responds. */ }
    // This full-screen route is outside MainTabsScaffold. Account for the system cutout,
    // gesture navigation and IME instead of relying on padding provided by the tab bar.
    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        ProfileToolbar(stringResource(R.string.feature_create_channel), { if (!busy) onBack() })
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(title, { title = it.take(128) }, label = { Text(stringResource(R.string.feature_channel_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(description, { description = it.take(255) }, label = { Text(stringResource(R.string.feature_channel_description)) },
                modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !busy)
            Text(stringResource(R.string.feature_channel_private_note), color = palette.textSecondary)
            error?.let { Text(it, color = palette.danger) }
            Button(
                enabled = title.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        try { create(title, description).fold(onCreated, { error = it.userMessage(context, R.string.feature_action_failed) }) }
                        finally { busy = false }
                    }
                }
            ) { Text(stringResource(if (busy) R.string.common_saving else R.string.feature_create_channel)) }
        }
    }
}
