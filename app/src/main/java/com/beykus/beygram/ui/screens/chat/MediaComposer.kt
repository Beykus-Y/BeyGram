package com.beykus.beygram.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AttachmentButton(
    chatId: Long,
    caption: String,
    onCaptionChange: (String) -> Unit,
    sendMedia: suspend (OutgoingMedia, String) -> Result<Unit>,
    captionLimit: Int = 1024,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val importer = remember { OutgoingMediaImporter(context) }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var pending by rememberSaveable(chatId) { mutableStateOf(arrayListOf<OutgoingMedia>()) }
    var importing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }

    fun prepare(uris: List<android.net.Uri>, asDocument: Boolean) {
        if (uris.isEmpty()) return
        if (uris.size > 10) { error = "Выберите не более 10 вложений за раз"; return }
        importJob = scope.launch {
            importing = true
            error = null
            try {
                for (uri in uris) {
                    val prepared = importer.prepare(uri, asDocument)
                    pending = ArrayList(pending + prepared)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Не удалось подготовить вложение. Попробуйте отправить его как файл." }
            finally { importing = false }
        }
    }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { prepare(it, false) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { prepare(it, true) }

    IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.size(44.dp).semantics { contentDescription = "Прикрепить медиа или файл" }) {
        Text("+", fontSize = 28.sp)
    }
    if (menu) AlertDialog(
        onDismissRequest = { menu = false }, title = { Text("Прикрепить") },
        text = {
            Column {
                TextButton(onClick = { menu = false; photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Text("Фото и видео") }
                TextButton(onClick = { menu = false; documents.launch(arrayOf("*/*")) }) { Text("Файл без изменений") }
            }
        }, confirmButton = { TextButton(onClick = { menu = false }) { Text("Закрыть") } }
    )

    fun discardAll() {
        if (sending) return
        importJob?.cancel()
        val discarded = pending.toList()
        pending = arrayListOf()
        importing = false
        error = null
        scope.launch { discarded.forEach { importer.discard(it) } }
    }

    if (pending.isNotEmpty() || importing || error != null) AlertDialog(
        onDismissRequest = ::discardAll,
        title = { Text(if (importing) "Подготавливаем вложения…" else "Отправить вложения (${pending.size})") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pending.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.thumbnailPath != null) LocalMediaImage(item.thumbnailPath, Modifier.size(52.dp), fit = true)
                        else Text("▤", modifier = Modifier.width(40.dp), fontSize = 24.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(item.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text("${item.kind.label()} · ${formatMediaSize(item.size)}", fontSize = 11.sp)
                        }
                        TextButton(enabled = !sending && !importing, onClick = {
                            pending = ArrayList(pending.filterNot { it.path == item.path })
                            scope.launch { importer.discard(item) }
                        }) { Text("×") }
                    }
                }
                if (importing || sending) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                OutlinedTextField(value = caption, onValueChange = onCaptionChange, modifier = Modifier.fillMaxWidth(), label = { Text("Подпись") }, supportingText = { Text("${caption.codePointCount(0, caption.length)} / $captionLimit") }, isError = caption.codePointCount(0, caption.length) > captionLimit, maxLines = 4, enabled = !sending)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        dismissButton = { TextButton(onClick = ::discardAll, enabled = !sending) { Text("Отмена") } },
        confirmButton = {
            TextButton(enabled = pending.isNotEmpty() && !importing && !sending && caption.codePointCount(0, caption.length) <= captionLimit, onClick = {
                scope.launch {
                    sending = true
                    error = null
                    var nextCaption = caption
                    try {
                        for (item in pending.toList()) {
                            val result = sendMedia(item, nextCaption)
                            if (result.isFailure) { error = result.exceptionOrNull()?.message ?: "Не удалось отправить вложение"; break }
                            pending = ArrayList(pending.filterNot { it.path == item.path })
                            nextCaption = ""
                            onCaptionChange("")
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Не удалось отправить вложение" }
                    finally { sending = false }
                }
            }) { Text(if (sending) "Отправляем…" else "Отправить") }
        }
    )
}
