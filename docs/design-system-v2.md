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

The axes are independent. `Material 2 + Graphite + AMOLED` is a valid combination, and switching the style keeps every
other choice: custom accent, bubble colour, message text/time colour, wallpaper, message size, line spacing, bubble
shape, density, motion preset and per-chat overrides.

A missing, damaged or unknown stored value falls back to `Default` / `Auto`. A new installation is `Default`.

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

`designFor(style, quality)` and `resolveTokens(config, systemDark)` are pure; the Android capability reads live in
`glassCapabilities(context)`. Screens read tokens, they never branch on the style for colours.

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
| Components | `DesignComponents` | navigation variant, search variant, top bar, composer, dividers, floating navigation |
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
* `GlassPanel` draws that layer offset to the panel position, applies a real `RenderEffect` blur (API 31+), then a
  tint, a one-pixel light rim and a specular highlight.
* Blurring the panel's own children (`Modifier.blur`) is deliberately not used: it smears the panel instead of the
  background.

Quality levels: `High` (sampled blur, 28dp), `Balanced` (tint and rim, no blur pass), `Lite` (dense matte). `Auto`
picks `Balanced` without API 31 blur support, `Lite` under reduced transparency, `Balanced` in power saving or on low-RAM
devices. Message bubbles and body content stay opaque: readability wins over decoration, and translucent surfaces never
expose content hidden by `FLAG_SECURE` because nothing is drawn outside the app window.

### BeyGram Signature
BeyGram's own language, not a mixture: chat-first hierarchy, Source Sans 3 with an editorial scale (heavy display and
wide-tracked labels), 6–28dp geometry, quiet layered depth (1–8dp) instead of hard shadows, a floating dock and a
composer that belongs to the conversation. It is never applied automatically.

## Component adapters

| Component | Default | Material 2 | Material 3 | Liquid Glass | Signature |
|---|---|---|---|---|---|
| Navigation | pill tabs 56×32 | classic tabs + divider | navigation bar 64×32 | floating glass capsule | floating dock |
| Search | filled 24dp | outlined 2dp | filled, container-high | translucent | filled 18dp |
| Top bar | flat | elevated 4dp | flat | translucent | layered |
| Composer | 20dp | 4dp, square send | 20dp | 24dp translucent | 16dp |
| Bubbles | existing metrics | 6/2dp | 18/4dp | 20/8dp | 18/6dp |

The user's bubble shape (`Soft`, `Square`) subtracts radius from the style base, and an explicitly chosen radius wins.

## Editor

`Appearance → Interface style` lists the five styles with live miniatures drawn by the real `BeyGramTheme` of that
style on the current palette and brightness. The Liquid Glass entry adds the glass quality selector. The page uses the
shared draft: preview, Apply, Undo and Cancel behave exactly like the rest of the editor, and switching the style never
writes colours, wallpapers or motion.

## Draft persistence

`AppearanceDraftSaver` writes 16 slots: the 13 original fields, the design style, the glass quality and the version.
Drafts of 7–14 slots (v1/v2) still restore, with `Default` / `Auto` for the new fields. Damaged values fall back the
same way DataStore does.

## Testing

* `DesignSystemTest` — deterministic resolution, distinct profiles, Default parity, glass quality policy.
* `DesignStyleTest` — stored-value parsing and fallbacks.
* `AppearanceDraftSaverTest` — v3 round trip and legacy drafts.
* `MotionDesignProfileTest` — style motion never overrides user/system restrictions.
* `AppearancePreviewHarnessActivity` (debug) — `--es style <Style> --es quality <Quality> --es page <Page>` and
  `--es screen chrome` for the navigation/composer chrome over a list.
