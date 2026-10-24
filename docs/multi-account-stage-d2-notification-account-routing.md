# Issue #185 — D2: account-scoped notification deep-link routing

Legacy notification links are unchanged. A secondary TDLib notification
must carry an immutable `accountId` from its source client. Android
PendingIntent identity includes this account ID in the URI, not only mutable
extras: identical Telegram numeric chat/group/message IDs for two accounts
cannot collide.

`NotificationIntents.accountForChatIntent` checks the token (via the normal
entrypoint) and verifies that account ID, chat ID, group ID, newest notice ID
and forum topic exactly match the URI path. Tampering with the account extra
alone cannot redirect into a different account.

`NotificationOpenRequest.accountId` is saved across Activity recreation.
Before opening a secondary notification, the application validates the account
against its durable registry and selects that exact account. The selected
secondary chat ID goes to the **secondary TDLib ChatScreen** and never
`TdLibTelegramRepository.loadChat(chatId)`. If the native secondary client
hasn't restored yet, the source account remains selected and the screen
indicates that it is offline. Legacy notification groups are not dismissed
when receiving a secondary deep link.

This PR establishes the **routing boundary** only. The next stage must:
- Listen to secondary TDLib UpdateActiveNotifications and
  UpdateNotificationGroup, post account-unique Android tray tags/channels.
- Key persisted watermark/dismiss/reply outboxes by account ID.
- Preserve the source account in authenticated mutable inline-reply PendingIntents,
  including when the app process is restarted.
- Guarantee action routing to native secondary TDLib with no fallback to legacy.
- Test real Android multi-account notifications and OS restrictions.

Until that work is reviewed, do **not** emit secondary inline-reply actions,
or reuse the primary AndroidNotificationRenderer or ReplyOutbox for secondary
updates. Keep #185 open.
