package com.beykus.beygram.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R

@OptIn(ExperimentalTextApi::class)
private fun sourceSans(weight: FontWeight) = Font(
    R.font.source_sans_3,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val SourceSans3 = FontFamily(
    sourceSans(FontWeight.Normal),
    sourceSans(FontWeight.Medium),
    sourceSans(FontWeight.SemiBold),
    sourceSans(FontWeight.Bold)
)

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = SourceSans3, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

// Display 36/700, Title 24/700, Headline 17/600, Body 16/400, Secondary 14/400, Caption 12/500, Label 11/700
val Typography = Typography(
    displaySmall = style(36, FontWeight.Bold, 40, -0.36),
    headlineMedium = style(24, FontWeight.Bold, 30),
    titleMedium = style(17, FontWeight.SemiBold, 24),
    bodyLarge = style(16, FontWeight.Normal, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Medium, 16),
    labelLarge = style(15, FontWeight.SemiBold, 20),
    labelMedium = style(14, FontWeight.SemiBold, 18),
    labelSmall = style(11, FontWeight.Bold, 16, 0.88)
)
