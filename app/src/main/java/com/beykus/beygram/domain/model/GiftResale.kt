package com.beykus.beygram.domain.model

data class ResaleGiftItem(
    val id: Long,
    val name: String,
    val title: String,
    val number: Int,
    val emoji: String,
    /** Null for a TON-only listing or an unavailable Stars price. */
    val starCount: Long?,
    val isOwned: Boolean
)

data class ResaleGiftsPage(val items: List<ResaleGiftItem>, val nextOffset: String)

fun resaleCanBuy(item: ResaleGiftItem, balance: StarBalance?): Boolean =
    !item.isOwned && item.name.isNotBlank() && item.starCount?.let { it > 0L && balance != null && balance.starCount >= it } == true

fun resalePriceMatches(confirmed: Long, current: Long?, tonOnly: Boolean): Boolean =
    confirmed > 0L && confirmed == current && !tonOnly

fun mergeResaleGifts(current: List<ResaleGiftItem>, next: List<ResaleGiftItem>): List<ResaleGiftItem> =
    (current + next).distinctBy { it.name }
