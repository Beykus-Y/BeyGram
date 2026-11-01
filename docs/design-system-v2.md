# BeyGram Design System v2

Five interface styles share one screen tree, one Theme Engine and one set of data. A style is a **presentation
profile**: it changes geometry, typography, surfaces, navigation and motion, and it never changes colours the user
picked, wallpapers, message data, gestures, permissions or TDLib behaviour.

## Axes

| Axis | Stored as | Values | Where it applies |
|---|---|---|---|
| Design style | `design_style` | `Default`, `Material2`, `Material3`, `LiquidGlass`, `Signature` | whole interface |
| Colour preset | `theme_preset` | `Warm`, `Graphite`, `MaterialYou` | palette only |
| Brightness | `theme_mode` | `FollowSystem`, `Light`, `Dark`, `Amoled` | palette only |
| Glass quality | `glass_quality` | `Auto`, `High`, `Balanced`, `Lite` | Liquid Glass only |
| Bottom bar placement | `navigation_placement` | `Auto`, `Pinned`, `Floating` | phone bottom navigation |

The axes are independent. `Material 2 + Graphite + AMOLED` is a valid combination, and switching the style keeps every
other choice: custom accent, bubble colour, message text/time colour, wallpaper, message size, line spacing, bubble
shape, density, motion preset and per-chat overrides.

A missing, damaged or unknown stored value falls back to `Default` / `Auto`. A new installation is `Default`.

## Bottom bar placement

The placement is a separate axis from the style: the style decides **how the bar looks** (indicator, labels, weights,
surface, divider) and the placement decides **where that same bar is drawn**. There is one set of tabs, handlers and
selection state; only the layout/surface adapter differs.

* `Auto` (default, and the migration value for every existing install) keeps whatever the style has always done:
  `Default`, `Material 2` and `Material 3` pin the bar, `Liquid Glass` and `Signature` float it.
* `Pinned` puts the bar in the ordinary bottom slot: full window width, no side margin, no outer dock radius, no dock
  shadow, correct system navigation inset. A glass or Signature style keeps its own tab contents and material cues
  (a single hairline instead of the floating rim); it does not fake backdrop blur, because there is no content behind a
  pinned bar.
* `Floating` gives every style the capsule: side margins, bottom margin above the navigation inset, content visible
  behind it, the style's own shadow. `Material 2` keeps its divider and square-ish indicator.

`MainTabsScaffold` reserves the **measured** bar height plus the floating margin instead of a fixed token, so a large
font scale or a 3-button navigation inset cannot put the last row under the dock.

### Dock geometry, create button and mini-player

The measurement is published as `LocalNavigationDock` (`floating`, `height`, `horizontalMargin`, `bottomMargin`,
`contentGap`). It is the one place the rest of the interface learns how much room the navigation takes:

* the scaffold reserves `height + bottomMargin + contentGap` for a floating capsule and keeps the mini-player above
  that gap, so the two never touch;
* the create button lines up with the capsule's side margin when it floats, keeps a small gap above it, and returns to
  the classic bottom-end corner above a pinned bar; its shape and depth come from `DesignSystem.fab` (Material 2 is a
  circle, Material 3 a squircle, Glass a lens, Signature an almost flat rounded square);
* the list keeps `button + gap + 32dp` of bottom padding, computed from the same placement, so the last conversation
  always scrolls clear of the button.

### Chat list hierarchy

`DesignSystem.mainScreen` carries the pieces the user sees before opening a chat: title size/weight/tracking, section
label casing, folder chip radius and selection tone, archive treatment, avatar size, row title weight, unread badge
radius and the header gap. `ChatsScreen` and `SwipeChatRow` read them, so switching the style changes the screen and not
only the bar below it. Default keeps its 30sp title, 16dp accent chips, tonal archive card, 54dp avatars and 11dp badge,
which `DesignSystemTest` pins.

The two floating styles are designed separately instead of sharing one "bath": Glass keeps more air (16/10), a larger
radius (24dp), a softer selection wash (82%) and a real shadow; Signature is tighter (14/9), smaller (18dp), quieter
(72%) and almost shadowless.

On a wide window (`usesMainNavigationRail`, ≥ 600dp) the placement is not applied: the navigation rail stays a rail, so
the sections do not move away from the leading edge and no phone dock appears in a tablet layout.

## Resolution

