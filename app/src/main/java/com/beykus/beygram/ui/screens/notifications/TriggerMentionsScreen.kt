package com.beykus.beygram.ui.screens.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.beykus.beygram.ui.components.BeyGramDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.triggers.TriggerMention
import com.beykus.beygram.notifications.TriggerWordsStore
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.screens.profile.formatDateTime
import com.beykus.beygram.ui.components.GlassScreenScaffold

/** Messages that matched a trigger word, newest first. Clearing removes only this device's records. */
@Composable
fun TriggerMentionsScreen(store: TriggerWordsStore, onBack: () -> Unit) {
    val palette = beyGramPalette()
    val mentions by store.mentions.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    // Text kept while an account's previews were on stays hidden once they are turned off.
    val previews = remember(mentions) { mentions.map { it.accountId }.distinct().associateWith { store.previewsEnabled(it) } }
    BackHandler(onBack = onBack)
    GlassScreenScaffold(
        modifier = Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing),
        toolbar = {
            ProfileToolbar(stringResource(R.string.trigger_mentions_title), onBack)
        }
    ) { chrome ->
        if (mentions.isEmpty()) {
            Text(
                stringResource(R.string.trigger_mentions_empty),
                color = palette.textSecondary,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = chrome.top + 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.trigger_mentions_clear), color = palette.danger)
                    }
                }
                items(mentions, key = { it.id }) { mention -> MentionRow(mention, showText = previews[mention.accountId] == true) }
            }
        }
    }
    if (confirmClear) {
        BeyGramDialog(
            onDismissRequest = { confirmClear = false },
            title = stringResource(R.string.trigger_mentions_clear_title),
            message = stringResource(R.string.trigger_mentions_clear_body),
            confirmText = stringResource(R.string.trigger_mentions_clear_confirm),
            onConfirm = {
                store.clearMentions()
                confirmClear = false
            },
            destructive = true,
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun MentionRow(mention: TriggerMention, showText: Boolean) {
    val palette = beyGramPalette()
    val meta = listOf(mention.senderName, formatDateTime(mention.dateSeconds.toInt()))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            mention.chatTitle.ifBlank { mention.senderName },
            color = palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(meta, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.trigger_mention_word, mention.triggers.joinToString(", ")),
            color = palette.accent,
            fontSize = 13.sp
        )
        Text(
            mention.snippet?.takeIf { showText } ?: stringResource(R.string.trigger_mention_hidden_text),
            color = palette.textPrimary,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
