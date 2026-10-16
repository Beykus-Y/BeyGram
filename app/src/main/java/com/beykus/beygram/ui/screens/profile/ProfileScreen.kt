package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.components.*
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.screens.chat.MediaViewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    state: ProfileState, isOwnProfile: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    onBack: () -> Unit, onRefresh: () -> Unit, onEdit: () -> Unit,
    setPhoto: suspend (OutgoingMedia) -> Result<Unit>,
    isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit,
    onNotifications: () -> Unit = {}
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val importer = remember { OutgoingMediaImporter(context) }
    var showPhoto by rememberSaveable(state.info?.id) { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<OutgoingMedia?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photoError by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparing = true; photoError = null
            try { pendingPhoto = importer.prepare(uri, asDocument = false, squarePhoto = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { photoError = error.message ?: "Не удалось подготовить фото" }
            finally { preparing = false }
        }
    }
    BackHandler { if (!saving && !preparing) onBack() }
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(if (isOwnProfile) "Мой профиль" else "Информация о чате", { if (!saving && !preparing) onBack() }) {
            TextButton(onClick = onRefresh, enabled = !state.isLoading && !saving) { Text("Обновить") }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            state.info?.let { info ->
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TelegramAvatar(info.initials, palette.telegramBlue, 112.dp, info.photo, observeFile, downloadFile,
                        Modifier.clickable(enabled = info.photo != null) { showPhoto = true })
                    Spacer(Modifier.height(16.dp))
                    Text(info.title, color = palette.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(info.subtitle(), color = palette.textSecondary, fontSize = 14.sp)
                    if (info.isVerified || info.isPremium) Text(listOfNotNull(if (info.isVerified) "Подтверждённый аккаунт" else null, if (info.isPremium) "Telegram Premium" else null).joinToString(" · "), color = palette.telegramBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    if (isOwnProfile) {
                        Spacer(Modifier.height(14.dp))
                        FilledTonalButton(onClick = onEdit, enabled = !state.isLoading && state.error == null && !saving && !preparing) { Text("Редактировать профиль") }
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !saving && !preparing) { Text(if (preparing) "Подготавливаем фото…" else "Изменить фотографию") }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(palette.surface)) {
                    if (info.phone.isNotBlank()) InfoRow("Номер телефона", "+${info.phone.removePrefix("+")}")
                    (info.usernames + info.username.takeIf { it.isNotBlank() }).filterNotNull().distinct().forEach { InfoRow("Имя пользователя", "@$it") }
                    if (info.bio.isNotBlank()) InfoRow(if (info.kind == ProfileKind.Group || info.kind == ProfileKind.Channel) "Описание" else "О себе", info.bio)
                    if (info.memberCount != null) InfoRow(if (info.kind == ProfileKind.Channel) "Подписчиков" else "Участников", info.memberCount.toString())
                    InfoRow("Тип", when (info.kind) {
                        ProfileKind.Person -> if (info.isSelf) "Ваш аккаунт" else "Личный чат"
                        ProfileKind.Bot -> "Бот"
                        ProfileKind.Group -> "Группа"
                        ProfileKind.Channel -> "Канал"
                        ProfileKind.Secret -> "Секретный чат"
                    })
                }
                if (isOwnProfile) TextButton(onClick = onNotifications, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Уведомления и фоновая работа") }
                if (isOwnProfile) Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Тёмная тема", color = palette.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
                }
            }
            if (state.info == null && !state.isLoading && state.error == null) Text("Сведения ещё не загружены", color = palette.textSecondary, modifier = Modifier.padding(24.dp))
            state.error?.let { Text(it, color = palette.warningText, modifier = Modifier.padding(20.dp)) }
            photoError?.let { Text(it, color = palette.warningText, modifier = Modifier.padding(20.dp)) }
        }
    }
    val photo = state.info?.photo
    if (showPhoto && photo != null) MediaViewer(MessageMedia(MediaKind.Photo, photo.bigFileId, photo.smallFileId, "Фото профиля", "image/jpeg"), observeFile, downloadFile, onViewed = {}, onClose = { showPhoto = false })
    pendingPhoto?.let { selected ->
        fun discard() { if (!saving) { pendingPhoto = null; scope.launch { importer.discard(selected) } } }
        AlertDialog(onDismissRequest = ::discard, title = { Text("Фотография профиля") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                LocalMediaImage(selected.path, Modifier.size(220.dp).clip(CircleShape))
                Text("Используется квадратная область по центру фото", fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                if (saving) CircularProgressIndicator(Modifier.padding(top = 12.dp).size(24.dp))
                photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            } },
            dismissButton = { TextButton(onClick = ::discard, enabled = !saving) { Text("Отмена") } },
            confirmButton = { TextButton(enabled = !saving, onClick = { scope.launch {
                saving = true; photoError = null
                try {
                    val result = setPhoto(selected)
                    if (result.isSuccess) pendingPhoto = null
                    else photoError = result.exceptionOrNull()?.message ?: "Не удалось обновить фото"
                } finally { saving = false }
            } }) { Text(if (saving) "Сохраняем…" else "Установить фото") } }
        )
    }
}

