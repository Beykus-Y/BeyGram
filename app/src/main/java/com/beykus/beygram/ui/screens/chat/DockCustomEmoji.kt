package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.playback.LocalPlaybackAccountId

internal data class DockCustomEmoji(
    val packs: List<CustomEmojiSetBrief>,
    val rows: List<PremiumEmojiRow>,
    val recentIds: List<Long>,
    val loadPack: (Long) -> Unit,
    val failed: Boolean,
    val retry: () -> Unit,
    val observeCustomEmoji: ObserveCustomEmoji,
    val observeFile: ObserveMediaFile,
    val downloadFile: DownloadMediaFile,
    val onPick: (CustomEmoji) -> Unit
)

@Composable
internal fun rememberDockCustomEmoji(
    conversation: ConversationRepository,
    observeFile: ObserveMediaFile,
    downloadFile: DownloadMediaFile,
    onPick: (CustomEmoji) -> Boolean
): DockCustomEmoji {
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val prefs = remember(context, accountId) {
        context.applicationContext.getSharedPreferences("emoji_panel", android.content.Context.MODE_PRIVATE)
    }
    val recentKey = "custom_recent_$accountId"
    var recents by remember(prefs, recentKey) {
        mutableStateOf(prefs.getString(recentKey, "").orEmpty().split(',')
            .mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0L } }.distinct().take(36))
    }
    var packs by remember(conversation) { mutableStateOf<List<CustomEmojiSetBrief>>(emptyList()) }
    var failed by remember(conversation) { mutableStateOf(false) }
    var attempt by remember(conversation) { mutableIntStateOf(0) }
    LaunchedEffect(conversation, attempt) {
        failed = false
        conversation.installedCustomEmojiSets().fold(
            onSuccess = { values -> packs = values.distinctBy { it.id } },
            onFailure = { failed = true }
        )
    }
    val scope = rememberCoroutineScope()
    val loader = remember(scope, conversation) { CustomEmojiSetLoader(scope, load = conversation::customEmojiSetIds) }
    val states by loader.states.collectAsStateWithLifecycle()
    val rows = remember(packs, states) { premiumEmojiRows(packs, states) }
    return DockCustomEmoji(
        packs, rows, recents, loader::request, failed, { attempt++ },
        conversation::observeCustomEmoji, observeFile, downloadFile
    ) { emoji ->
        if (onPick(emoji)) {
            recents = (listOf(emoji.customEmojiId) + recents.filterNot { it == emoji.customEmojiId }).take(36)
            prefs.edit().putString(recentKey, recents.joinToString(",")).apply()
        }
    }
}

@Composable
internal fun DockCustomEmojiCell(
    id: Long,
    dock: DockCustomEmoji,
    modifier: Modifier = Modifier,
    onClick: ((CustomEmoji) -> Unit)? = dock.onPick
) {
    val current by remember(id, dock.observeCustomEmoji) {
        dock.observeCustomEmoji(id)
    }.collectAsStateWithLifecycle()
    val emoji = current?.takeIf { it.customEmojiId == id }
    Box(
        modifier.height(48.dp).then(if (onClick != null && emoji != null) {
            Modifier.clickable(role = Role.Button) { onClick(emoji) }
        } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (emoji != null) CustomEmojiImage(emoji, 28.dp, dock.observeFile, dock.downloadFile) {
            Text(emoji.emoji, fontSize = 24.sp)
        }
    }
}
