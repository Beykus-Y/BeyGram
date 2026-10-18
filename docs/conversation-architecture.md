# Conversations and future privacy features

## Boundaries

- `domain/repository/ConversationRepository.kt` is the UI port for conversation queries, commands and user interaction events. UI components do not construct TDLib requests or set protocol flags.
- `domain/model/ChatMessage.kt` holds immutable messages, replies, capabilities, server read markers and limits. Permissions are queried separately because they can change while a message remains on screen.
- `data/tdlib/TdConversationActions.kt` implements sending, replies, edit/delete, reply hydration and context retrieval. Each edit/delete/reply rechecks TDLib permissions at execution time. Responses and updates converge on the same message identity in `MessageHistoryStore`.
- `data/tdlib/TdConversationEffects.kt` is the only gateway for `ViewMessages`, `OpenMessageContent`, the TDLib `online` option and `SendChatAction`. These effects are deliberately independent of history retrieval and message commands. Chat activity (typing, recording, uploading) starts from the composer and from TDLib upload updates, and is sent again while it lasts.
- `domain/model/InteractionPolicy.kt` decides whether an effect is permitted for the account and optional chat. `Standard` preserves ordinary Telegram behavior. `ConfigurableInteractionPolicy` accepts account preferences and per-chat overrides as providers; a future settings store can supply these without changing screens.

The policy is injected when the process's TDLib client/repository is created (`getInstance(context, policy)`). After changing a provider's settings, call `refreshInteractionPolicy()` to apply presence and clear view deduplication. This does not replay suppressed receipts. A future multi-account implementation must scope the repository, TDLib database and policy to an account, rather than reuse one singleton across accounts.

## Read receipts and presence

The Compose screen reports stable viewport IDs after 350 ms, only while its lifecycle is resumed and its window focused. An item must overlap the viewport by at least half its height or 64 dp, whichever is smaller. Pending and failed local messages are excluded. Headers and off-screen items are excluded.

The effects gateway additionally checks authorization, app foreground state, the opened chat and current policy. Read batches are deduplicated for that chat session and use `force_read=false`. Failed reads have bounded retries that recheck these conditions before sending. Loading history, hydrating a reply, opening a profile and fetching reply context never call `ViewMessages`.

Media download is separate from content-open receipts. Images report opening in the viewer, audio/video when playback starts, and documents when Android accepts the open-file intent. App pause/resume controls presence through the same policy boundary.

Inbox/outbox markers come from the initial TDLib Chat and subsequent read updates. The unread count follows `UpdateChatReadInbox`. One check means Telegram accepted an outgoing message; two checks mean its ID is covered by Telegram's outgoing read marker. This does not claim a separate recipient-device delivery acknowledgment or identify individual group readers.

## Replies, editing and deletion

Replies carry the original chat/message identity and a short preview. Loaded originals are reached by scrolling and highlighting. Distant originals open a separate context window; their history is not appended to the main message list, avoiding a gap that would break older-message pagination. Unavailable originals have an explicit error state.

Text and media sends can carry a reply; the first accepted attachment consumes the composer reply target. Topic metadata is inherited from the original message. Selecting Reply, Copy or Edit does not itself send a Telegram message.

Edit and delete menus use `GetMessageProperties`, including protected-content copy restrictions and available deletion scopes. Editing supports plain text and supported media captions, preserves caption placement, and uses TDLib's current text/caption limits. Rich text entity editing is a future feature. Deletion is confirmed explicitly and defaults to self-only when that scope is available.

Native content/edit/delete updates patch visible history. In-flight history pages carry a revision captured before their request, so a late page cannot roll back a newer live edit or resurrect a permanently deleted/retired message. Temporary-to-server ID replacement remains authoritative.

## Extending privacy behavior

Future unread mode can deny `ReadHistory`; content receipts and online presence are separate decisions. `Typing` gates every chat activity shown to other members (typing, recording and uploading), and the sender rechecks it while an activity lasts. Any new presence, typing, draft-sync or read side effect must pass a domain policy boundary; never add a direct native call to a composable or history loader.

Policy suppression alone must not be advertised as guaranteed invisibility: server behavior can reveal activity through other actions. Before exposing a mode that suppresses content receipts, handle self-destructing/view-once media before loading or playback; silently displaying it while dropping its required open event is not a complete implementation. No stealth settings are exposed by this slice.

## Validation and deployment

Build Dev/Prod APKs without running connected instrumentation tests on the user's account. Use `scripts/update-dev.ps1` for an existing Dev installation; it only replaces the APK and checks the original install time. Verify authorization after launch. Actual sends, edits and deletions in the user's account are left for the user to trigger through the UI.
