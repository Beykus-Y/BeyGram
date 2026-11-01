package com.beykus.beygram.media.bgmix

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val SUPPORTED_EXTENSIONS = setOf("wav", "mp3", "flac")

/** Copies a picked document into app storage: the native decoder needs a real file path, not a content URI. */
private fun copyToCache(context: Context, uri: Uri, dir: File): File? = try {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        .orEmpty()
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension !in SUPPORTED_EXTENSIONS) {
        null
    } else {
        dir.mkdirs()
        val target = File(dir, "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            null
        } else {
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            target
        }
    }
} catch (_: Exception) {
    null
}

/**
 * Experimental BG Mix panel, opened from Settings when the flag is on. It has its own transport and does not
 * replace the regular music player, so normal playback is unaffected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgMixSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { BgMixPlayer(File(context.filesDir, "bgmix-cache")) }
    val status by player.status.collectAsStateWithLifecycle()
    var available by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val unsupportedText = stringResource(R.string.bgmix_unsupported_files)

    DisposableEffect(player) {
        if (!player.start()) available = false
        onDispose { player.release() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.Default) {
                uris.mapNotNull { copyToCache(context, it, File(context.filesDir, "bgmix-input")) }
            }
            withContext(Dispatchers.Default) { files.forEach { player.addTrack(it) } }
            message = if (files.size < uris.size) unsupportedText else null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.bgmix_sheet_title))
            val statusText = when {
                !available -> stringResource(R.string.bgmix_unavailable)
                message != null -> message
                status?.lastError?.isNotEmpty() == true -> status?.lastError
                else -> status?.status
            }
            if (!statusText.isNullOrEmpty()) Text(statusText)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = available, onClick = { picker.launch(arrayOf("audio/*")) }) {
                    Text(stringResource(R.string.bgmix_add))
                }
                Button(enabled = available, onClick = { player.play() }) { Text(stringResource(R.string.bgmix_play)) }
                OutlinedButton(enabled = available, onClick = { player.pause() }) { Text(stringResource(R.string.bgmix_pause)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = available, onClick = { player.next() }) { Text(stringResource(R.string.bgmix_next)) }
                OutlinedButton(enabled = available, onClick = { player.stopAll() }) { Text(stringResource(R.string.bgmix_stop)) }
            }

            val queue = status?.queue.orEmpty()
            if (queue.isEmpty()) {
                Text(stringResource(R.string.bgmix_empty_queue))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(queue) { index, item ->
                        Text("${index + 1}. ${item.name} · ${item.state}${if (item.error.isNotEmpty()) " · ${item.error}" else ""}")
                    }
                }
            }
        }
    }
}
