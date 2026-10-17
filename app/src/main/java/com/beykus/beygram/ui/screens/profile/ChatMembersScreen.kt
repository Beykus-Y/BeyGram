package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMembersPage
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.ui.components.*
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val MEMBERS_PAGE = 50
/** Сколько строк до конца списка запускают загрузку следующей страницы. */
private const val MEMBERS_PREFETCH = 10

/** Полный список участников: страницы подгружаются по мере прокрутки. */
@Composable
fun ChatMembersScreen(loadPage: suspend (offset: Int, limit: Int) -> Result<ChatMembersPage>, onBack: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var members by remember { mutableStateOf<List<ProfileMember>>(emptyList()) }
    var total by remember { mutableStateOf<Int?>(null) }
    var nextOffset by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun loadNext() {
        if (loading || total?.let { nextOffset >= it } == true) return
        loading = true
        error = null
        scope.launch {
            loadPage(nextOffset, MEMBERS_PAGE)
                .onSuccess { page ->
                    members = members + page.members
                    nextOffset += page.fetched
                    // Пустая страница означает конец списка, даже если TDLib назвал другое число.
                    total = if (page.fetched == 0) nextOffset else page.total
                }
                .onFailure { error = it.userMessage(context, R.string.profile_members_load_failed) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadNext() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= members.size - MEMBERS_PREFETCH) loadNext()
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.profile_members_title), onBack)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        error?.let { message ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(message, color = palette.danger)
                TextButton(onClick = ::loadNext) { Text(stringResource(R.string.common_retry), fontWeight = FontWeight.SemiBold) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            itemsIndexed(members) { _, member ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name.ifBlank { stringResource(R.string.user_name_unknown) }, color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(memberStatus(member), color = palette.textSecondary, fontSize = 13.sp)
                }
                HorizontalDivider(color = palette.divider)
            }
        }
    }
}
