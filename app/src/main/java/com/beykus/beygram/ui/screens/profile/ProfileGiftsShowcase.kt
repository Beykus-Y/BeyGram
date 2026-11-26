package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.sortProfileShowcaseGifts
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Horizontal gifts showcase for a user profile (#1144, #1135). Works in both Profile A and experimental Profile C
 * without depending on Profile C completion or synthetic personal colors.
 */
@Composable
internal fun ProfileGiftsShowcase(
    gifts: List<ReceivedGiftItem>,
    onOpenAllGifts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showcase = remember(gifts) { sortProfileShowcaseGifts(gifts, limit = 6) }
    if (showcase.isEmpty()) return
    val palette = beyGramPalette()
    var detailGift by remember { mutableStateOf<ReceivedGiftItem?>(null) }

    ProvideGiftAnimationBudget(maxActive = 2) {
        Column(
            modifier.fillMaxWidth().profileCardSurface().padding(vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.gifts_showcase_title),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onOpenAllGifts, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.gifts_showcase_all))
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(showcase, key = { it.id.ifBlank { "${it.dateSeconds}:${it.emoji}" } }) { gift ->
                    Column(
                        Modifier
                            .clickable(role = Role.Button) { detailGift = gift }
                            .semantics(mergeDescendants = true) {}
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            GiftArtworkFrame(
                                visual = gift.visual,
                                emoji = gift.emoji,
                                collectible = gift.collectible,
                                frameSize = 64.dp,
                                stickerSize = 46.dp,
                                symbolSize = 16.dp,
                                cornerRadius = 14.dp
                            )
                            if (gift.isPinned) {
                                Icon(
                                    Icons.Rounded.PushPin,
                                    contentDescription = stringResource(R.string.gifts_pinned),
                                    tint = palette.accentInk,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp)
                                )
                            }
                        }
                        val label = gift.collectible?.let { "#${it.number}" }
                            ?: gift.name.ifBlank { stringResource(R.string.gifts_gift_generic) }
                        Text(
                            label,
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    detailGift?.let { gift ->
        GiftDetailSheet(target = GiftDetailTarget.Received(gift), onDismiss = { detailGift = null })
    }
}