```
AppearanceConfig (style, preset, mode, overrides)
        + device capabilities (Android version, reduced transparency, power state)
        + user glass quality choice
                    ↓
        DesignSystem  (LocalDesignSystem)
        BeyGramTokens (LocalBeyGramTokens)
                    ↓
BeyGramTheme → MaterialTheme(colorScheme, shapes, typography)
             → beyGramPalette() for BeyGram components
```

`designFor(style, quality, placement)` and `resolveTokens(config, systemDark)` are pure; the Android capability reads
live in `glassCapabilities(context)`. Screens read tokens, they never branch on the style for colours.
`resolveNavigationFloating(placement, styleFloating)` is the only place where the placement axis meets a style.

### Motion precedence

`resolveMotion(token, settings, reduced)` runs first: master switch, system reduced-motion and disabled categories
produce an **instant** spec. Only then the style's `DesignMotionProfile` scales duration and travel. A style can make
motion calmer or more expressive, never longer than a user restriction allows.

## Token layers

| Layer | Type | Notes |
|---|---|---|
| Colour roles | `BeyGramTokens` | unchanged engine from #127; style only changes the tonal container ramp |
| Typography | `Typography` per style | `typographyFor(style)`; Default keeps Source Sans 3 and the current scale |
| Shapes | `DesignShapes` | Material scale `extraSmall…extraLarge` plus `button`, `card`, `sheet`, `dialog`, `chip`, `search`, `composer`, `navigationIndicator` |
| Spacing | `DesignSpacing` | `xxs…xxl`, screen padding, section gap, list row height, navigation height |
| Surfaces | `DesignSurfaces` | treatment, elevations, tint, surface alpha, tonal step, optional `GlassStyle` |
| Motion | `DesignMotionProfile` | `speedScale`, `intensityScale` |
| Components | `DesignComponents` | navigation variant, search variant, top bar, composer, dividers, the style's natural placement |
| Navigation placement | `DesignSystem.navigationFloating` | the resolved placement: the style's own stance unless the user forced `Pinned` / `Floating` |
| Bubbles | `DesignBubbleGeometry` | base geometry; the user's bubble shape, density and explicit radius apply on top |

## Styles

### Default
The interface BeyGram has always had. Material shape scale `4/8/12/16/28`, `Typography` unchanged, the original tonal
container ramp, `bubbles = null` so `bubbleMetricsFor` keeps full ownership, the original 56×32 pill bar and the current
motion timing. Changing `DefaultDesignSystem` is the only way to change Default.

### Material 2
Classic Material Design: 2/4/8dp radii, flat white/grey surfaces that carry real elevation (app bar 4dp, cards 2dp,
sheets 16dp), Roboto with the 2014 scale (title 20/500, capitalised 14sp button label), solid dividers in lists, a
classic tab bar with a divider and no selection pill, outlined search and composer, and a rounded-square send control.

### Material 3
Material You: the current 4/8/12/16/28 scale, tonal containers instead of shadows, Roboto with M3 tracking, the
navigation bar with a 64×32 indicator, filled search and softer motion. Dynamic colour still comes from the existing
Material You preset; on Android < 12 it falls back to Warm.

### Liquid Glass
Android rendition of translucent floating panels — not an Apple API. Floating navigation, translucent top bar, search
and composer, with the **backdrop** sampled:

* `Modifier.glassBackdropSource(backdrop)` records the screen content into an offscreen `GraphicsLayer` every frame.
  It is only wired up when the resolved style actually draws glass, so Default and the other styles never pay for it.
* `GlassPanel` draws that layer offset to the panel position, applies a real `RenderEffect` blur (API 31+), then a
  tint, a one-pixel light rim and a specular highlight.
* Blurring the panel's own children (`Modifier.blur`) is deliberately not used: it smears the panel instead of the
  background.

Quality levels: `High` (sampled blur, 28dp), `Balanced` (tint and rim, no blur pass), `Lite` (dense matte). `Auto`
picks `Balanced` without API 31 blur support, `Lite` under reduced transparency, `Balanced` in power saving or on low-RAM
devices. Message bubbles and body content stay opaque: readability wins over decoration, and translucent surfaces never
expose content hidden by `FLAG_SECURE` because nothing is drawn outside the app window.

