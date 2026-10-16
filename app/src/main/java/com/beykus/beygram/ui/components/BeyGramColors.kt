package com.beykus.beygram.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object BeyGramColors {
    val TelegramBlue = Color(0xFF2AABEE)
}

data class BeyGramPalette(
    val telegramBlue: Color,
    val screen: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val incomingBubble: Color,
    val outgoingBubble: Color,
    val disabled: Color,
    val warningSurface: Color,
    val warningText: Color,
    val successSurface: Color,
    val successText: Color
)

@Composable
fun beyGramPalette(): BeyGramPalette {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f

    return BeyGramPalette(
        telegramBlue = BeyGramColors.TelegramBlue,
        screen = colorScheme.background,
        surface = colorScheme.surface,
        textPrimary = colorScheme.onSurface,
        textSecondary = colorScheme.onSurfaceVariant,
        divider = colorScheme.outlineVariant,
        incomingBubble = colorScheme.surface,
        outgoingBubble = if (isDarkTheme) Color(0xFF16384C) else Color(0xFFE5F4FF),
        disabled = colorScheme.surfaceVariant,
        warningSurface = if (isDarkTheme) Color(0xFF3A2B16) else Color(0xFFFFF3D6),
        warningText = if (isDarkTheme) Color(0xFFFFD28A) else Color(0xFF7A4B00),
        successSurface = if (isDarkTheme) Color(0xFF17352D) else Color(0xFFDFF8EF),
        successText = if (isDarkTheme) Color(0xFF8BE0CF) else Color(0xFF0F766E)
    )
}
