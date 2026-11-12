package com.beykus.beygram.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape

/** Rows a source shows in the overview of the music search before «Ещё» opens its full list. */
internal const val MUSIC_SEARCH_PREVIEW_ROWS = 5

/**
 * One source in the music search: its heading and, in the overview, its rows in a group card. [onMore] adds «Ещё» to
 * the heading, which opens the full list of the source. [carded] false draws the rows on the screen itself, as the
 * full list of one source does.
 */
@Composable
internal fun MusicSearchSection(
    title: String,
    onMore: (() -> Unit)?,
    carded: Boolean = true,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        MusicGroupHeading(
            title = title,
            actionLabel = if (onMore != null) stringResource(R.string.music_search_more) else null,
            onAction = { onMore?.invoke() }
        )
        if (note != null) Text(
            note,
            color = beyGramPalette().textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 22.dp, end = MusicScreenGutter, bottom = 8.dp)
        )
        if (carded) MusicCardColumn(content) else Column(Modifier.fillMaxWidth(), content = content)
        Spacer(Modifier.height(8.dp))
    }
}

/** A line inside a search section: no results, a failure, or a source that is not reachable. */
@Composable
internal fun MusicSearchMessage(text: String) {
    Text(
        text,
        color = beyGramPalette().textSecondary,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

/** Gray stand-ins for track rows while a source searches, so the section keeps its shape. */
@Composable
internal fun MusicSearchSkeletonRows(count: Int = 3) {
    val block = beyGramPalette().textSecondary.copy(alpha = 0.12f)
    repeat(count) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(styledShape(12.dp)).background(block))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(CircleShape).background(block))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(CircleShape).background(block))
            }
        }
    }
}
