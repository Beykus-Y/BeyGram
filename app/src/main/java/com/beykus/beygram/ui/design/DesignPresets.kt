package com.beykus.beygram.ui.design

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality

/**
 * The original BeyGram design. Every value here reproduces what the app draws before the design system existed:
 * Material's default shape scale, the existing bubble metrics (`bubbles = null`), the current bottom bar and the
 * current motion timing. Changing this object is the only thing that can change Default.
 */
val DefaultDesignSystem = DesignSystem(
    style = DesignStyle.Default,
    shapes = DesignShapes(
        extraSmall = 4.dp, small = 8.dp, medium = 12.dp, large = 16.dp, extraLarge = 28.dp,
        button = 16.dp, card = 20.dp, sheet = 28.dp, dialog = 28.dp, chip = 12.dp,
        search = 24.dp, composer = 20.dp, navigationIndicator = 16.dp, groupCard = 20.dp, listGroup = 18.dp
    ),
    spacing = DesignSpacing(
        xxs = 2.dp, xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 20.dp, xxl = 24.dp,
        screenPadding = 16.dp, sectionGap = 18.dp, listRowMinHeight = 64.dp, navigationHeight = 60.dp
    ),
    surfaces = DesignSurfaces(
        treatment = SurfaceTreatment.Flat,
        cardElevation = 0.dp, barElevation = 0.dp, sheetElevation = 0.dp, buttonElevation = 0.dp,
        tintAlpha = 0f, surfaceAlpha = 1f, containerTonalStep = 1f, glass = null
    ),
    motion = DesignMotionProfile(speedScale = 1f, intensityScale = 1f),
    components = DesignComponents(
        navigation = NavigationVariant.PillTabs,
        search = SearchFieldVariant.Filled,
        topBar = TopBarVariant.Flat,
        composer = ComposerVariant.Rounded,
        showListDividers = false,
        floatingNavigation = false
    ),
    fab = DesignFab(cornerRadius = 20.dp, elevation = 6.dp),
    // The exact numbers the chat list has always used: 30sp bold title, 16dp chips, a tonal archive card, 54dp avatars.
    mainScreen = DesignMainScreen(
        titleSize = 30.sp, titleWeight = FontWeight.Bold, titleTracking = (-0.3).sp,
        sectionLabelUppercase = false, sectionLabelTracking = 0.sp,
        chipRadius = 16.dp, chipSelection = ChipSelectionTone.Filled,
        archive = ArchiveTreatment.Tonal, archiveRadius = 14.dp, archiveElevation = 0.dp,
        avatarSize = 54.dp, rowTitleWeight = FontWeight.SemiBold, badgeRadius = 11.dp,
        headerGap = 14.dp
    ),
    bubbles = null
)

/**
 * Classic Material Design. Small corner radii, flat white/grey surfaces that carry a real elevation, 4dp app bars,
 * a denser type scale and solid dividers instead of tonal containers.
 */
