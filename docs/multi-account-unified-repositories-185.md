# Issue #185 — One BeyGram UI, account-scoped repositories

## Why this replaces the interim secondary-chat experience

The secondary TDLib **native session** and its leased database were correct, but
`SecondaryChatsScreen` and `TdSecondaryChatStore` formed a second, reduced
messenger implementation. The normal `ChatsScreen`, `ChatScreen`, folders,
profile, search, calls and media already depend on `TelegramRepository` /
`ConversationRepository` and must be shared by every account.

## Ownership (no TDLib migration, no duplicate Client)

- `TdLibTelegramRepository.getInstance(context)` keeps its original legacy
  singleton, `filesDir/tdlib` database, notification/direct-share owner and
  its existing persistent preference names.
- Each registered secondary ID is still created by
  `TdAccountNativeClientOwner` and its original `TdSecondaryNativeLogin`
  (shared authenticated Mihomo preflight and database leases unchanged).
- After that native session is READY, it can be bound to **one complete**
  `TdLibTelegramRepository` instance. The full repository **borrows** the
  existing TDLib `Client`, subscribes to the account-bound update handler and
  reuses the native owner; it MUST NOT call `Client.create` again, acquire a
  second lease or change proxy/TDLib parameters.
- The process session coordinator exposes a full `TelegramRepository`
  interface for the selected account. The original first account is never
  unregistered or moved.

## UI and data safety

- `BeyGramAppContent` uses the **selected full repository** for the same
  reusable chat list, conversation, archive, media, profile, contact, folder
  and call screens. No separate secondary chat UX for normal navigation.
- If a selected secondary session is offline/unavailable or not READY, the
  UI uses an empty unavailable repository and account management/picker,
  **never** displays first-account chats under its label.
- Per-account drafts and forum drafts have separate preferences, as do
  per-account archive settings and unconfirmed group creation state. The
  existing first-account preference filenames remain byte-for-byte unchanged.
- A notification with source `legacy` is always resolved against the
  legacy repository regardless of the currently selected UI account.
  Secondary notification IDs remain bound to their source account.
- Switching stops the old shared player and clears selected chat and forum
  IDs before mounting the new repository.

## Important verification requirements

A repository implementation can compile while real TDLib request/state
ordering still behaves differently on Android. Before this code is released to
main/testers, validate at least:
- add second + third real Telegram accounts, restart/restore, switch while a
  message is uploading or downloading, background/foreground and listener loss;
- dialogs with the **same numeric chat/message ID** in two accounts, drafts,
  forum topics, media previews and replies, search, favorites and chat folders;
- secret chats and authorization changes, individual logout without deleting
  the wrong database, account-scoped push/inline reply/outbox;
- M21 low-RAM stress and any global shared playback, media-staging, cache and
  diagnostics state before exposing the experimental feature.

This single PR is a substantial unified-repository migration, but CI alone
cannot attest that every real-device and notification scenario is correct.
Keep #185 open until those acceptance tests and account-scoped notification
backend are completed.
