package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChannelTopPost
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.text.resolve

/**
 * Recent posts with the metrics TDLib returns for them. A row opens the post; the list is TDLib's short list of recent
 * interactions, so the heading says that it is not the whole period.
 */
@Composable
internal fun TopPostsSection(
    posts: List<ChannelTopPost>,
    palette: BeyGramPalette,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val inset = statisticsInset()
    Column(modifier.fillMaxWidth().padding(top = 18.dp)) {
        StatisticsSectionHeader(stringResource(R.string.channel_statistics_top_posts))
        Text(
            stringResource(R.string.channel_statistics_top_posts_hint),
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (posts.isEmpty()) {
            Text(
                stringResource(R.string.channel_statistics_top_posts_empty),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
            )
        } else {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).statisticsGroupSurface()
                    .padding(horizontal = inset, vertical = 4.dp)
            ) {
                posts.forEachIndexed { index, post ->
                    if (index > 0) HorizontalDivider(color = palette.divider)
                    TopPostRow(post, palette, onOpenPost)
                }
            }
        }
    }
}

@Composable
private fun TopPostRow(
    post: ChannelTopPost,
    palette: BeyGramPalette,
    onOpenPost: (Long) -> Unit
) {
    val openLabel = stringResource(R.string.channel_statistics_open_post)
    val preview = post.preview.resolve().trim()
        .ifBlank { stringResource(R.string.channel_statistics_post_fallback, post.messageId) }
    val views = stringResource(R.string.channel_statistics_post_views, formatCount(post.viewCount))
    val forwards = stringResource(R.string.channel_statistics_post_forwards, formatCount(post.forwardCount))
    val reactions = stringResource(R.string.channel_statistics_post_reactions, formatCount(post.reactionCount))

    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = openLabel) { onOpenPost(post.messageId) }
            .padding(vertical = 8.dp)
    ) {
        Text(
            preview,
            color = palette.textPrimary,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (post.date > 0) {
            Text(
                formatDateTime(post.date),
                color = palette.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            "$views · $forwards · $reactions",
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
