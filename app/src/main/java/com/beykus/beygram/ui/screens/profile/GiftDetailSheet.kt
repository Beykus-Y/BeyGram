package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.GiftRarityTier
import com.beykus.beygram.domain.model.GiftVisual
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.ResaleGiftItem
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal sealed interface GiftDetailTarget {
    data class Received(val gift: ReceivedGiftItem) : GiftDetailTarget
    data class Resale(val gift: ResaleGiftItem) : GiftDetailTarget
}

/**
 * Interactive detail view for a received or resale gift. Shows the real artwork (with tap-to-pause/replay)
 * and only the attributes actually present in TDLib, never substituting placeholder values.
 */
@Composable
internal fun GiftDetailSheet(
    target: GiftDetailTarget,
    actionBusy: Boolean = false,
    actionError: String? = null,
    onToggleSaved: (() -> Unit)? = null,
    onTogglePinned: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    var paused by remember(target) { mutableStateOf(false) }

    val visual: GiftVisual?
    val emoji: String
    val collectible: GiftCollectibleDetails?
    val title: String
    val starCount: Long?
    val senderName: String?
    val dateSeconds: Int?
    val messageText: String
    val badges: List<Pair<String, Boolean>>

    when (target) {
        is GiftDetailTarget.Received -> {
            val g = target.gift
            visual = g.visual
            emoji = g.emoji
            collectible = g.collectible
            title = g.name.ifBlank {
                g.collectible?.title.orEmpty().ifBlank { stringResource(R.string.gifts_gift_generic) }
            }
            starCount = g.starCount
            senderName = g.senderName ?: stringResource(R.string.gifts_sender_unknown)
            dateSeconds = g.dateSeconds
            messageText = g.text
            badges = buildList {
                if (g.isPinned) add(stringResource(R.string.gifts_pinned) to false)
                if (g.isUpgraded) add(stringResource(R.string.gifts_upgraded) to false)
                if (g.isSaved) add(stringResource(R.string.gifts_saved) to false)
                if (g.isPrivate) add(stringResource(R.string.gifts_private) to false)
                if (g.wasRefunded) add(stringResource(R.string.gifts_refunded) to true)
            }
        }
        is GiftDetailTarget.Resale -> {
            val g = target.gift
            visual = g.visual
            emoji = g.emoji
            collectible = g.collectible
            title = g.title.ifBlank { stringResource(R.string.gifts_gift_generic) }
            starCount = g.starCount
            senderName = null
            dateSeconds = null
            messageText = ""
            badges = emptyList()
        }
    }

    val toggleLabel = stringResource(R.string.gifts_detail_toggle_animation)

    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                collectible?.let { details ->
                    if (details.number > 0) {
                        Text(
                            stringResource(R.string.gifts_collectible_number, details.number),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                GiftArtworkFrame(
                    visual = visual,
                    emoji = emoji,
                    collectible = collectible,
                    frameSize = 128.dp,
                    stickerSize = 100.dp,
                    symbolSize = 26.dp,
                    cornerRadius = 24.dp,
                    staticOnly = paused,
                    modifier = Modifier
                        .clickable(role = Role.Button) { paused = !paused }
                        .semantics { contentDescription = toggleLabel }
                )

                collectible?.let { details ->
                    if (details.totalUpgradedCount > 0 && details.maxUpgradedCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.gifts_edition_count,
                                details.totalUpgradedCount,
                                details.maxUpgradedCount
                            ),
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (messageText.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        messageText,
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = palette.divider)
                Spacer(Modifier.height(8.dp))

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    collectible?.let { details ->
                        if (details.modelName.isNotBlank() || details.modelRarityTier != null || details.modelRarityPerMille != null) {
                            GiftAttributeRow(
                                label = stringResource(R.string.gifts_attr_model),
                                value = details.modelName,
                                rarityText = formatRarityText(details.modelRarityTier, details.modelRarityPerMille),
                                rarityColor = giftRarityColor(details.modelRarityTier, palette)
                            )
                        }
                        if (details.symbolName.isNotBlank() || details.symbolRarityTier != null || details.symbolRarityPerMille != null) {
                            GiftAttributeRow(
                                label = stringResource(R.string.gifts_attr_symbol),
                                value = details.symbolName,
                                rarityText = formatRarityText(details.symbolRarityTier, details.symbolRarityPerMille),
                                rarityColor = giftRarityColor(details.symbolRarityTier, palette)
                            )
                        }
                        if (details.backdropName.isNotBlank() || details.backdropRarityTier != null || details.backdropRarityPerMille != null) {
                            GiftAttributeRow(
                                label = stringResource(R.string.gifts_attr_backdrop),
                                value = details.backdropName,
                                rarityText = formatRarityText(details.backdropRarityTier, details.backdropRarityPerMille),
                                rarityColor = giftRarityColor(details.backdropRarityTier, palette)
                            )
                        }
                    }
                    senderName?.let {
                        GiftAttributeRow(label = stringResource(R.string.gifts_attr_sender), value = it)
                    }
                    dateSeconds?.let { seconds ->
                        val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(seconds.coerceAtLeast(0).toLong() * 1000L))
                        GiftAttributeRow(label = stringResource(R.string.gifts_attr_date), value = formatted)
                    }
                    starCount?.let { stars ->
                        GiftAttributeRow(
                            label = stringResource(R.string.gifts_attr_price),
                            value = stringResource(R.string.gifts_cost, formatStarAmount(stars))
                        )
                    }
                    if (badges.isNotEmpty()) {
                        val hasDanger = badges.any { it.second }
                        GiftAttributeRow(
                            label = stringResource(R.string.gifts_attr_status),
                            value = badges.joinToString(" · ") { it.first },
                            valueColor = if (hasDanger) palette.danger else palette.textPrimary
                        )
                    }
                }

                if (target is GiftDetailTarget.Received && !target.gift.wasRefunded && (onTogglePinned != null || onToggleSaved != null)) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = palette.divider)
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.fillMaxWidth()) {
                        onTogglePinned?.let { togglePin ->
                            TextButton(
                                onClick = togglePin,
                                enabled = !actionBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text(
                                    stringResource(
                                        if (target.gift.isPinned) R.string.gifts_action_unpin
                                        else R.string.gifts_action_pin
                                    )
                                )
                            }
                        }
                        onToggleSaved?.let { toggleSave ->
                            TextButton(
                                onClick = toggleSave,
                                enabled = !actionBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text(
                                    stringResource(
                                        if (target.gift.isSaved) R.string.gifts_action_hide_profile
                                        else R.string.gifts_action_show_profile
                                    )
                                )
                            }
                        }
                    }
                }
                if (!actionError.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(actionError, color = palette.danger, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmText = stringResource(R.string.common_close),
        onConfirm = onDismiss
    )
}

@Composable
private fun GiftAttributeRow(
    label: String,
    value: String,
    rarityText: String? = null,
    rarityColor: Color? = null,
    valueColor: Color? = null
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (value.isNotBlank()) {
                Text(value, color = valueColor ?: palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (!rarityText.isNullOrBlank()) {
                Text(
                    rarityText,
                    color = rarityColor ?: palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun formatRarityText(tier: GiftRarityTier?, perMille: Int?): String? = when {
    tier != null -> stringResource(giftRarityLabelRes(tier))
    perMille != null -> stringResource(
        R.string.gifts_rarity_percent,
        String.format(Locale.getDefault(), "%.1f", perMille / 10.0)
    )
    else -> null
}
