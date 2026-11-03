package com.beykus.beygram.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement

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
    /** Shape and depth of the create-chat button, so the primary action belongs to the same style as the lists. */
    val fab: DesignFab,
    /** Geometry and hierarchy of the chat list, so a style changes the screen and not only the bar below it. */
    val mainScreen: DesignMainScreen,
    /** Bubble geometry of the style, or null when the style keeps today's bubble metrics untouched (Default). */
    val bubbles: DesignBubbleGeometry?,
    /**
     * Where the main navigation is actually drawn. It defaults to the style's own stance, so a style keeps its look;
     * an explicit choice in Appearance overrides it without changing [components] or anything else of the style.
     */
    val navigationFloating: Boolean = components.floatingNavigation
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
    val navigationIndicator: Dp,
    /** Settings group card. Default keeps its 20dp card, Material 2 turns it into a 4dp surface. */
    val groupCard: Dp,
    /** Slightly smaller settings card used on dense pages. Default keeps 18dp. */
    val listGroup: Dp,
    /**
     * Largest corner any other surface may have in this style: panels, popups, media frames and buttons that are not
     * named above keep their own radius up to this value. Null keeps every radius as it was drawn, which is what
     * Default and the modern styles do; Material 2 caps them at its 4dp component corner.
     */
    val cornerCap: Dp? = null
)

/** The corner a surface drawn with [radius] gets in this style. Without a cap the radius is returned unchanged. */
fun DesignShapes.corner(radius: Dp): Dp = cornerCap?.let { minOf(radius, it) } ?: radius

/**
 * A rounded shape whose radius follows the style in effect. Screens pass the radius they have always used, so Default
 * draws exactly what it drew before and Material 2 turns large rounded panels into its classic 4dp surfaces.
 */
@Composable
fun styledShape(radius: Dp): RoundedCornerShape = RoundedCornerShape(LocalDesignSystem.current.shapes.corner(radius))

/** The same shape with only the top corners rounded, for sheets and panels attached to the bottom edge. */
@Composable
fun styledTopShape(radius: Dp): RoundedCornerShape {
    val corner = LocalDesignSystem.current.shapes.corner(radius)
    return RoundedCornerShape(topStart = corner, topEnd = corner)
}

/** Shape of a settings group card, read from the style in effect. */
@Composable
fun groupCardShape(): RoundedCornerShape = RoundedCornerShape(LocalDesignSystem.current.shapes.groupCard)

/** Shape of the denser settings card used on long pages. */
@Composable
fun listGroupShape(): RoundedCornerShape = RoundedCornerShape(LocalDesignSystem.current.shapes.listGroup)

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

/**
 * Visual structure of the main navigation. [PillTabs] is the original BeyGram bottom bar and must stay pixel-identical.
 * [SignatureMark] has no pill at all: the selected tab is drawn in ink with a short mark above it, and the same bar works
 * pinned on the canvas or floating in a capsule.
 */
enum class NavigationVariant { PillTabs, ClassicTabs, NavigationBar, FloatingGlass, SignatureMark }

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

/**
 * The create-chat button. It is part of the style, not a fixed Material 3 widget: Material 2 is a circle, Material 3 a
 * soft squircle, Glass a lens and Signature a calm rounded square with almost no shadow. Size stays at the Material
 * touch standard for every style, so the target never shrinks.
 */
@Immutable
data class DesignFab(
    val cornerRadius: Dp,
    /** Shadow under the button. Signature keeps almost none; the depth there comes from the layers behind it. */
    val elevation: Dp,
    /**
     * Material 3 fills the button with the primary container and its on-colour instead of the solid accent, so the
     * main action stays tonal like the rest of the screen. The other styles keep the accent fill.
     */
    val tonal: Boolean = false
)

/** How the archive row meets the list: a raised card, a flat tonal row or a plain navigation line. */
enum class ArchiveTreatment { Card, Tonal, Flat }

/** How the selected folder chip is marked. */
enum class ChipSelectionTone {
    /** Filled with the accent, the BeyGram default. */
    Filled,
    /** Muted container with accent label, the Material 3 tonal filter chip. */
    Tonal,
    /** No container change, an accent label over a bottom rule, the classic Material 2 tab. */
    Underlined,
    /**
     * The Material 3 filter chip: a 32dp outlined chip when off, the secondary container with a leading check when on.
     * The touch target stays 48dp around the smaller visual chip.
     */
    Filter,
    /**
     * BeyGram Signature: no container when off, only a quiet label; the selected folder is filled with the ink (the
     * text colour) and its label takes the canvas colour. Accent stays reserved for unread counts and actions.
     */
    Ink
}