**A backdrop source must never contain a glass panel.** Recording such a subtree would make the panel ask the layer that
is currently being written to be drawn into itself, which is a native `SIGSEGV` in the render thread, not a recoverable
exception. The source therefore sits on the content area only (in `MainTabsScaffold` the list/chat, never the bar), and
`GlassBackdrop.recording` is a second guard: while a frame is being recorded, a panel inside it skips sampling and draws
its tint only.

### Crash-loop protection

`DesignStartupGuard` writes a marker on every process start and clears it once two frames have been drawn and the
interface has settled. If the previous start never got that far, the saved non-Default style is replaced with `Default`
and persisted before the theme is applied. Only `design_style` changes: palette, wallpaper, message geometry, motion,
accounts and TDLib data are untouched, so a bad style can never lock the user out of the app.

### BeyGram Signature
BeyGram's own language, not a mixture: chat-first hierarchy, Source Sans 3 with an editorial scale (heavy display and
wide-tracked labels), 6–28dp geometry, quiet layered depth (1–8dp) instead of hard shadows, a floating dock and a
composer that belongs to the conversation. It is never applied automatically.

## Component adapters

| Component | Default | Material 2 | Material 3 | Liquid Glass | Signature |
|---|---|---|---|---|---|
| Navigation | pill tabs 56×32 | classic tabs + divider | navigation bar 64×32 | floating glass capsule | floating dock |
| Dock air (side/bottom, floating) | 12/8 | 12/8 | 12/8 | 16/10 | 14/9 |
| Dock radius / selection wash | 16dp · 100% | 2dp · 100% | 20dp · 100% | 24dp · 82% | 18dp · 72% |
| Create button | 20dp, 6dp | circle, 6dp | 16dp, 6dp | 26dp, 4dp | 18dp, 2dp |
| Search | filled 24dp | outlined 2dp | filled, container-high | translucent | filled 18dp |
| Folder chips | 16dp, accent fill | 2dp, accent rule | 8dp, tonal | 18dp, tonal | 14dp, accent fill |
| Archive row | tonal card 14dp | elevated card 4dp | tonal 12dp | card 20dp | plain line |
| List row | 54dp avatar, semibold | 48dp, medium | 52dp, medium | 54dp, semibold | 56dp, bold |
| Unread badge | 11dp pill | 2dp square | 8dp pill | 11dp pill | 10dp pill |
| Top bar | flat | elevated 4dp | flat | translucent | layered |
| Composer | 20dp | 4dp, square send | 20dp | 24dp translucent | 16dp |
| Bubbles | existing metrics | 6/2dp | 18/4dp | 20/8dp | 18/6dp |

The user's bubble shape (`Soft`, `Square`) subtracts radius from the style base, and an explicitly chosen radius wins.

## Editor

`Appearance → Interface style` lists the five styles with live miniatures drawn by the real `BeyGramTheme` of that
style on the current palette and brightness. Below them, **Bottom bar position** offers `Follow the style`, `Pinned to
the bottom` and `Floating` with a small sketch of each, and the preview above updates before Apply. The Liquid Glass
entry adds the glass quality selector. The page uses the shared draft: preview, Apply, Undo and Cancel behave exactly
like the rest of the editor, and switching the style never writes colours, wallpapers or motion.

## Draft persistence

`AppearanceDraftSaver` writes 17 slots: the 13 original fields, the design style, the glass quality, the bottom bar
placement and the version. Drafts of 7–14 slots (v1/v2) still restore with `Default` / `Auto`; a 16-slot v3 draft
restores with `Auto` for the placement. Damaged values fall back the same way DataStore does.

## Testing

* `DesignSystemTest` — deterministic resolution, distinct profiles, Default parity, glass quality policy.
* `DesignStyleTest` — stored-value parsing and fallbacks.
* `NavigationSpecTest` — the resolved placement for every style and placement, Default numbers, contents never change.
* `NavigationDockTest` — create-button placement, list clearance and the reserved dock space.
* `AppearanceMigrationTest` — stored-value parsing for the placement.
* `AppearanceDraftSaverTest` — v4 round trip and legacy drafts.
* `MotionDesignProfileTest` — style motion never overrides user/system restrictions.
* `AppearancePreviewHarnessActivity` (debug) — `--es style <Style> --es quality <Quality> --es placement <Placement>
  --es page <Page>` and `--es screen chrome` for the navigation/composer chrome over a list.
