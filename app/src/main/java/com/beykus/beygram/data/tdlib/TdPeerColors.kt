package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GroupAuthorEmojiStatus
import com.beykus.beygram.domain.model.PeerAccentColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi

/** Lives with the owning TDLib client. TDLib sends the current palettes when the client starts. */
internal class TdPeerColors {
    private var accents = emptyMap<Int, PeerAccentColor>()
    private var profiles = emptyMap<Int, ProfileColorThemes>()
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    // Called under TdProfiles' lock, which also protects the cached user and its accent ID.
    fun update(update: TdApi.UpdateAccentColors) {
        val next = update.colors.orEmpty().mapNotNull { color ->
            if (color.id in 0..6 || color.id < 0 || color.builtInAccentColorId !in 0..6) return@mapNotNull null
            color.id to PeerAccentColor(
                color.builtInAccentColorId,
                color.lightThemeColors?.validRgb().orEmpty(),
                color.darkThemeColors?.validRgb().orEmpty()
            )
        }.toMap()
        if (next != accents) {
            accents = next
            userAppearanceChanged()
        }
    }

    fun updateProfiles(update: TdApi.UpdateProfileAccentColors) {
        profiles = update.colors.orEmpty().associate { color ->
            color.id to ProfileColorThemes(color.lightThemeColors.toPalette(), color.darkThemeColors.toPalette())
        }
    }

    fun profilePalette(id: Int): ProfileColorThemes? = profiles[id]

    private fun TdApi.ProfileAccentColors?.toPalette(): ProfileColorPalette = ProfileColorPalette(
        this?.paletteColors?.validRgb().orEmpty(),
        this?.backgroundColors?.validRgb().orEmpty(),
        this?.storyColors?.validRgb().orEmpty()
    )

    fun userAppearanceChanged() {
        _revision.update { it + 1 }
    }

    fun accent(id: Int?): PeerAccentColor? = when (id) {
        null -> null
        in 0..6 -> PeerAccentColor(id)
        else -> accents[id]
    }

    private fun IntArray.validRgb(): List<Int> =
        take(3).takeIf { values -> values.all { it in 0..0xFFFFFF } }.orEmpty()
}

internal data class ProfileColorThemes(val light: ProfileColorPalette, val dark: ProfileColorPalette)
internal data class ProfileColorPalette(val palette: List<Int>, val background: List<Int>, val story: List<Int>)

internal fun groupAuthorEmojiStatus(status: TdApi.EmojiStatus?): GroupAuthorEmojiStatus? {
    status ?: return null
    val id = when (val type = status.type) {
        is TdApi.EmojiStatusTypeCustomEmoji -> type.customEmojiId
        is TdApi.EmojiStatusTypeUpgradedGift -> type.modelCustomEmojiId
        else -> return null
    }
    return id.takeIf { it > 0 }?.let { GroupAuthorEmojiStatus(it, status.expirationDate.toLong()) }
}