val Material2DesignSystem = DesignSystem(
    style = DesignStyle.Material2,
    shapes = DesignShapes(
        extraSmall = 2.dp, small = 4.dp, medium = 4.dp, large = 8.dp, extraLarge = 12.dp,
        button = 4.dp, card = 4.dp, sheet = 0.dp, dialog = 4.dp, chip = 16.dp,
        search = 2.dp, composer = 4.dp, navigationIndicator = 0.dp, groupCard = 4.dp, listGroup = 4.dp
    ),
    spacing = DesignSpacing(
        xxs = 2.dp, xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 20.dp, xxl = 24.dp,
        screenPadding = 16.dp, sectionGap = 16.dp, listRowMinHeight = 64.dp, navigationHeight = 56.dp
    ),
    surfaces = DesignSurfaces(
        treatment = SurfaceTreatment.Elevated,
        cardElevation = 2.dp, barElevation = 4.dp, sheetElevation = 16.dp, buttonElevation = 2.dp,
        tintAlpha = 0f, surfaceAlpha = 1f, containerTonalStep = 0f, glass = null
    ),
    motion = DesignMotionProfile(speedScale = 1f, intensityScale = 0.9f),
    components = DesignComponents(
        navigation = NavigationVariant.ClassicTabs,
        search = SearchFieldVariant.Outlined,
        topBar = TopBarVariant.Elevated,
        composer = ComposerVariant.Outlined,
        showListDividers = true,
        floatingNavigation = false
    ),
    // Classic Material Design draws the action button as a circle above a flat list.
    fab = DesignFab(cornerRadius = 28.dp, elevation = 6.dp),
    // Dense 20sp app-bar title, rectangular chips and a badge that is not a pill, so nothing stays modern-rounded
    // next to the outlined 2dp search field.
    mainScreen = DesignMainScreen(
        titleSize = 22.sp, titleWeight = FontWeight.Medium, titleTracking = 0.sp,
        sectionLabelUppercase = true, sectionLabelTracking = 0.6.sp,
        chipRadius = 2.dp, chipSelection = ChipSelectionTone.Underlined,
        archive = ArchiveTreatment.Card, archiveRadius = 4.dp, archiveElevation = 2.dp,
        avatarSize = 48.dp, rowTitleWeight = FontWeight.Medium, badgeRadius = 2.dp,
        headerGap = 10.dp
    ),
    bubbles = DesignBubbleGeometry(
        cornerRadius = 6.dp, tailRadius = 2.dp, replyRadius = 4.dp, textInset = 12.dp, textTopInset = 8.dp
    )
)

/**
 * Material 3 (Material You). Tonal container surfaces instead of shadows, the current shape and type scales and the
 * navigation bar. Dynamic colour still reaches it through the existing palette resolution.
 */
val Material3DesignSystem = DesignSystem(
    style = DesignStyle.Material3,
    shapes = DesignShapes(
        extraSmall = 4.dp, small = 8.dp, medium = 12.dp, large = 16.dp, extraLarge = 28.dp,
        button = 20.dp, card = 12.dp, sheet = 28.dp, dialog = 28.dp, chip = 8.dp,
        search = 28.dp, composer = 20.dp, navigationIndicator = 16.dp, groupCard = 12.dp, listGroup = 12.dp
    ),
    spacing = DesignSpacing(
        xxs = 2.dp, xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 24.dp, xxl = 32.dp,
        screenPadding = 16.dp, sectionGap = 20.dp, listRowMinHeight = 72.dp, navigationHeight = 80.dp
    ),
    surfaces = DesignSurfaces(
        treatment = SurfaceTreatment.Tonal,
        cardElevation = 0.dp, barElevation = 0.dp, sheetElevation = 0.dp, buttonElevation = 0.dp,
        tintAlpha = 0.14f, surfaceAlpha = 1f, containerTonalStep = 1f, glass = null
    ),
    motion = DesignMotionProfile(speedScale = 0.95f, intensityScale = 1.15f),
    components = DesignComponents(
        navigation = NavigationVariant.NavigationBar,
        search = SearchFieldVariant.Filled,
        topBar = TopBarVariant.Flat,
        composer = ComposerVariant.Rounded,
        showListDividers = false,
        floatingNavigation = false
    ),
    fab = DesignFab(cornerRadius = 16.dp, elevation = 6.dp),
    // Material You: a lighter title, tonal chips without a hard accent fill and an 8dp badge.
    mainScreen = DesignMainScreen(
        titleSize = 26.sp, titleWeight = FontWeight.SemiBold, titleTracking = 0.sp,
        sectionLabelUppercase = false, sectionLabelTracking = 0.4.sp,
        chipRadius = 8.dp, chipSelection = ChipSelectionTone.Tonal,
        archive = ArchiveTreatment.Tonal, archiveRadius = 12.dp, archiveElevation = 0.dp,
        avatarSize = 52.dp, rowTitleWeight = FontWeight.Medium, badgeRadius = 8.dp,
        headerGap = 12.dp
    ),
    bubbles = DesignBubbleGeometry(
        cornerRadius = 18.dp, tailRadius = 4.dp, replyRadius = 12.dp, textInset = 12.dp, textTopInset = 8.dp
    )
)

