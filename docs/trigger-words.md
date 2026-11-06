# Trigger words

Trigger words flag incoming messages by a word, a phrase or a regular expression. A match in a muted chat sends a separate alert and goes to the mentions feed. Matches in other chats go only to the feed, unless the scope is set to all chats, which still sends no extra alert there because Telegram already notifies for those chats.

The feature is language-independent. Nothing in the matcher depends on a language, and every user-visible string is a resource with translations in `values`, `values-en`, `values-de`, `values-es` and `values-fr`.

## Rules and matching

Each rule has a pattern, a mode, two case options and an enabled switch.

| Mode | Behaviour |
| --- | --- |
| Whole word (default) | The match must not touch a letter, digit, underscore or mark at its edges. Only edges that are word characters count, so `#release` matches `#release!`. |
| Anywhere in text | Plain substring match on the normalized text. `test` matches `tests`. |
| Regular expression | An RE2 expression (RE2J library), applied to the text as written. No lookaround or backreferences. `\w` and `\b` are ASCII, so write letters of other scripts as `\p{L}`. Matching takes time linear in the text, so no expression can stall incoming messages. Case handling follows the case switch. Diacritics are not applied. |

Plain patterns and message texts are normalized the same way before comparison:

- NFKC normalization, so compatibility forms such as full-width letters compare equal.
- Invisible format characters (zero-width spaces, soft hyphens) are removed.
- Runs of whitespace, including non-breaking spaces, collapse to one space. A phrase matches across line breaks.
- Ignore letter case (on by default) uses Unicode case folding, so `STRASSE` matches `straße`.
- Ignore diacritics (off by default) removes nonspacing marks after canonical decomposition, so `é` matches `e`. It is off by default because some letters differ only by a mark, for example `й` and `и` in Russian.

For scripts without spaces (Han, Hiragana, Katakana, Thai, Lao, Khmer, Myanmar), whole-word matching also asks Unicode word segmentation (`java.text.BreakIterator`) whether the match starts and ends on a word boundary. Android implements it with ICU, which uses dictionaries for these scripts. The JVM used by unit tests does not, so whole-word results for text without spaces can differ between the two. Use "Anywhere in text" for those languages when reliability matters.

Limits: 50 rules, 120 characters per pattern. A regular expression that matches an empty text (or a single space) is refused, because it would fire on every message. Invalid expressions are refused with a message.

## Which messages are checked

- Incoming messages only. Outgoing messages are never matched.
- Secret chats are never matched.
- The text is the message text, or the caption of a photo, video, animation, audio, document or voice note.
- Each account's repository checks its own messages, so every signed-in account is covered while its TDLib client runs.
- Checking happens in the update handler of `TdLibTelegramRepository` (`offerToTriggerWords`). Any failure there is swallowed so the update stream keeps running.

The scope is set in the trigger words screen:

- **Only muted chats** (default). Telegram stays silent in muted chats, so a match there gives an alert and a feed entry.
- **All chats.** Matches in unmuted chats give a feed entry only. Matches in muted chats give an alert and a feed entry.

## Alerts and the mentions feed

- Alerts use their own notification channel, "Trigger words", so Messages keeps its sound and importance. Each message gets its own notification tag. The tap opens the app; opening the chat at the message is not implemented yet. At most 10 trigger alerts stay in the tray: the oldest leave it, and the feed keeps every match. Android limits the notifications of one app, so an unbounded number of these alerts would also block ordinary message alerts.
- Previews on: the alert shows the sender and the message text, and the feed keeps the text. Previews off, in the account's settings or in the chat's own notification settings: the alert names only the trigger word, and no text is kept for the match. Previews off for the account also hides the text of older entries in the feed. Protected content (chats where saving is forbidden) keeps no text either. Turning previews off later removes the trigger alerts still in the tray that show that text: all of the account's alerts when the account's switch goes off, the chat's alerts when its setting goes off, and the alerts of the chats a scope covers when the scope's setting goes off. The feed keeps the matches.
- An alert follows the account's notification switch, its background switch and the Android notification permission, as other notifications do. No alert is posted for a chat that is open on screen, or for a message older than 10 minutes, so a catch-up after a reconnect does not ring for old matches. A mention is still recorded when no alert is posted.
- A mention is identified by account, chat and message. A repeated update for the same message does not add a second entry or alert.
- The feed keeps the newest 200 mentions by message date, so a catch-up of older messages cannot push out newer matches. "Clear feed" removes the records from this device only; Telegram messages are not changed.

## Storage

Everything is in SharedPreferences `trigger_words`, as JSON: the rules, the scope and the mentions. The rules are shared by all accounts. Each mention records its account, so a match from a secondary account keeps that account's identity. Signing an account out, the first one included, removes its alerts and its mentions from this device. The rules and the scope stay.

## Limits

- Matching runs only while the account's TDLib client runs: the app is open, or the background notification service is running. Doze and battery restrictions can delay the connection, as described in [notifications.md](notifications.md).
- The matcher runs on the update path synchronously for each incoming message. It folds each text once per option pair, so the cost stays small for up to 50 rules, but a very large number of messages in a burst is not measured.
- A regular expression runs in time linear in the message length, so a pattern with nested repeats cannot stall the update path. Telegram texts are at most 4096 characters, so that is the longest input.

## Checks on a device (not run)

These need a real account and a muted group. They are not established by compilation.

1. Add the word `тест` and mute a group. Send the word from another account. Expect an alert with the word and a feed entry.
2. Turn previews off for the account. Expect the alert to name only the word, and the feed entry to have no text.
3. Set the scope to all chats. A match in an unmuted chat should appear in the feed without an alert.
4. Enter `[` as a regular expression. Expect the "cannot be parsed" message and no save.
5. Put a message in a secret chat that contains a rule's word. Expect no mention.
6. Turn off background notifications for the account, then send a matching message to a muted group while the app is in the background. Expect no alert, and the feed still records the match.
7. Sign out of a secondary account that has an alert in the tray. Expect the alert and its feed entries to disappear.
8. Sign out of the first account with an alert in the tray, then sign in again. Expect the alert and the old feed entries to be gone, and the rules to remain.
9. Turn previews off for the account while a trigger alert with text is in the tray. Expect that alert to leave the tray, and the feed to keep the match with its text hidden.
