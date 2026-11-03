# Liquid Glass: materiality spec (#630)

Liquid Glass is one material used across the chrome of the app, not an effect glued to the bottom dock. This page says
which surfaces are glass, how the material is built, how it degrades and how it must be measured. The architecture and
the crash guard live in [design-system-v2.md](design-system-v2.md#liquid-glass).

## What is glass and what stays opaque

| Surface | Material | Why |
| --- | --- | --- |
| Floating navigation dock | glass, samples the screen content | overlay above scrolling content |
| Pinned (full-width) navigation | glass tint, specular wash, top hairline; no sample | nothing but the window is behind it, so a blur would be fake |
| Chat list top chrome (title, search, folders) | glass, samples its own chat list | the list scrolls under it |
| Search field inside glass chrome | inner glass (lighter fill on the pane) | one layer of glass, not a second pane |
| Chat header and composer | tinted glass, no sample | the backdrop is private message content; it is never blurred into chrome |
| Message bubbles, message text, photos, video | opaque | readability wins over decoration |
| Chat rows, long lists | opaque | dense text, scrolled constantly; rows are painted with the screen colour for swipe actions, so on the chat list the glow shows only around and below the rows |
| Settings group cards, attachment tiles | subtle glass tint (0.55 fill) and one thin outer stroke over the ambient glow, no highlight | the glow shows through the card; a gloss inside a list card reads as plastic |
| Attachment menu | dense glass sheet (0.94 fill, lit top edge, highlight) | the messages behind the menu must not be readable through it |
| Other sheets and dialogs | opaque Material surfaces | modal content needs full contrast |
| Screen background (chat list, settings) | screen colour with two soft accent glows | translucent glass reads as glass only over colour |

## How the material is built

One pane of glass is, from back to front:

1. **Sample** (High only): the content behind the pane, recorded by `glassBackdropSource` and blurred by a real
   `RenderEffect` (Android 12+). The sample is drawn over an opaque base (`backdropBase` of `GlassPanel`, the screen
   colour on the chat list and the dock), so a list without its own background never shows sharp content through the
   pane. A frame without a finished sample draws a dense mask instead, so the light tint of real glass never shows sharp
   text through the pane.
2. **Tint**: the surface colour at the style's `tintAlpha` over a sample (0.62 at High), at least 0.93 without one.
3. **Outline**: one 1dp stroke around a floating pane or a list card, low contrast: the caller's edge colour at
   `outlineTopAlpha` 0.18 at the top, fading to `outlineBottomAlpha` 0.04 at the bottom. It is the only border of
   that layer. Rows and content inside never add a stroke of their own; fill, light and depth carry them.
4. **Specular highlight**: a white wash across the top 60% (`Modifier.glassHighlight`).
5. **Depth**: a small lift (6dp High, 4dp Balanced, 2dp Lite). Heavy shadow plus a full rim read as a plastic tray.

Full-width chrome (pinned bar, chat list header) uses the same layers with square window edges and a single hairline on
the edge that meets the content.

## Backdrop sampling rules

* A backdrop source records **only content**, never a subtree that contains a `GlassPanel` sampling the same backdrop.
  Self-reference is a native `SIGSEGV` in RenderThread (#623). `GlassBackdrop.recording` is the second guard.
* A screen whose chrome samples its own list provides its **own** `GlassBackdrop` that records the list only. Nesting
  that inside the window-level source is safe: the outer layer records the already drawn chrome, it never samples it.
* Nothing is recorded unless something really blurs it: `DesignSystem.recordsGlassBackdrop` is true only for High on
  Android 12+. Balanced, Lite, other styles and old Android never pay for a record pass.
* Message content is never sampled: chat header and composer stay tinted.

## Quality levels

| Level | Sample + blur | Tint | Rim | Highlight | Shadow |
| --- | --- | --- | --- | --- | --- |
| High | 40dp, Android 12+ | 0.62 | 0.65 | 0.24 | 6dp |
| Balanced | no | 0.93 | 0.55 | 0.18 | 4dp |
| Lite | no | 0.96 | 0.40 | 0.08 | 2dp |

`Auto` picks Balanced without render effects, Lite under reduced transparency (animations off), Balanced in battery
saver or on a low-RAM device, otherwise High. An explicit High on Android below 12 is drawn as Balanced
(`effectiveGlassQuality`) and the settings row says so; the saved choice is kept. Palette, AMOLED, wallpaper, text size,
reduced transparency and motion settings are never overridden by the material.

## Measuring on a device

Compare each level against Default on the same build, same account, same chat list:

1. `adb shell dumpsys gfxinfo com.beykus.beygram reset`, then scroll the chat list for 20 s with a fling every second.
2. `adb shell dumpsys gfxinfo com.beykus.beygram` — record janky frames %, 50/90/99th percentile frame time.
3. Repeat with overscroll at the top and bottom of the list, and with a tab switch every 2 s.
4. Memory: `adb shell dumpsys meminfo com.beykus.beygram` after the run (Graphics and GL lines).
5. Energy: `adb shell dumpsys batterystats --reset`, 5 minutes of scrolling, then `dumpsys batterystats` for the app.

Target devices: Samsung M21 (or a comparable weak GPU), one Android 12+ flagship, API 29/30 for the fallback. Check
High really blurs (text behind the dock is unreadable but coloured), then Balanced/Lite/Auto, dark/light/AMOLED and
3-button/gesture navigation. Cold start, typing, navigation and scrolling must not crash (#623).
