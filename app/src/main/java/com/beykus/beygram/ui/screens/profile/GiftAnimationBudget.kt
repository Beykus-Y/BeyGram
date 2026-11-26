package com.beykus.beygram.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal const val DEFAULT_GIFT_ANIMATION_BUDGET = 4

/**
 * Limits how many gift stickers may animate concurrently on a single surface (gifts list, profile showcase, chat).
 * Slots are acquired when a sticker is visible and autoplay-eligible, and released immediately when scrolled off-screen
 * or paused.
 */
@Stable
internal class GiftAnimationBudget(val maxActive: Int = DEFAULT_GIFT_ANIMATION_BUDGET) {
    private var activeKeys by mutableStateOf<Set<Any>>(emptySet())

    val activeCount: Int get() = activeKeys.size

    fun isActive(key: Any): Boolean = key in activeKeys

    fun tryAcquire(key: Any): Boolean {
        val current = activeKeys
        if (key in current) return true
        if (current.size >= maxActive) return false
        activeKeys = current + key
        return true
    }

    fun release(key: Any) {
        val current = activeKeys
        if (key in current) {
            activeKeys = current - key
        }
    }
}

internal val LocalGiftAnimationBudget = compositionLocalOf<GiftAnimationBudget?> { null }

@Composable
internal fun ProvideGiftAnimationBudget(
    maxActive: Int = DEFAULT_GIFT_ANIMATION_BUDGET,
    content: @Composable () -> Unit
) {
    val budget = remember(maxActive) { GiftAnimationBudget(maxActive) }
    CompositionLocalProvider(LocalGiftAnimationBudget provides budget, content = content)
}
