# Issue #185 — Stage C3: isolated secondary chat and history preview

The first read-only view of a *real second TDLib account* is now connected to
Settings → Telegram accounts → [authorized secondary] → Preview chats.

## Safety boundaries

- `TdSecondaryChatStore(accountId)` is created **before** that account's
  `Client.create` and receives only generation-fenced source events from its
  own `TdAccountNativeClientOwner`. It is **never** wired to the legacy
  `TelegramRepository`, its notification feed, media player or profile cache.
- Chat identity is `TdAccountChatKey(accountId, chatId)`; history identity
  uses `TdAccountMessageKey(accountId, chatId, messageId)`. Numeric TDLib
  IDs cannot be reused across accounts without their account keys.
- Only main-list chats with a positive TDLib order are displayed. The store
  observes native `UpdateNewChat`, title/position/last-message/unread changes
  and new messages for histories already opened in this view.
- On explicit preview, `LoadChats(ChatListMain, 100)` requests a batch.
  Refresh can request another. Opening a chat calls
  `GetChatHistory(chatId, 0, 0, 50, false)` on **the chosen secondary native
  client** and validates both account and returned chat IDs.
- Auth must be READY and network not suspended. Closing the secondary TDLib
  client clears its in-memory projection, never the stored TDLib database.
- The preview contains **no composer or send API**. It cannot accidentally
  dispatch to the first account or reuse its chat UI state.
- Only one secondary native TDLib client may run in parallel with legacy
  during this beta, as enforced by Stage C2.

## Intentionally not full account switching

- The ordinary Chats tab, profile, media, calls, notifications, folders,
  drafts and deep links still belong to the legacy account. This preview is
  a separate, clearly labeled read-only screen.
- TDLib account-specific chat repository with full media conversions,
  paginated history, typing/drafts, send/delete/edit, mute/read actions and
  selected-account navigation remains necessary before replacing the normal
  Chats screen. No background notifications for the secondary account yet.
- Messages of non-text types appear as a generic media placeholder; media
  content is not downloaded or rendered in this stage.
- The preview loads only the first history page; do not claim it includes
  the complete history or that UI account switching is finished.
- Neither test nor preview should cause a Telegram logout or remove app data.

## Regression

Run `./gradlew :app:testDevDebugUnitTest`. New
`TdSecondaryChatStoreTest` validates duplicate numeric IDs across accounts,
explicit rejection of foreign events and messages, ordering, history
deduplication, and clearing one store without affecting another.
CI additionally compiles both app variants and Android instrumented tests;
real two-account use on a disposable Android test installation is still
required before general rollout. Keep issue #185 open.