/**
 * The chat list hierarchy of a style: header type, folder chips, archive row, list rows and badges. These are the
 * pieces the user sees before opening a chat, so a style that only changed the bottom bar would not be recognised here.
 * Every value has a Default counterpart equal to what BeyGram has always drawn.
 */
@Immutable
data class DesignMainScreen(
    val titleSize: TextUnit,
    val titleWeight: FontWeight,
    val titleTracking: TextUnit,
    /** Section labels such as the pinned header: capitals are a Material 2 habit, quiet tracking suits the others. */
    val sectionLabelUppercase: Boolean,
    val sectionLabelTracking: TextUnit,
    val chipRadius: Dp,
    val chipSelection: ChipSelectionTone,
    val archive: ArchiveTreatment,
    val archiveRadius: Dp,
    val archiveElevation: Dp,
    val avatarSize: Dp,
    val rowTitleWeight: FontWeight,
    /** Corner radius of the unread counter; Material 2 keeps it square, the others are pills. */
    val badgeRadius: Dp,
    /** Space between the header block and the search field. */
    val headerGap: Dp,
    /**
     * The small accent app name above the screen title. Material 3 keeps one title line in its top app bar, so the
     * header does not stack a brand label, a title, the search bar and the folders on top of each other.
     */
    val showBrandOverline: Boolean = true,
    /** Visual height of the search field at the standard font scale. Material 3 uses its 56dp search bar. */
    val searchHeight: Dp = 48.dp,
    /**
     * Side inset of a chat row's pressed and selected tile. 0 keeps the full-width row; Signature draws the row as a
     * rounded tile inside the list, so press feedback never touches the screen edge.
     */
    val rowInset: Dp = 0.dp,
    /** Corner of that tile. Only used together with [rowInset]. */
    val rowRadius: Dp = 0.dp,
    /** Unread rows draw their preview in the text colour, so new messages stand out without extra chrome. */
    val unreadPreviewEmphasis: Boolean = false
)

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
    /** Opacity of the light edge of full-width chrome, sheets and the pinned bar's hairline; panels use [outlineTopAlpha]. */
    val rimAlpha: Float,
    /** Opacity of the specular highlight across the top of the glass. */
    val highlightAlpha: Float,
    val shadowElevation: Dp,
    /**
     * Opacity of the thin outer stroke of a large floating container or a list card, at its top edge. The stroke fades to
     * [outlineBottomAlpha] at the bottom. It is the only border of that layer: the fill and the light carry the rest.
     */
    val outlineTopAlpha: Float = 0.18f,
    /** Opacity of the outer stroke at its bottom edge, see [outlineTopAlpha]. */
    val outlineBottomAlpha: Float = 0.04f
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

/**
 * Where the bar is drawn for the user's [placement] choice and the stance of the style. [NavigationPlacement.Auto]
 * keeps whatever the style has always done, which is what makes an old installation look unchanged after an update.
 */
fun resolveNavigationFloating(placement: NavigationPlacement, naturalFloating: Boolean): Boolean = when (placement) {
    NavigationPlacement.Auto -> naturalFloating
    NavigationPlacement.Pinned -> false
    NavigationPlacement.Floating -> true
}

/** The resolved design for the saved choices and the device capabilities. */
fun designFor(
    style: DesignStyle,
    glassQuality: GlassQuality = GlassQuality.Auto,
    navigationPlacement: NavigationPlacement = NavigationPlacement.Auto
): DesignSystem {
    val base = when (style) {
        DesignStyle.Default -> DefaultDesignSystem
        DesignStyle.Material2 -> Material2DesignSystem
        DesignStyle.Material3 -> Material3DesignSystem
        DesignStyle.LiquidGlass -> liquidGlassDesign(resolveGlassQuality(glassQuality))
        DesignStyle.Signature -> SignatureDesignSystem
    }
    val floating = resolveNavigationFloating(navigationPlacement, base.components.floatingNavigation)
    // Keep the shared, immutable preset when the choice agrees with the style instead of allocating a copy per screen.
    return if (floating == base.navigationFloating) base else base.copy(navigationFloating = floating)
}
