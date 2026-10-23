# Issue #185 — C5: persisted foreground Telegram account selection

This step makes the **primary Chats route** display the selected source, not
a mislabeled legacy `ChatPreview`. A new account picker is available from
both chat headers. Selection is persisted in `TdAccountRegistry` and restored
after a process restart, preserving the zero-copy legacy authorization.

## Security invariants

- Only connected, READY, unpaused secondary sessions with a matching
  `chatStore.accountId` can be selected. Registry slots alone aren't logins.
- Selecting an account is a verified, durable operation. It stops primary media
  playback and clears selected chat/topic/message navigation state. It does
  **not** log out or close the other client's native TDLib session.
- The ordinary `AppRoute.Chats` renders `SecondaryChatsScreen` if secondary
  is selected. It NEVER passes a secondary `chatId` to the primary
  `TdLibTelegramRepository`.
- Unmigrated legacy-only tabs are **fail-closed** while secondary is selected:
  ChatInfo, profile, calls, folders, contacts, global search, notifications
  and the legacy ChatScreen. They can be used only after explicitly switching
  back to the legacy account; showing wrong-account content is worse than an
  temporarily unavailable action.
- Existing notification/deep link sources currently belong to the legacy
  repository only. Their owner is explicitly switched back to legacy before
  handling the legacy chat ID. No notification is forged for the secondary.
- An external share launched with secondary active does **not** open the
  legacy share targets unnoticed. The user must explicitly select primary
  (or cancel) before an existing legacy sender can be used.
- Registry corruption leaves an error screen and never writes a new registry
  automatically. The primary TDLib database is never moved or deleted.

## User experience

- Main chat toolbar → People icon → account picker, or secondary chat
  header → People icon → account picker.
- Primary row is always available if its usual session is authorized.
- Secondary row is selectable only for a live READY client; otherwise opens
  account management for explicit reconnection.
- Add account opens the existing separate login manager.
- After app process restart, a previously selected but closed secondary
  session stays visibly unavailable until the user reconnects it.
  There is no silent switch to primary, nor silent direct network fallback.

## Remaining full #185 work

The secondary chat screen has basic message browsing; secondary full media,
folders, contacts, drafts, calls, background notification/deep-link routing,
per-account logout and crash-safe automatic native-session restart are not
finished. These must be implemented before calling this a full
multi-account client. A real-device two-account QA matrix is required.

## Tests

`TdAccountUiPolicyTest` checks fail-closed routes for secondary accounts,
including an explicit primary notification producer. Android CI builds DEV,
Prod and instrumentation APKs, but does NOT sign into real accounts.
