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
import com.beykus.beygram.data.appearance.DesignStyle

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

/** Roboto is what Android has always shipped as the default family, so M2 and M3 ask for it by name. */
private val Roboto = FontFamily.Default

private fun robotoStyle(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Roboto,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp
)

/**
 * Material Design 2: the 2014 scale with Roboto. Body 16/14, title 20/500, a capitalised 14sp button label and a
 * smaller caption, so the whole app reads denser and more utilitarian than the BeyGram default.
 */
val Material2Typography = Typography(
    displaySmall = robotoStyle(34, FontWeight.Normal, 40, 0.25),
    headlineMedium = robotoStyle(24, FontWeight.Normal, 32),
    titleMedium = robotoStyle(20, FontWeight.Medium, 26),
    bodyLarge = robotoStyle(16, FontWeight.Normal, 24),
    bodyMedium = robotoStyle(14, FontWeight.Normal, 20),
    bodySmall = robotoStyle(12, FontWeight.Normal, 16),
    labelLarge = robotoStyle(14, FontWeight.Medium, 20, 1.25),
    labelMedium = robotoStyle(14, FontWeight.Medium, 20, 0.5),
    labelSmall = robotoStyle(11, FontWeight.Medium, 16, 1.5)
)

/**
 * Material 3: Roboto with the current scale. Titles are 16sp/500 rather than 17sp/semibold, labels are quieter and
 * body text gets the M3 tracking, which is what makes M3 read differently from M2 despite the same family.
 */
val Material3Typography = Typography(
    displaySmall = robotoStyle(36, FontWeight.Normal, 44, -0.25),
    headlineMedium = robotoStyle(28, FontWeight.Normal, 36),
    titleMedium = robotoStyle(16, FontWeight.Medium, 24, 0.15),
    bodyLarge = robotoStyle(16, FontWeight.Normal, 24, 0.5),
    bodyMedium = robotoStyle(14, FontWeight.Normal, 20, 0.25),
    bodySmall = robotoStyle(12, FontWeight.Normal, 16, 0.4),
    labelLarge = robotoStyle(14, FontWeight.Medium, 20, 0.1),
    labelMedium = robotoStyle(12, FontWeight.Medium, 16, 0.5),
    labelSmall = robotoStyle(11, FontWeight.Medium, 16, 0.5)
)

/**
 * Liquid Glass: the tighter, heavier display of iOS with the platform's own family, so it stays legible on Android.
 * Headlines are tighter and body text keeps a shorter line height, which is what makes the glass panels feel like
 * floating labels rather than page furniture.
 */
val LiquidGlassTypography = Typography(
    displaySmall = robotoStyle(34, FontWeight.Bold, 40, -0.5),
    headlineMedium = robotoStyle(24, FontWeight.SemiBold, 30, -0.2),
    titleMedium = robotoStyle(17, FontWeight.SemiBold, 22, -0.1),
    bodyLarge = robotoStyle(16, FontWeight.Normal, 21, -0.05),
    bodyMedium = robotoStyle(15, FontWeight.Normal, 20),
    bodySmall = robotoStyle(13, FontWeight.Normal, 18, 0.1),
    labelLarge = robotoStyle(15, FontWeight.SemiBold, 20, -0.1),
    labelMedium = robotoStyle(13, FontWeight.SemiBold, 18),
    labelSmall = robotoStyle(11, FontWeight.SemiBold, 15, 0.3)
)

/**
 * BeyGram Signature: the humanist Source Sans 3 with an editorial hierarchy - a heavy display, a confident title and
 * wide-tracked small labels. The family stays BeyGram's own instead of borrowing another platform's.
 */
val SignatureTypography = Typography(
    displaySmall = style(38, FontWeight.Bold, 44, -0.5),
    headlineMedium = style(25, FontWeight.Bold, 31, -0.2),
    titleMedium = style(17, FontWeight.Bold, 24),
    bodyLarge = style(16, FontWeight.Normal, 23, 0.05),
    bodyMedium = style(14, FontWeight.Normal, 21, 0.1),
    bodySmall = style(12, FontWeight.SemiBold, 17, 0.2),
    labelLarge = style(15, FontWeight.Bold, 20, 0.1),
    labelMedium = style(13, FontWeight.SemiBold, 18, 0.3),
    labelSmall = style(11, FontWeight.Bold, 16, 0.7)
)

/** The Material typography of a design style. Default returns the exact object the app has always used. */
fun typographyFor(style: DesignStyle): Typography = when (style) {
    DesignStyle.Default -> Typography
    DesignStyle.Material2 -> Material2Typography
    DesignStyle.Material3 -> Material3Typography
    DesignStyle.LiquidGlass -> LiquidGlassTypography
    DesignStyle.Signature -> SignatureTypography
}
