# Theme Engine

Theme Engine v1 is part of #127. It covers the colours, bubble shape and text settings in the appearance editor, and the chat wallpaper. This page lists the tokens, the rules that keep text readable, where each value is stored, and how Undo works.

## Tokens

`BeyGramTokens` (`ui/theme/Color.kt`) holds the colour roles of one theme. The presets (Warm, Graphite and the system colours) build the same set, and `withOverrides` (`ui/theme/ThemeOverrides.kt`) applies the colours the user picked on top of it.

| Token | Role |
| --- | --- |
| `bg` | Screen background |
| `surface`, `surface2` | Cards. `surface` is also the incoming bubble |
| `line` | Dividers. The floating date edge uses it when no wallpaper is set |
| `text`, `muted` | Primary and secondary text |
| `accent`, `accentSoft`, `accentInk`, `onAccent` | The accent and the roles derived from it |
| `bubbleOut` | Outgoing bubble |
| `bubbleText` | Message text in bubbles. Defaults to `text`. A picked colour replaces it only while it reads on both bubbles |
| `bubbleTime` | Message time and the sending clock in bubbles. Defaults to `muted`. Same rule as `bubbleText` |
| `ok`, `okInk`, `warn`, `warnInk`, `danger` | Status colours |

`BeyGramPalette` (`ui/components/BeyGramColors.kt`) is built from the tokens for the screens. The bubble roles map as follows: `incomingBubble` is `surface`, `outgoingBubble` is `bubbleOut`, `messageText` is `bubbleText` and `messageTime` is `bubbleTime`.

Read ticks keep the accent colour, because they show whether a message was read. The time colour does not change them.

## Readability rules

- Body text needs a contrast of at least 4.5:1 (`MinTextContrast`). The accent needs 3:1 against the background (`MinAccentContrast`).
- A picked outgoing bubble is kept only if the text it would show reads on it. That text is the picked message text when it reads on both bubbles, and the theme text otherwise.
- A picked message text or time colour is kept only if it reads on both the incoming bubble (`surface`) and the outgoing bubble (`bubbleOut`).
- A stored pick that no longer reads (for example after the system switches theme) falls back to the theme value without an error. The editor runs the same check before the user applies.
- In the editor, a bubble or message colour problem blocks Apply. A close accent is only a warning.
- On a wallpaper, the floating date keeps its opaque pill, which gives about 6:1 text contrast. Its edge uses `muted` at 60% opacity, because the thin divider disappears on bright photos.

## Stored settings

The values are stored in the DataStore file `appearance` (`data/appearance/AppearanceSettingsStore.kt`).

| Key | Type | Value |
| --- | --- | --- |
| `theme_mode`, `theme_preset`, `bubble_style`, `list_density` | string | The stored name of the enum entry. Unknown names read as the default |
| `accent_color` | int | ARGB. Absent means the preset accent |
| `outgoing_bubble_color` | int | ARGB. Absent means the preset bubble colour |
| `message_text_color` | int | ARGB. Absent means the theme text colour |
| `message_time_color` | int | ARGB. Absent means the theme muted colour |
| `message_text_size_sp` | int | 12 to 22 sp, default 16. Applied before the system font scale |
| `bubble_corner_radius_dp` | int | 4 to 24 dp. Absent means the radius of the bubble shape |
| `message_line_spacing` | string | `Compact` (1.2), `Normal` (1.375, the old line height) or `Relaxed` (1.6) |
| `chat_wallpaper` | string | Encoded by `ChatWallpaper.encode()`. Photos keep their dim percent and file name, and a blurred photo uses the `blurphoto:` prefix |

## Editor drafts

The editor works on a draft. Each choice in `ThemeEditorScreen.kt` is a `rememberSaveable` value, so the draft survives rotation. The config the editor was opened with, and a draft that has not been applied yet, are kept at app level in `BeyGramApp.kt` with `AppearanceDraftSaver`, a positional list of strings. Drafts from earlier versions (7 or more parts) still restore, and missing parts take their defaults. Changes reach the app only after Apply. Leaving the screen with unsaved changes asks first.

## Undo

Undo steps back through the earlier states of the draft, up to 50 steps (`DraftHistory`, `ui/screens/theme/DraftHistory.kt`).

- A step is taken before each change: a colour, shape, line spacing or density choice, the "as the shape" radius choice, and Reset all.
- A choice that leaves the draft as it was takes no step.
- A slider drag is one step, taken at its first real move. Later moves of the same drag take no step.
- Undo itself takes no step, so there is no redo.

The history lives in memory with the editor screen. `MainActivity` does not handle configuration changes, so rotation recreates the screen and clears the history. The draft itself is kept (see above): after a rotation the edits stay, and Undo has nothing to return to until the next change.

## Wallpapers

- Photo wallpapers carry a dim percent from 0 to 70.
- Blur needs Android 12 or newer (`Modifier.blur`). Older systems darken the photo by 15 points more, so messages stay readable.

## Tests

- `ThemeOverridesTest` covers the picked colours, the readability invariant over a grid of picks, and the editor warnings.
- `DraftHistoryTest` covers the undo order and the limit.
- `BubbleMetricsTest` and `BubbleAppearanceTest` cover the corner radius and the line spacing.
- `ChatWallpaperBlurTest` covers the `blurphoto:` round trip, old values and damaged values.

## Not measured yet

Frame time and recomposition counts have not been measured. That needs a device, with the same chat set before and after the change. The cloud environment has no Android SDK and no device.

## Device checks

- The date edge on bright wallpapers, in light and dark themes.
- Blur on Android 12 and newer, and the darkening fallback on older systems.
- The text and time colour rows in both themes, including the warning and the blocked Apply.
- The corner radius slider, the "as the shape" choice, and the line spacing in long messages.
- Undo after a colour change, after a slider drag (one step), after Reset all, and after the "as the shape" choice. After a rotation Undo is empty, by design.