/**
 * Android rendition of translucent floating panels. The backdrop is sampled and blurred at the [GlassQuality.High]
 * level; lower levels keep the geometry, the tint and the light rim but skip the blur pass, so the interface stays
 * readable and cheap on weak hardware.
 */
fun liquidGlassDesign(quality: GlassQuality): DesignSystem {
    val resolved = if (quality == GlassQuality.Auto) GlassQuality.High else quality
    val glass = when (resolved) {
        GlassQuality.High -> GlassStyle(
            quality = resolved, blurRadiusDp = 28.dp, tintAlpha = 0.78f,
            rimAlpha = 0.55f, highlightAlpha = 0.18f, shadowElevation = 12.dp
        )
        GlassQuality.Balanced -> GlassStyle(
            quality = resolved, blurRadiusDp = 0.dp, tintAlpha = 0.90f,
            rimAlpha = 0.45f, highlightAlpha = 0.10f, shadowElevation = 8.dp
        )
        // Lite is the matte fallback: no translucency at all, but the layout and the rim stay recognisably glass.
        GlassQuality.Lite, GlassQuality.Auto -> GlassStyle(
            quality = GlassQuality.Lite, blurRadiusDp = 0.dp, tintAlpha = 0.96f,
            rimAlpha = 0.30f, highlightAlpha = 0f, shadowElevation = 4.dp
        )
    }
    return DesignSystem(
        style = DesignStyle.LiquidGlass,
        shapes = DesignShapes(
            extraSmall = 10.dp, small = 14.dp, medium = 18.dp, large = 24.dp, extraLarge = 32.dp,
            button = 22.dp, card = 24.dp, sheet = 32.dp, dialog = 28.dp, chip = 18.dp,
            search = 24.dp, composer = 24.dp, navigationIndicator = 26.dp, groupCard = 24.dp, listGroup = 20.dp
        ),
        spacing = DesignSpacing(
            xxs = 2.dp, xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 22.dp, xxl = 28.dp,
            screenPadding = 16.dp, sectionGap = 20.dp, listRowMinHeight = 68.dp, navigationHeight = 64.dp
        ),
        surfaces = DesignSurfaces(
            treatment = SurfaceTreatment.Glass,
            cardElevation = 0.dp, barElevation = 0.dp, sheetElevation = 0.dp, buttonElevation = 0.dp,
            tintAlpha = glass.tintAlpha,
            surfaceAlpha = when (resolved) {
                GlassQuality.High -> 0.86f
                GlassQuality.Balanced -> 0.92f
                else -> 1f
            },
            containerTonalStep = 1f,
            glass = glass
        ),
        motion = DesignMotionProfile(speedScale = 0.9f, intensityScale = 1.2f),
        components = DesignComponents(
            navigation = NavigationVariant.FloatingGlass,
            search = SearchFieldVariant.Glass,
            topBar = TopBarVariant.Glass,
            composer = ComposerVariant.Glass,
            showListDividers = false,
            floatingNavigation = true
        ),
        // A lens, not a second pane of glass: the button carries a light shadow and its own round edge.
        fab = DesignFab(cornerRadius = 26.dp, elevation = 4.dp),
        // Glass reads through the chrome: soft round chips, a larger archive card and a quiet title weight.
        mainScreen = DesignMainScreen(
            titleSize = 28.sp, titleWeight = FontWeight.SemiBold, titleTracking = (-0.2).sp,
            sectionLabelUppercase = false, sectionLabelTracking = 0.3.sp,
            chipRadius = 18.dp, chipSelection = ChipSelectionTone.Tonal,
            archive = ArchiveTreatment.Card, archiveRadius = 20.dp, archiveElevation = 0.dp,
            avatarSize = 54.dp, rowTitleWeight = FontWeight.SemiBold, badgeRadius = 11.dp,
            headerGap = 14.dp
        ),
        bubbles = DesignBubbleGeometry(
            cornerRadius = 20.dp, tailRadius = 8.dp, replyRadius = 14.dp, textInset = 12.dp, textTopInset = 8.dp
        )
    )
}

