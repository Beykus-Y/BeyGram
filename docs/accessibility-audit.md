# Accessibility audit (issue #390)

Status: implementation pass complete for the representative screens below. Remaining items are device/TalkBack checks,
not code gaps found in this pass. No emulator or connected device was available, so none of those checks were run here.

Date: 2026-10-24

## Scope

| Area | Screen / component | Status |
| --- | --- | --- |
| Chat list | `ui/screens/chats/ChatsScreen.kt`, `SwipeChatRow.kt`, `ChatActionsSheet.kt` | Audited, fixes applied |
| Chat and composer | `ui/screens/chat/ChatScreen.kt`, `ChatSearch.kt`, `FormattingTextField.kt` | Audited, fixes applied |
| Message actions | `ui/screens/chat/MessageActions.kt` | Audited, fixes applied |
| Media in the feed/viewer | `ui/screens/chat/MediaViews.kt`, `MessageAlbum.kt`, `MessageReactions.kt` | Audited, fixes applied |
| Voice / video notes | `ui/screens/chat/VoiceBubble.kt`, `VideoNote.kt`, `RecordingPreviewDialog.kt` | Audited, fixes applied |
| Profile | `ui/screens/profile/ProfileScreen.kt`, `GroupProfile.kt`, `MemberRow.kt`, `SharedMediaSection.kt`, `ChatMembersScreen.kt` | Audited, fixes applied |
| Settings | `ui/screens/settings/SettingsScreen.kt` | Audited, fixes applied |
| Error banners / dialogs | Chat, chat list, profile, search, forward and recording dialogs | Audited, fixes applied |
| Reusable helpers | `ui/accessibility/AccessibilitySupport.kt` | Added |

## What was fixed

### Focus order and one announcement per row

- Chat rows now expose one merged label (title, preview, time, unread count, muted state, send state) instead of
  separate focus stops for every `Text` and status glyph. Avatars are decorative inside that row.
  `SendStatusIcon` no longer duplicates a status description that the row already speaks.
- The chat header avatar and title share one "chat info" focus stop. The avatar itself is decorative there.
- Profile rows merge label and value; profile/group name, toolbar title and section headers are marked as headings.
- Member rows, forwarded-chat rows and archive rows merge their text and have a button role.
- Settings group headers and the screen title keep heading semantics; links remain normal buttons.
- Delete confirmation choices are one `RadioButton` target per row (the nested `RadioButton` no longer creates a
  second, competing focus node).

### Content descriptions and states

- Icon-only controls checked in the audited screens now have labels: profile avatar, media download badge,
  audio play/more buttons, voice/video-note state, send/edit composer button, search clear, archive actions.
- Spoken send/upload state is attached to the control, not repeated by a second hidden label:
  `stateDescription` on the composer send button, video note state, and merged chat-row status.
- Media previews and viewer actions use existing localized media-kind labels (`media_kind_*`) and action labels.
- Voice/video notes describe themselves once (kind, duration, playing/paused/loading) while their visible duration
  remains plain text. The inaccurate image label on decoded images was removed; callers can pass a meaningful one.

### Touch targets

Policy: every interactive control must accept a tap in at least `48 x 48 dp`; visual glyphs may stay smaller inside
that box (`ui/accessibility/AccessibilitySupport.kt`).

Fixed in this pass:

- Chat list: profile avatar, folder chips, filter chips, archive actions, "search all chats" row.
- Chat: composer send button (40 dp visual inside a 48 dp target), "jump to latest", header info target.
- Media: audio play/more buttons, reaction chips and reaction picker cells, voice play/seek area, download badge.
- Profile: group tabs, hidden-tab rows.
- Dialogs: delete choices, transcription rating buttons.
- Search: search field and clear button.

### Errors and dynamic status

- Error/warning banners use polite live regions so TalkBack announces a newly appeared failure without stealing
  focus: chat network banner, chat send/history errors, chat-list folder error, profile/gallery load and photo errors,
  chat/search errors, forward and recording dialogs, shared-media errors.
- The app keeps the user in place; retry/close actions stay reachable next to the announced text.

### Reduce motion

