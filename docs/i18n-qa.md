# BeyGram localization audit — 2026-10-21 (Issue #228)

## Supported language behavior

- `ru` is the default resource language (`values/`).
- `en` has a matching explicit translation for every default-language `<string>` key (`values-en/`).
- **System language:** `AppLocale.SYSTEM` follows the Android locale. Languages other than Russian and English deliberately fall back to the default resources until explicitly supported.
- Language selection is stored by `AppLocale`, applies Android 13+ application locales via `LocaleManager`, and recreates the Activity to refresh Compose strings. Before Android 13, `attachBaseContext` wraps the context in the selected locale. Device verification is still required.
- Server-supplied names, messages, URLs, file names and stable diagnostic payload fields are not translated. Only UI wording is localized.

## Automated checks

`EnglishResourceCoverageTest` checks on JVM:
1. Every default `strings*.xml` resource has an English translation, including errors and empty/error states.
2. Format placeholders (`%1$s`, `%1$d`, `%1$.1f`, `%%`, etc.) match between languages.
3. Translations are nonempty and no duplicate keys appear across the locale's multiple XML files.

## Manual acceptance matrix (not covered by JVM tests)

| Screen/flow | English | Russian | System |
|---|---|---|---|
| Authentication, phone, 2FA, errors | Check | Check | Check |
| Chats, archive, folders, global search | Check | Check | Check |
| Message composer, replies, reactions and media | Check | Check | Check |
| Group/channel profile, members, gallery | Check | Check | Check |
| Create chats/groups/channels and forum topics | Check | Check | Check |
| Appearance, wallpaper, bottom navigation | Check | Check | Check |
| Proxy subscription, latency check, failure states | Check | Check | Check |
| Notifications, Android permission UI | Check | Check | Check |
| Storage, sessions, last seen, updates | Check | Check | Check |
| Video notes, voice recording, playback | Check | Check | Check |
| Diagnostics, resource monitor, report submission | Check | Check | Check |

On Android 10 and Android 13+, switch RU → EN → System → RU **without force stopping** the app. Check immediate recomposition after activity recreation, configuration changes, back navigation to a previously opened screen, error dialogs, TalkBack announcements and truncation with large fonts. On Android 13+, confirm the system app-language setting stays in sync. Test English with a non-Russian system language to detect fallback to the default language.

The above is a checklist, **not evidence of completed device tests**. Keep issue #228 open until real-device screenshot review catches any hard-coded user-facing strings outside `strings*.xml`.
