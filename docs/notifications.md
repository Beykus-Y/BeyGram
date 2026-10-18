# Notifications without Google services

## Delivery and lifecycle

No Firebase, Google Play Services, device push registration or app-owned relay server is used. The existing TDLib client maintains a direct Telegram connection. `TelegramNotificationService` shares the process singleton with the UI; it never opens a second TDLib database/client.

The background connection is optional and user-visible through an ongoing, silent notification with a Stop action. BeyGram does not remove that notification programmatically while the service runs, because Android requires a foreground service to show one. The user can change the channel in Android settings, which the notification settings screen links to; how a hidden foreground-service notification behaves depends on the Android version and device. The service uses Android's `specialUse` foreground type, with the direct messaging use case declared in the manifest. It is not mislabeled as media playback or a timed data-sync job. Android documents the type and its declaration requirements in [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use). A future Play Store submission would need the declared use case reviewed there.

Start requests come only from a resumed UI or permitted boot/package-replacement broadcasts. The service uses `START_STICKY` for system recreation, and its receiver requires a previously authorized account, enabled background delivery and Android notification access. Force-stop is respected: reopening the app is required. No exact alarms, repeating polling jobs or restart loops are used. Wake locks have explicit time bounds: startup holds one for at most 15 seconds and releases it at the first connected state, while pending delivery holds one for at most 60 seconds and only while TDLib reports pending deliveries.

Android Doze and manufacturer battery restrictions can delay a direct socket connection. The settings screen offers a user-initiated battery exemption request and reports its current status. A foreground service alone is not a guarantee of instant delivery in deep idle. See Android's [Doze and App Standby guidance](https://developer.android.com/training/monitoring-device-state/doze-standby). Direct delivery also requires the same working Telegram route/VPN/proxy as the app, and can cost more battery than shared push transport.

## Data flow

1. `NotificationRepository` exposes immutable `TelegramNotifications` and presentation state, plus dismiss/navigation operations.
2. `TdNotifications` consumes TDLib's notification API, explicitly enabling `notification_group_count_max=25` and `notification_group_size_max=10`. It applies startup snapshots, group additions/removals and content changes. It does not infer notifications from downloaded history or unread counts.
3. TDLib decides which ordinary messages/mentions should notify according to Telegram's server settings. `notification_sound_id=0` and explicitly silent messages suppress sound. Custom Telegram ringtone files are not downloaded in this slice; otherwise the Android Messages channel controls the sound.
4. `NotificationRuntime` combines source state, local settings, auth and foreground/opened-chat state. `AndroidNotificationRenderer` displays one MessagingStyle notification per native group. Android handles notification access and channels. Active-chat notifications are suppressed while that chat is open in the foreground.
5. Native removals (including reads from another device) cancel the corresponding Android entries. Startup restoration and content edits are silent. Per-group handled watermarks prevent repeated alerts after reconstruction/settings changes. An alert stays pending until the system accepts its post: the group's alert id survives content updates and TDLib snapshots while its notice exists, and the handled watermark moves only after a successful post or a deliberate suppression (open chat, disabled notifications, no permission). Stale tray entries are cancelled only after TDLib's state has arrived (its startup snapshot or the first group update), so a cold start does not clear the tray. A notification API enabled after the startup phase, with no snapshot, still reconciles removals from its first group update. A chat's archived flag is re-read whenever its list position changes.

The grouping and startup behavior follow the [TDLib Notification API](https://core.telegram.org/tdlib/notification-api). Groups may represent ordinary messages, mentions or new secret chats. Calls are not rendered because the app does not implement Telegram calls. FCM push-only notification types are not registered or processed.

## Privacy and user actions

- System notification access is requested once after successful login. Denial is respected; the user can reopen the app's notification settings. Background service operation is gated on notification access.
- Local preview settings and TDLib's `show_preview` are both enforced. Secret-chat content, and content whose chat metadata is not resolved yet, is hidden. Lock-screen public versions reveal only a generic new-message indication.
- Explicit immutable PendingIntents use distinct identities and a private capability token for exported activity actions. A notification tap routes to the correct chat, including a chat outside the main list. Failed lookup shows an error. The Activity handles new intents and preserves unconsumed navigation across recreation.
- Swiping away a notification stores a per-group dismissal watermark immediately, then calls TDLib `RemoveNotificationGroup`. Removal is retried with bounded backoff (four attempts) and again on authorization or connectivity changes. It never invokes `ViewMessages`, `OpenMessageContent` or an online-presence operation. An old dismissal cannot erase a newer notice ID in that group.
- Opening a chat leaves read decisions to `ConversationRepository` and its interaction policy. The service never announces that the app UI is in the foreground. This keeps future unread/stealth behavior independent of notification delivery.

Settings are under the bell in the chat list and My Profile → Notifications and background operation. The user can disable notifications, disable the persistent connection, hide text, open the Android settings of the Messages and background-connection channels, or request unrestricted battery operation. A Messages channel blocked in Android is shown on the settings screen. No app setting overrides Android notification permission or a blocked channel.

## Development

Build with `assembleDevDebug` / `assembleProdRelease`, then use `scripts/update-dev.ps1` for a replacement update. Do not run connected tests on the user's installed package or reset app data.

The `BeyGramNotify` Dev log contains only update class names and active-group counts. Do not dump notification payloads, sender names, message text or intent tokens into diagnostics. Validate actual delivery with a message supplied by the user; do not send messages to their contacts for testing. Deep-idle, OEM restart behavior and reboot delivery require separate user-approved device checks and are not established by compilation.
