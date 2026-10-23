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
- Scroll position and recording recovery view models are keyed by account,
  chat and topic. Pending video-note exports, previews, retries and failure
  notices explicitly include the source account so identical chat IDs never
  make an export eligible for a foreign account.
- The secondary TDLib full repository replays the native session's most recent
  startup state (folders, active emoji reactions, connection state and options)
  before attaching its live observer. Each client also monitors Android network
  handoffs, but a suspended/proxy-policy-blocked native client stays offline.
- Upload marker IDs are account-owned; existing numeric legacy markers keep
  their original meaning. Eviction only examines the owning account's sent
  uploads and excludes other accounts from the eviction budget.
- The process-scoped secondary session controller already attaches all READY
  secondary repositories to the notification runtime on startup/restore.
  Secondary notification preferences must come from that account's store,
  not the legacy store. Secondary inline reply intentionally remains disabled
  until the account-bound reply outbox supports authenticated background replies.
- Cold-start foreground notification service eligibility checks registered secondary
  account IDs, confirmed native restore routes and per-account enabled/background/
  authorized preferences. Only those accounts auto-restore in a background-only
  process. Primary TDLib/VpnBackend is not constructed for secondary-only delivery;
  FOLLOW_PRIMARY with an unknown legacy route stays blocked rather than going DIRECT.
  Opening the UI upgrades this controller to its full session-restoration policy.
- A foreground service started for secondary accounts observes their connected and
  pending-notification states, using the existing bounded wake-lock policy. The
  process-wide "Stop background notifications" service action disables background
  delivery for all configured accounts. Individual settings still remain scoped.

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
