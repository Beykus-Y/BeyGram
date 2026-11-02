# BeyGram Signature — "ink and mark"

Specification of the fifth interface style (issue #631, umbrella #620). Signature is BeyGram's own language: a clean,
chat-first interface with a strong content hierarchy and restrained chrome. It is not Liquid Glass without blur and not
a Material 2/3 hybrid. It is never applied automatically.

## Principles

1. **Content is the loudest layer.** Headers, the bottom bar, the composer and the mini-player sit on the same canvas as
   the content (`palette.screen`) and are separated from it by one hairline (`palette.divider`). No tinted bars, no
   hard shadows under chrome.
2. **Ink selects, accent acts.** Every selection (bottom tab, folder, filter, segmented choice) is drawn in *ink*, the
   text colour. The accent is kept for what needs attention or is an action: unread counters, the create and send
   buttons, links, the user's own bubble. A custom accent therefore lands exactly where it matters and is never
   replaced by a style colour.
3. **The mark.** The single ornament of the style is a short rounded ink bar (20×3dp). It marks the selected tab and
   glides between tabs with the `TabSelect` motion token. It is not repeated as decoration elsewhere.
4. **Soft rectangles.** Radii 6 / 10 / 14 / 20 / 26dp; cards 16dp, settings groups 18dp, search 14dp, folder chips
   12dp, the composer field 22dp, bubbles 18dp with a 6dp tail. Nothing is fully pill-shaped except avatars, the
   send button and counters.
5. **Layered depth, not elevation.** Three planes: canvas (`screen`) → grouped surface (`surface`, settings cards,
   sheets) → field (`surface2`, search and composer). The tonal ramp is a quiet 0.45 step. Only layers that really
   float cast a shadow: the floating bar (3dp), the create button (2dp) and sheets (8dp).
6. **Editorial type.** Source Sans 3 with the Signature scale (`SignatureTypography`): a 32sp bold screen title with
   tight tracking, bold conversation names, wide-tracked capital section labels.

## Rhythm and density

4dp grid. Screen padding 14dp, section gap 20dp, list rows at least 68dp. The user's list density (Compact /
Comfortable / Spacious) still scales row padding on top of the style.

## States

| State | Treatment |
|---|---|
| Pressed | Ripple clipped to the element shape; chat rows are rounded 16dp tiles inset 8dp from the screen edge |
| Selected | Ink: ink-filled chip with a canvas label, ink icon + bold label + mark in navigation |
| Open chat (two panes) | Accent-soft tile, `selected` semantics for TalkBack |
| Disabled | 38% ink, the shape stays |
| Unread | Accent counter; the preview line switches to the text colour (muted chats keep the quiet preview) |

## Motion

Signature uses the Motion Engine tokens with its profile (speed ×0.9, travel ×1.15). The mark glides with `TabSelect`,
chip colours cross-fade with `TabSelect`. Motion Off, disabled categories and the system reduced-motion setting are
resolved first by `resolveMotion`, so they make every Signature transition instant.

## Navigation

One set of tabs, two placements (#625):

* **Pinned** (the Signature default for `Auto`): full-width bar on the canvas, one hairline on top, no pill. The
  selected tab is drawn in ink with the mark lifted towards the top edge.
* **Floating**: the same tabs in a surface capsule (20dp radius, 14/9dp margins) with a hairline rim and a 3dp shadow.

The rounded dock is not a part of Signature; it is only what the user gets after choosing "Floating".

## Component reference

| Component | Signature |
|---|---|
| Bottom bar | ink + mark, canvas + hairline pinned, hairline capsule floating |
| Chat list header | 32sp bold title, brand overline, quiet icon actions |
| Folders / filters | ink chips: plain label off, ink fill on, accent unread count; 36dp chip in a 48dp target |
| Archive | plain line, no card |
| Chat rows | 56dp avatar, bold name, rounded inset tile for press/selection, emphasised unread preview |
| Create button | accent, 18dp rounded square, 2dp shadow |
| Search | filled `surface2`, 14dp |
| Top bars (chat, nested screens, mini-player) | canvas + bottom hairline |
| Composer | canvas + top hairline, 22dp field on `surface2`, accent round send |
| Bubbles | 18/6dp; reactions and meta stay inside the bubble geometry |
| Settings / profile | 18dp groups on `surface` over the canvas, capital tracked section labels |
| Sheets / dialogs | 26dp / 22dp, `surface`, 8dp sheet shadow |

## Accessibility

Touch targets stay 48dp (chips draw 36dp inside a 48dp target). TalkBack semantics are unchanged by the style: tabs keep
`Role.Tab` and `selected`, rows keep their merged description. Ink on canvas is the text contrast by definition, so
selection stays readable in light, dark and AMOLED; the canvas label on an ink chip is the inverse pair. Font scale 2×
grows chips and rows through `heightIn`, never clips them.

## Rollout

The style is built in vertical slices so each PR can be checked on its own:

1. Specification, tokens, navigation and the chat list (this document's first PR).
2. Chat: top bar, composer, bubbles and reactions, mini-player.
3. Settings, profile, contacts, music, sheets and dialogs.