/**
 * BeyGram Signature: chat-first hierarchy with a floating dock, a composer that belongs to the conversation and
 * quiet layered depth instead of hard shadows. It has its own geometry, type emphasis and motion profile rather
 * than mixing the other four styles.
 */
val SignatureDesignSystem = DesignSystem(
    style = DesignStyle.Signature,
    shapes = DesignShapes(
        extraSmall = 6.dp, small = 10.dp, medium = 14.dp, large = 20.dp, extraLarge = 28.dp,
        button = 16.dp, card = 18.dp, sheet = 26.dp, dialog = 22.dp, chip = 14.dp,
        search = 18.dp, composer = 16.dp, navigationIndicator = 12.dp, groupCard = 18.dp, listGroup = 16.dp
    ),
    spacing = DesignSpacing(
        xxs = 2.dp, xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 22.dp, xxl = 28.dp,
        screenPadding = 14.dp, sectionGap = 20.dp, listRowMinHeight = 68.dp, navigationHeight = 64.dp
    ),
    surfaces = DesignSurfaces(
        treatment = SurfaceTreatment.Layered,
        cardElevation = 1.dp, barElevation = 2.dp, sheetElevation = 8.dp, buttonElevation = 0.dp,
        tintAlpha = 0.08f, surfaceAlpha = 1f, containerTonalStep = 0.45f, glass = null
    ),
    motion = DesignMotionProfile(speedScale = 0.9f, intensityScale = 1.15f),
    components = DesignComponents(
        navigation = NavigationVariant.SignatureDock,
        search = SearchFieldVariant.Filled,
        topBar = TopBarVariant.Signature,
        composer = ComposerVariant.Signature,
        showListDividers = false,
        floatingNavigation = true
    ),
    // Quiet depth: the button sits almost on the surface instead of casting a hard Material shadow.
    fab = DesignFab(cornerRadius = 18.dp, elevation = 2.dp),
    // Editorial hierarchy: the largest title, tight tracking, a plain archive line instead of a card.
    mainScreen = DesignMainScreen(
        titleSize = 32.sp, titleWeight = FontWeight.Bold, titleTracking = (-0.6).sp,
        sectionLabelUppercase = true, sectionLabelTracking = 0.8.sp,
        chipRadius = 14.dp, chipSelection = ChipSelectionTone.Filled,
        archive = ArchiveTreatment.Flat, archiveRadius = 0.dp, archiveElevation = 0.dp,
        avatarSize = 56.dp, rowTitleWeight = FontWeight.Bold, badgeRadius = 10.dp,
        headerGap = 16.dp
    ),
    bubbles = DesignBubbleGeometry(
        cornerRadius = 18.dp, tailRadius = 6.dp, replyRadius = 12.dp, textInset = 12.dp, textTopInset = 8.dp
    )
)

/**
 * The glass level that is actually used. An explicit user choice is kept; [GlassQuality.Auto] takes [autoLevel],
 * which the caller derives from the Android version, window blur support, the system reduced-transparency flag and
 * the power state. Damaged stored values never disable the interface: they resolve to [GlassQuality.Lite].
 */
fun resolveGlassQuality(requested: GlassQuality, autoLevel: GlassQuality = GlassQuality.High): GlassQuality = when (requested) {
    GlassQuality.High, GlassQuality.Balanced, GlassQuality.Lite -> requested
    GlassQuality.Auto -> autoLevel.takeUnless { it == GlassQuality.Auto } ?: GlassQuality.Lite
}
