package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.ui.components.beyGramPalette

/** A forum is a list of independent conversations, not one merged group message timeline. */
@Composable
fun ForumTopicsScreen(
    chatTitle: String,
    topics: List<ForumTopic>,
    onBack: () -> Unit,
    onTopic: (ForumTopic) -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().background(palette.surface).padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(chatTitle, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.forum_topics_subtitle), color = palette.textSecondary, fontSize = 13.sp)
            }
        }
        HorizontalDivider(color = palette.divider)
        if (topics.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.forum_topics_empty), color = palette.textSecondary)
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(topics, key = { it.id }) { topic ->
                Row(
                    Modifier.fillMaxWidth().clickable { onTopic(topic) }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                            .background(palette.accentSoft),
                        contentAlignment = Alignment.Center
                    ) { Text(if (topic.isPinned) "📌" else "💬", fontSize = 22.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(topic.title, color = palette.textPrimary, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subtitle = when {
                            topic.isClosed -> stringResource(R.string.forum_topic_closed)
                            topic.lastMessagePreview.isNotBlank() -> topic.lastMessagePreview
                            else -> stringResource(R.string.forum_topic_no_messages)
                        }
                        Text(subtitle, color = palette.textSecondary, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (topic.unreadCount > 0) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(palette.accent)
                            .padding(horizontal = 9.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(topic.unreadCount.toString(), color = palette.onAccent, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 77.dp))
            }
        }
    }
}
