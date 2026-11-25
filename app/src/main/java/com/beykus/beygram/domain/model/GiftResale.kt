package com.beykus.beygram.domain.model

data class ResaleGiftItem(
    val id: Long,
    val name: String,
    val title: String,
    val number: Int,
    val emoji: String,
    /** Null for a TON-only listing or an unavailable Stars price. */
    val starCount: Long?,
    val isOwned: Boolean,
    val visual: GiftVisual? = null
)

data class ResaleGiftsPage(val items: List<ResaleGiftItem>, val nextOffset: String)

fun resaleCanBuy(item: ResaleGiftItem, balance: StarBalance?): Boolean =
    item.id > 0L && !item.isOwned && item.name.isNotBlank() && item.starCount?.let { it > 0L && balance != null && balance.starCount >= it } == true

fun resalePriceMatches(confirmed: Long, current: Long?, tonOnly: Boolean): Boolean =
    confirmed > 0L && confirmed == current && !tonOnly

/** Preserve the first visible position, but replace its price/ownership with the newest server row. */
fun mergeResaleGifts(current: List<ResaleGiftItem>, next: List<ResaleGiftItem>): List<ResaleGiftItem> =
    (current + next).associateBy { it.name }.values.toList()

/** Stop any repeated cursor, including cycles across several pages. Failed requests do not consume a cursor. */
fun nextResaleOffset(loadedOffsets: Set<String>, requested: String, serverNext: String): String =
    serverNext.takeIf { it.isNotBlank() && it != requested && it !in loadedOffsets }.orEmpty()
