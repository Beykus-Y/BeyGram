package com.beykus.beygram.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality

/**
 * The resolved design system of the interface in effect: one immutable set of semantic tokens produced from the
 * saved style, brightness and the device capabilities. Screens read it through [LocalDesignSystem]; they name a
 * purpose ("navigation surface", "card shape") and never a number, so a style can change the whole app in one place.
 */
@Immutable
data class DesignSystem(
    val style: DesignStyle,
    val shapes: DesignShapes,
    val spacing: DesignSpacing,
    val surfaces: DesignSurfaces,
    val motion: DesignMotionProfile,
    val components: DesignComponents,
    /** Bubble geometry of the style, or null when the style keeps today's bubble metrics untouched (Default). */
    val bubbles: DesignBubbleGeometry?
)

/**
 * Shape scale of a style. The five first values map onto Material's own scale; the named ones are used by the
 * BeyGram components that are not Material widgets (navigation, search, cards, bubbles).
 */
@Immutable
data class DesignShapes(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp,
    val button: Dp,
    val card: Dp,
    val sheet: Dp,
    val dialog: Dp,
    val chip: Dp,
    val search: Dp,
    val composer: Dp,
    val navigationIndicator: Dp
)

/** Spacing and sizing scale. Values are semantic steps, not one-off numbers. */
@Immutable
data class DesignSpacing(
    val xxs: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val screenPadding: Dp,
    val sectionGap: Dp,
    val listRowMinHeight: Dp,
    val navigationHeight: Dp
)

/** How a style builds surfaces: flat paper, tonal containers, raised cards, translucent glass or layered depth. */
enum class SurfaceTreatment { Flat, Tonal, Elevated, Glass, Layered }

/** Visual structure of the main navigation. [PillTabs] is the original BeyGram bottom bar and must stay pixel-identical. */
enum class NavigationVariant { PillTabs, ClassicTabs, NavigationBar, FloatingGlass, SignatureDock }

/** Visual structure of the search field. */
enum class SearchFieldVariant { Filled, Outlined, Glass }

/** Visual structure of the screen header above lists and chats. */
enum class TopBarVariant { Flat, Elevated, Glass, Signature }

/** Visual structure of the message composer. */
enum class ComposerVariant { Rounded, Outlined, Glass, Signature }

/** Component-level choices: the pieces whose structure, not only geometry, differs between styles. */
@Immutable
data class DesignComponents(
    val navigation: NavigationVariant,
    val search: SearchFieldVariant,
    val topBar: TopBarVariant,
    val composer: ComposerVariant,
    val showListDividers: Boolean,
    /** The navigation floats above the content with the background visible behind it. */
    val floatingNavigation: Boolean
)

/** Material 2 draws the send control as a rounded square; the other styles keep the circular control. */
val DesignSystem.squareSendButton: Boolean get() = style == DesignStyle.Material2

/** Elevation, translucency and the optional glass treatment of a style. */
@Immutable
data class DesignSurfaces(
    val treatment: SurfaceTreatment,
    val cardElevation: Dp,
    val barElevation: Dp,
    val sheetElevation: Dp,
    val buttonElevation: Dp,
    /** 0 = no tint over the background, 1 = fully tinted. Used by tonal surfaces and glass. */
    val tintAlpha: Float,
    /** Opacity of the surface fill itself; 1 is opaque, lower values let the backdrop through (glass). */
    val surfaceAlpha: Float,
    /**
     * How far the Material surface-container ramp departs from the plain surface: 0 keeps every container flat like
     * Material 2, 1 is the tonal ramp Material 3 and BeyGram already use. Fractions give quieter layered depth.
     */
    val containerTonalStep: Float,
    /** True when this style draws backdrop-sampled glass; [glass] then carries its parameters. */
    val glass: GlassStyle?
)

/**
 * Parameters of the glass material once the quality level has been resolved.
 * [blurRadiusDp] is a real blur of the sampled backdrop, not a blur of the element's own content.
 */
@Immutable
data class GlassStyle(
    val quality: GlassQuality,
    val blurRadiusDp: Dp,
    /** Tint laid over the sampled backdrop; keeps text readable over any photo. */
    val tintAlpha: Float,
    /** Opacity of the one-pixel light rim that marks the edge of the glass. */
    val rimAlpha: Float,
    /** Opacity of the specular highlight across the top of the glass. */
    val highlightAlpha: Float,
    val shadowElevation: Dp
) {
    /** The backdrop is sampled and blurred only at the full level; lower levels keep the layout and the tint. */
    val samplesBackdrop: Boolean get() = quality == GlassQuality.High && blurRadiusDp > 0.dp
}

/** The style's multiplier for motion, applied on top of the user's own motion settings and never against them. */
@Immutable
data class DesignMotionProfile(
    /** Greater than 1 is slower: the duration is multiplied by this value. */
    val speedScale: Float,
    /** Scales travel distance and overshoot only; opacity always completes. */
    val intensityScale: Float
)

/** The bubble geometry a style asks for. The user's bubble shape, density and radius still apply on top of it. */
@Immutable
data class DesignBubbleGeometry(
    val cornerRadius: Dp,
    val tailRadius: Dp,
    val replyRadius: Dp,
    val textInset: Dp,
    val textTopInset: Dp
)

/**
 * The design in effect. Defaults to the original BeyGram design, which is also what a preview of the Default style
 * and an old draft see, so nothing can be drawn with tokens that no style asked for.
 */
val LocalDesignSystem = staticCompositionLocalOf { DefaultDesignSystem }

/** True when the resolved style draws backdrop glass and the device may actually blur. */
val DesignSystem.usesGlass: Boolean get() = surfaces.glass?.samplesBackdrop == true

/** The resolved design for the saved choices and the device capabilities. */
fun designFor(style: DesignStyle, glassQuality: GlassQuality = GlassQuality.Auto): DesignSystem = when (style) {
    DesignStyle.Default -> DefaultDesignSystem
    DesignStyle.Material2 -> Material2DesignSystem
    DesignStyle.Material3 -> Material3DesignSystem
    DesignStyle.LiquidGlass -> liquidGlassDesign(resolveGlassQuality(glassQuality))
    DesignStyle.Signature -> SignatureDesignSystem
}
