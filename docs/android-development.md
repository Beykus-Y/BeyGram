# BeyGram Android: build and device diagnostics

## Android SDK and ADB

The Android SDK is installed at:

`C:\Users\Beykus\AppData\Local\Android\Sdk`

ADB executable:

`C:\Users\Beykus\AppData\Local\Android\Sdk\platform-tools\adb.exe`

Check a connected device:

```powershell
& 'C:\Users\Beykus\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
```

## Build variants

- **Dev**: debuggable, has a `.dev` application ID suffix, and can be installed beside prod.
- **Prod**: non-debuggable release build. A release signing key must be configured before distribution.

Build the dev APK:

```powershell
.\gradlew.bat :app:assembleDevDebug
```

Install or update it on the connected phone:

```powershell
& 'C:\Users\Beykus\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r app\build\outputs\apk\dev\debug\app-dev-debug.apk
```

The connected phone holds a real Telegram session. Keep the package ID and signing key stable and use replacement installation only. Never uninstall the application, clear its data, or reset the TDLib database without explicit user authorization. A failed update must not fall back to a clean install.

Do not run Gradle connected instrumentation tests against the user's installed Dev/Prod package: the test installation/cleanup workflow can remove the target application and its data. Use an emulator or an isolated test application ID instead. `adb install -r` after such a test cannot recover an already removed session. See the repository's `AGENTS.md` for the persistent development rules.

Build prod:

```powershell
.\gradlew.bat :app:assembleProdRelease
```

Both variants read Telegram API credentials from the ignored local `secrets.properties` file. Never commit that file or share unredacted TDLib logs: older TDLib log levels can include login details.

For subsequent Dev updates, use `scripts/update-dev.ps1`. It requires an existing Dev installation, calls only `adb install -r`, and compares the original installation time before/after replacement. An installation error stops the script; it never uninstalls or clears data. The installation time check alone does not prove authorization: confirm `AuthorizationStateReady` after launch.

## Avatars and profiles

Chat-list and conversation-header avatars download the small TDLib photo through the shared file store. Profile photos open at full size. `UpdateChatPhoto`, user/group updates and `UpdateFile` refresh the UI without storing public image URLs.

Tap a chat's name or avatar in the conversation header to view its information: user status, available phone/username/bio/birthday, business address, number of groups in common, group/channel description, invite link, member, administrator and online counts, the owner and administrators of a group or channel (loaded with `GetChatAdministrators`; personal chats skip this request), and the first ten members with their roles and online state. «Все участники» opens a paged list (`GetSupergroupMembers` or the basic group's member list, sliced locally). Channels whose TDLib full info denies member listing hide the section. Visibility follows what TDLib makes available to the current account. Hidden phone numbers are not inferred.

Below the members block, the same screen has four tabs: Media, Files, Links and Voice. Their counts come from `GetChatMessageCount` when the info screen refreshes. Each tab's content is paged with `SearchChatMessages` using an empty query and the filter for its kind, newest first, 50 messages per page; the next page loads when the list nears its end. Photos and videos open in the viewer, which pages only through what has loaded and has no save or share action (those stay in the chat, where protected content is checked). The viewer's «Показать в чате» and taps on files, links and voice messages open the message in the chat at its position. Secret media is not previewed in the tabs and opens in the chat.

The avatar at the top right of the chat list opens My Profile. Name, surname, editable username and bio can be changed with Save; the bio limit comes from TDLib's `bio_length_max` option. Server errors remain on the editor, and partially accepted changes are refreshed rather than rolled back silently. Choosing a new profile photo shows a centered square preview and requires Set Photo before uploading. These operations target the real Telegram account and must only be submitted by the user.

The My Profile screen also exposes the theme setting, which persists across launches. Account logout, account deletion and phone-number changes are not exposed by these screens.

## Network diagnostics

Notification delivery, foreground-service behavior and limits are documented in [notifications.md](notifications.md).

Run the redacting PowerShell diagnostic script from the project root:

```powershell
.\scripts\diagnose-android.ps1
```

It reports connected devices, installed BeyGram variants, Android's validated network/VPN state, and selected TDLib connection errors. Phone numbers, API hashes, proxy secrets, and passwords are redacted before output.

Dev also writes bounded connectivity probes to the `BeyGramNet` logcat tag whenever the app's default network changes: the Android route/VPN state, TCP reachability of the DC1 address observed in TDLib, and an HTTPS request to `web.telegram.org`. These probes contain no login data and do not run in prod release. A successful TCP probe establishes socket reachability only; TDLib must still complete the MTProto handshake.

In-app logs: «Поделиться логами» is available on the phone-number login screen (before sign-in) and under Profile → Notifications → Connection diagnostics. Both produce the same redacted report from the in-memory event buffer, so the buffer covers events since the app process started; keep the app running while it tries to connect before sharing.

## Media

Conversation actions and privacy extension points are documented in [conversation-architecture.md](conversation-architecture.md). Long-press a message for Reply/Copy/Edit/Delete; available actions follow TDLib permissions. Read receipts are driven by the visible foreground viewport, and outgoing read status follows Telegram's read marker.

- Chat attachments use TDLib file IDs and `UpdateFile` events for download/upload progress. Thumbnails load separately; tapping an attachment downloads the original for viewing.
- Photos support full-screen pinch/double-tap zoom. Videos, GIF video files, audio, voice messages and video notes use Android's local players. Files can be opened with another app through a read-only FileProvider URI.
- The `+` button offers Android's photo/video picker or the document picker, up to ten attachments per selection. Selected attachments have a preview, optional caption, removal and cancel controls. Sending starts only after the user presses Send. Multiple attachments are currently separate messages; the caption belongs to the first one.
- Photo mode exports an oriented JPEG with a maximum dimension of 2560 pixels and a 320-pixel thumbnail. Extreme panoramas are padded to Telegram's maximum aspect ratio of 20. File mode preserves original bytes and name.
- MP4/H.264 videos are sent as videos, with metadata and a thumbnail. Other codecs/containers are sent as original documents; transcoding is not implemented. Streaming is not advertised because the original MP4 index may be at the end.
- Prepared uploads are stored in the app's private `files/uploads` directory. Canceled selections are removed; accepted uploads remain available to TDLib for background sending. No broad gallery/storage permission is requested.
- Self-destructing photo/video viewers block screenshots and external opening. Content is marked opened after its local file is available in the viewer. Animated TGS stickers currently show their thumbnail; WebP/WebM stickers are viewable.

Validation covers TDLib media mapping, outgoing request fields, file progress/state ordering, photo resizing/FileProvider access, unchanged document bytes, and video metadata/thumbnail preparation. Device tests use generated local fixtures and never send media to Telegram contacts.