- `MessageAnimationFrame` still uses a Compose `Animatable`; Compose applies Android's animator duration scale
  (`Settings.Global.ANIMATOR_DURATION_SCALE`) to `AnimationSpec`-based animations. In addition,
  `systemMotionEnabled()` now skips the initial transparent/offset frame when the scale is `0`, so a new message is
  visible immediately instead of flashing for one frame.
- `messageAnimationPlan(..., motionEnabled)` is a pure, JVM-tested policy.
- Functional media playback, video-note playback and swipe progress are not disabled. Custom user animation
  preferences remain opt-in through the existing appearance settings.

### Contrast

- No new colour literals were introduced for text or controls. New labels reuse `beyGramPalette()` tokens
  (`textPrimary`, `textSecondary`, `accentInk`, `warningText`, `danger`, `messageTime`, `surface*`).
- Existing theme overrides already block low-contrast custom message text via `MinTextContrast`; this audit did not
  change the palette engine.

## Remaining device-only checks

These need a real device/emulator because they depend on TalkBack, the platform media stack or the device setting,
and JVM tests cannot cover them:

1. **TalkBack traversal** on chat list -> chat -> message actions -> media viewer -> profile -> settings.
   Confirm one focus stop per chat row, the send button reads "Send"/editing reads "Save", and long-press actions
   announce "Message actions".
2. **Swipe-to-reply** and **swipe-left chat actions** remain touch gestures. Confirm TalkBack users can still reply
   through the message long-press menu and archive/read through the chat action sheet.
3. **Voice waveform seeking**: the waveform is a direct-manipulation control without a separate focus stop; TalkBack
   users play/pause through the 48 dp button. If seek-by-screen-reader is required, add explicit skip actions in a
   follow-up.
4. **Video note gestures**: recording hold/lock/slide-to-cancel are gesture based. Verify the existing accessibility
   actions (`recording_a11y_*`) start/cancel/send and that the camera preview has a sensible announcement.
5. **Media viewer**: swipe/zoom remain gestures. Verify previous/next/zoom buttons, spoiler reveal, save/share and
   "show in chat" are reachable; check secure/secret pages do not announce hidden content.
6. **Dialogs**: TalkBack focus should land in the dialog and return to the invoking control. Check forward, delete,
   pin-in-channel, send options, scheduled messages and recording preview.
7. **Reduce motion**: Developer options -> Animator duration scale = Off. New messages should appear without an
   entrance animation; playback/recording still works. Lottie sticker animation is content, not a UI transition, and
   is not affected by `MessageAnimationFrame` (known limitation of Lottie 6.x with `MotionDurationScale`).
8. **Contrast**: check all bundled themes (Warm, Graphite, AMOLED, wallpaper-derived) in light/dark with maximum font
   scale and display size; verify warning banners, muted timestamps and disabled send icons.
9. **Large text / display size**: chat rows, composer, media viewer footer, profile rows and settings links should not
   clip labels. Media viewer action labels wrap at 200% font scale.
10. **RTL and per-app language**: Arabic/Hebrew layout, mirrored send/back icons, localized new `a11y_*` strings.
11. **Switch Access / keyboard**: verify focus order with Tab/arrow keys, the send button does not steal text-field
    focus, and dialogs trap focus correctly.
12. **Hardware keyboard**: Enter sends, Shift+Enter makes a newline, Esc closes search/action bars.
13. **Notification and external entry points**: opening a chat from a notification/share target lands on an
    announced title and composer.

## Tests added

- `app/src/test/java/com/beykus/beygram/ui/accessibility/AccessibilitySupportTest.kt`
  - touch targets grow to 48 dp but never shrink larger controls
  - status text is not duplicated when already visible
  - merged chat-row descriptions drop blank/repeated parts
- `app/src/test/java/com/beykus/beygram/ui/screens/chat/MessageAnimationPlanTest.kt`
  - a zero system motion scale skips the entrance plan while normal motion keeps it

No Compose instrumentation tests were added. The existing `EnglishResourceCoverageTest` verifies every new `a11y_*`
string has an English counterpart and matching format arguments.
