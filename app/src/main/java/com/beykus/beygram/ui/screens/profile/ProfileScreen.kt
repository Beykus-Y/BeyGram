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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    if (pendingPhoto == null) Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(if (isOwnProfile) "Мой профиль" else "Информация о чате", { if (!saving && !preparing) onBack() }) {
            IconButton(onClick = onRefresh, enabled = !state.isLoading && !saving) { Icon(Icons.Rounded.Refresh, "Обновить", tint = palette.textSecondary) }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            state.info?.let { info ->
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TelegramAvatar(info.initials, palette.accent, 112.dp, info.photo, observeFile, downloadFile,
                        Modifier.clickable(enabled = info.photo != null) { showPhoto = true })
                    Spacer(Modifier.height(16.dp))
                    Text(info.title, color = palette.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(info.subtitle(), color = palette.textSecondary, fontSize = 14.sp)
                    if (info.isVerified || info.isPremium) Text(listOfNotNull(if (info.isVerified) "Подтверждённый аккаунт" else null, if (info.isPremium) "Telegram Premium" else null).joinToString(" · "), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    if (isOwnProfile) {
                        Spacer(Modifier.height(14.dp))
                        FilledTonalButton(onClick = onEdit, enabled = !state.isLoading && state.error == null && !saving && !preparing, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(46.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accentSoft, contentColor = palette.accentInk)) { Text("Редактировать профиль", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !saving && !preparing) { Text(if (preparing) "Подготавливаем фото…" else "Изменить фотографию", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)) {
                    val rows = buildList {
                        if (info.phone.isNotBlank()) add("Номер телефона" to "+${info.phone.removePrefix("+")}")
                        (info.usernames + info.username.takeIf { it.isNotBlank() }).filterNotNull().distinct().forEach { add("Имя пользователя" to "@$it") }
                        if (info.bio.isNotBlank()) add((if (info.kind == ProfileKind.Group || info.kind == ProfileKind.Channel) "Описание" else "О себе") to info.bio)
                        if (info.memberCount != null) add((if (info.kind == ProfileKind.Channel) "Подписчиков" else "Участников") to info.memberCount.toString())
                        add("Тип" to when (info.kind) {
                            ProfileKind.Person -> if (info.isSelf) "Ваш аккаунт" else "Личный чат"
                            ProfileKind.Bot -> "Бот"
                            ProfileKind.Group -> "Группа"
                            ProfileKind.Channel -> "Канал"
                            ProfileKind.Secret -> "Секретный чат"
                        })
                    }
                    rows.forEachIndexed { index, (label, value) ->
                        if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 20.dp))
                        InfoRow(label, value)
                    }
                }
                if (isOwnProfile) TextButton(onClick = onNotifications, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { Text("Уведомления и фоновая работа", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (isOwnProfile) Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DarkMode, null, tint = palette.accent, modifier = Modifier.size(22.dp))
                    Text("Тёмная тема", color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
                }
            }
            if (state.info == null && !state.isLoading && state.error == null) Text("Сведения ещё не загружены", color = palette.textSecondary, modifier = Modifier.padding(24.dp))
            state.error?.let { Text(it, color = palette.danger, modifier = Modifier.padding(20.dp)) }
            photoError?.let { Text(it, color = palette.danger, modifier = Modifier.padding(20.dp)) }
        }
    }
    val photo = state.info?.photo
    if (showPhoto && photo != null) MediaViewer(MessageMedia(MediaKind.Photo, photo.bigFileId, photo.smallFileId, "Фото профиля", "image/jpeg"), observeFile, downloadFile, onViewed = {}, onClose = { showPhoto = false })
    pendingPhoto?.let { selected ->
        fun discard() { if (!saving) { pendingPhoto = null; scope.launch { importer.discard(selected) } } }
        BackHandler(onBack = ::discard)
        Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
            ProfileToolbar("Новое фото профиля", ::discard)
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                LocalMediaImage(selected.path, Modifier.size(240.dp).clip(CircleShape))
                Text("Предпросмотр · квадратная область по центру", color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                TextButton(enabled = !saving, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Выбрать другое", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (saving) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), color = palette.accent)
                photoError?.let { Text(it, color = palette.danger, modifier = Modifier.padding(top = 12.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = ::discard, enabled = !saving, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accentSoft, contentColor = palette.accentInk)) { Text("Отмена", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Button(enabled = !saving, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent), onClick = { scope.launch {
                        saving = true; photoError = null
                        try {
                            val result = setPhoto(selected)
                            if (result.isSuccess) pendingPhoto = null
                            else photoError = result.exceptionOrNull()?.message ?: "Не удалось обновить фото"
                        } finally { saving = false }
                    } }) { Text(if (saving) "Сохраняем…" else "Установить", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(label, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        SelectionContainer { Text(value, color = palette.textPrimary, fontSize = 16.sp, lineHeight = 22.sp) }
    }
}

@Composable
internal fun ProfileToolbar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = palette.textPrimary) }
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
    fun back() { if (!saving) { if (changed && !discard) discard = true else onBack() } }
    BackHandler(onBack = ::back)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        ProfileToolbar("Мой профиль", ::back) {
            TextButton(enabled = !saving && changed && first.isNotBlank() && bioCount <= bioLimit, onClick = { scope.launch {
                discard = false
                saving = true; error = null
                try {
                    val result = save(ProfileEdit(first, last, username, bio))
                    if (result.isSuccess) onBack()
                    else error = (result.exceptionOrNull()?.message ?: "Не удалось сохранить") + "\nУже принятые сервером изменения сохраняются."
                } finally { saving = false }
            } }) { Text(if (saving) "…" else "Сохранить") }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        if (discard) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(palette.warningSurface).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Есть несохранённые изменения", color = palette.warningText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Отменить", color = palette.warningText, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(first, { first = it.take(64) }, label = { Text("Имя") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = RoundedCornerShape(14.dp), colors = beyGramTextFieldColors())
            OutlinedTextField(last, { last = it.take(64) }, label = { Text("Фамилия") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = RoundedCornerShape(14.dp), colors = beyGramTextFieldColors())
            OutlinedTextField(username, { username = it.removePrefix("@").take(32) }, label = { Text("Имя пользователя") }, prefix = { Text("@") }, supportingText = { Text("Латинские буквы, цифры и _. По этому имени вас смогут найти.") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = RoundedCornerShape(14.dp), colors = beyGramTextFieldColors())
            OutlinedTextField(bio, { bio = it }, label = { Text("О себе") }, supportingText = { Text("$bioCount / $bioLimit") }, isError = bioCount > bioLimit, minLines = 3, maxLines = 6, enabled = !saving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = beyGramTextFieldColors())
            Text("Изменения сохранятся в вашем аккаунте Telegram после нажатия «Сохранить».", color = palette.textSecondary, fontSize = 13.sp)
            error?.let { Text(it, color = palette.danger) }
        }
    }
}