private fun ProfileInfo.subtitle(): String = when (kind) {
    ProfileKind.Group -> memberCount?.let { "Участников: $it" } ?: "Группа"
    ProfileKind.Channel -> memberCount?.let { "Подписчиков: $it" } ?: "Канал"
    ProfileKind.Secret -> "Секретный чат · $status"
    ProfileKind.Bot -> "Бот"
    ProfileKind.Person -> if (isSelf) "Ваш аккаунт Telegram" else status
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, color = palette.telegramBlue, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        SelectionContainer { Text(value, color = palette.textPrimary, fontSize = 16.sp) }
    }
}

@Composable
internal fun ProfileToolbar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹", fontSize = 28.sp) }
        Text(title, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun EditProfileScreen(info: ProfileInfo, bioLimit: Int, onBack: () -> Unit, save: suspend (ProfileEdit) -> Result<Unit>) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var first by rememberSaveable(info.id) { mutableStateOf(info.firstName) }
    var last by rememberSaveable(info.id) { mutableStateOf(info.lastName) }
    var username by rememberSaveable(info.id) { mutableStateOf(info.username) }
    var bio by rememberSaveable(info.id) { mutableStateOf(info.bio) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val changed = first.trim() != info.firstName || last.trim() != info.lastName || username.trim().removePrefix("@") != info.username || bio != info.bio
    val bioCount = bio.codePointCount(0, bio.length)
    fun back() { if (!saving) { if (changed) discard = true else onBack() } }
    BackHandler(onBack = ::back)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        ProfileToolbar("Редактирование", ::back) {
            TextButton(enabled = !saving && changed && first.isNotBlank() && bioCount <= bioLimit, onClick = { scope.launch {
                saving = true; error = null
                try {
                    val result = save(ProfileEdit(first, last, username, bio))
                    if (result.isSuccess) onBack()
                    else error = (result.exceptionOrNull()?.message ?: "Не удалось сохранить") + "\nУже принятые сервером изменения сохраняются."
                } finally { saving = false }
            } }) { Text(if (saving) "…" else "Сохранить") }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(first, { first = it.take(64) }, label = { Text("Имя") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), colors = beyGramTextFieldColors())
            OutlinedTextField(last, { last = it.take(64) }, label = { Text("Фамилия") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), colors = beyGramTextFieldColors())
            OutlinedTextField(username, { username = it.removePrefix("@").take(32) }, label = { Text("Имя пользователя") }, prefix = { Text("@") }, supportingText = { Text("Латинские буквы, цифры и _. По этому имени вас смогут найти.") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), colors = beyGramTextFieldColors())
            OutlinedTextField(bio, { bio = it }, label = { Text("О себе") }, supportingText = { Text("$bioCount / $bioLimit") }, isError = bioCount > bioLimit, minLines = 3, maxLines = 6, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = beyGramTextFieldColors())
            Text("Изменения сохранятся в вашем аккаунте Telegram после нажатия «Сохранить».", color = palette.textSecondary, fontSize = 13.sp)
            error?.let { Text(it, color = palette.warningText) }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Выйти без сохранения?") },
        confirmButton = { TextButton(onClick = onBack) { Text("Выйти") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Продолжить редактирование") } })
}
