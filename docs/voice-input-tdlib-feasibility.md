# Voice input through TDLib: feasibility

Issue: [#1128](https://github.com/Beykus-Y/BeyGram/issues/1128). Research only — nothing here is
implemented, and TDLib is not updated.

Method: the API surface was read from the shipped `app/libs/tdlib-java.jar`
(`org.drinkless.tdlib.TdApi`, TDLib `1.8.65`, see
[`docs/tdlib-reproducibility.md`](tdlib-reproducibility.md), added by PR #1162), not from current
upstream documentation.
Upstream behaviour was then checked against the matching `td/generate/scheme/td_api.tl` snapshot and
against current `tdlib/td` sources.

Evidence marks: **Confirmed** (read in this repository or upstream), **Derived** (conclusion from
those facts), **Assumption**, **Unknown**.

## 1. Verdict

| Question | Answer |
| --- | --- |
| Can BeyGram dictate into the composer with the shipped TDLib `1.8.65`? | **No, not through TDLib.** The file-based recognition request does not exist in this version. This says nothing about Android's own `SpeechRecognizer` / on-device recognition API, which is a separate option outside this research. |
| Can the shipped version recognize anything at all? | **Yes**, but only a *voice note or video note message that is already in a chat* (`recognizeSpeech`), not a local file. |
| Does dictation therefore require sending a voice message? | **Yes, with the current library.** That is the only input the shipped API accepts. |
| Is an update required? | **Yes** — at least to the TDLib version that added `recognizeSpeechInFile`, `getSpeechRecognitionLanguages` (both 2026-10-29) and `updateSpeechRecognitionInFile` (2026-10-29). That is TDLib **1.8.69 or newer**; BeyGram ships 1.8.65. |
| Is the work blocked? | **Not on research.** Blocked on an owner decision to update TDLib, which the current rules forbid without separate approval. |

**Scope note.** The verdict covers TDLib only. Android offers its own `SpeechRecognizer` (including on-device
recognition where the service is available), which needs no TDLib update and no Telegram servers; choosing it is a
separate product decision with its own privacy and language-coverage trade-offs, and it is not evaluated here.

## 2. Verified API signatures in the current library

Checked by enumerating the 3 547 classes of `tdlib-java.jar` and by `javap`:

| TDLib type | Present in 1.8.65 | Signature |
| --- | --- | --- |
| `RecognizeSpeech` | **yes** | `long chatId, long messageId` → `Ok` |
| `RateSpeechRecognition` | **yes** | `long chatId, long messageId, boolean isGood` → `Ok` |
| `UpdateSpeechRecognitionTrial` | **yes** | `int maxMediaDuration, int weeklyCount, int leftCount, int nextResetDate` |
| `SpeechRecognitionResult` | **yes** | abstract base |
| `SpeechRecognitionResultPending` | **yes** | `String partialText` |
| `SpeechRecognitionResultText` | **yes** | `String text` |
| `SpeechRecognitionResultError` | **yes** | `TdApi.Error error` |
| `VoiceNote.speechRecognitionResult` | **yes** | field, `SpeechRecognitionResult` |
| `VideoNote.speechRecognitionResult` | **yes** | field, `SpeechRecognitionResult` |
| `RecognizeSpeechInFile` | **no** | — |
| `GetSpeechRecognitionLanguages` | **no** | — |
| `UpdateSpeechRecognitionInFile` | **no** | — |
| `SpeechRecognitionLanguages` | **no** | — |
| `GetSpeechRecognitionModels` | **no** | — |
| `SpeechRecognitionModel` / `SpeechRecognitionModels` | **no** | — |

The complete set of speech-related classes in the jar is: `RecognizeSpeech`, `RateSpeechRecognition`,
`UpdateSpeechRecognitionTrial`, `SpeechRecognitionResult{,Pending,Text,Error}`,
`CallProblemDistortedSpeech` (unrelated, a call-quality enum).

**Derived:** a search for the requested names returns nothing, so this is not a naming difference —
the functionality is absent from the library.

## 3. Upstream additions and the required version

From `tdlib/td` history (`gh api search/commits`):

| Upstream commit | Date | Added |
| --- | --- | --- |
| `fb273279e8fd` | 2026-10-29 | `td_api::recognizeSpeechInFile` |
| `6484a38a3821` | 2026-10-29 | `td_api::getSpeechRecognitionLanguages` |
| `3745d89c79da` | 2026-10-29 | `td_api::updateSpeechRecognitionInFile` (partial results) |
| `659c72e47474` | 2026-11-14 | `td_api::getSpeechRecognitionModels` (on-device models) |

Version bumps around them: `0e88653dcbf4` "Update version to 1.8.69." (2026-10-20) and
`23383aef218a` "Update version to 1.8.70." (2026-11-08).

**Derived:** the minimum library for file-based dictation is **1.8.69**; partial-result streaming needs
the same version; on-device models need **1.8.71**. BeyGram ships **1.8.65**, i.e. four version
strings behind, which in upstream terms is roughly four months of API and database changes
(`Version.h` enum additions included).

## 4. Capability table

Two different features are easy to confuse. Everything below is split accordingly.

### 4.1 Current API: `recognizeSpeech(chatId, messageId)`

| Aspect | Behaviour | Evidence |
| --- | --- | --- |
| Input | A **message in a chat**: voice note or video note | tl doc: "Recognizes speech in a video note or a voice note message" |
| Eligibility | `messageProperties.can_recognize_speech` must be true | tl doc |
| Premium | Not required to attempt. Without Premium the server applies a trial quota | `updateSpeechRecognitionTrial.max_media_duration` is documented as "maximum allowed duration ... **without** a Telegram Premium subscription" |
| Free quota | `weekly_count` recognitions per week, `left_count` remaining, `next_reset_date` for the reset | `updateSpeechRecognitionTrial` |
| Exhausted quota | Server refusal. BeyGram already treats an exhausted quota as `left_count <= 0` and shows the reset date | `SpeechRecognitionErrors.kt` |
| Duration limit | `max_media_duration` seconds for free accounts; longer media fails with `MSG_VOICE_TOO_LONG` | tl doc of `speechRecognitionResultError` |
| Languages | **No language selection.** Server detects the language; no `getSpeechRecognitionLanguages` in 1.8.65 | jar |
| Result delivery | Asynchronous: `voiceNote.speechRecognitionResult` changes, i.e. through `UpdateMessageContent`. Partial text arrives as `SpeechRecognitionResultPending.partialText` | BeyGram `ConversationRepository.recognizeSpeech` comment: "the result arrives through UpdateMessageContent" |
| Errors | `SpeechRecognitionResultError.error` and the immediate request error: `FLOOD_WAIT`, `PREMIUM_ACCOUNT_REQUIRED`, `MSG_VOICE_TOO_LONG`, "Message can't be transcribed" | `SpeechRecognitionErrors.kt` |
| Secret chats | Not allowed; BeyGram refuses locally before sending | `TdLibTelegramRepository.recognizeSpeech` |
| Privacy | Audio is recognized **on Telegram servers**; the message already exists in the account | tl doc; no local-recognition type in 1.8.65 |
| Rating | `rateSpeechRecognition` lets the user score a result | jar |

### 4.2 Future API: `recognizeSpeechInFile(audioFile, recognitionId)`

| Aspect | Behaviour | Evidence |
| --- | --- | --- |
| Input | A local audio file (`InputFile`), uploaded to Telegram | tl doc at `master` |
| Output | The recognized text is the **direct result** (`= Text`) | tl doc |
| Format | **OGG container with Opus codec only** | tl doc: "The file must be in OGG format with the Opus codec" |
| Size | **up to 1 MB** | same tl doc |
| Availability | Requires `getOption("can_recognize_speech")` **or** an available trial quota | same tl doc |
| Secret chats | "must not be used in secret chats" | same tl doc |
| Partial results | Opt in by passing a non-zero `recognition_id`; text then arrives through `updateSpeechRecognitionInFile(recognition_id, partial_text)`; pass `0` for no partials | tl doc of both types |
| Languages | `getSpeechRecognitionLanguages(chatId)` returns 1–3 most probable IETF tags for voice notes in that chat (`chatId = 0` for none). This is *detection*, not a choice of recognition language | tl doc |
| On-device path | `getSpeechRecognitionModels(version)` returns downloadable model documents plus language/voice-activity detection models; "if the request fails ... built-in models or `recognizeSpeech` must be used instead" | tl doc, added 2026-11-14 |
| Cancellation | **No cancel method exists.** `recognition_id` only routes partial updates; abandoning the request client-side does not visibly stop the server-side work | Derived: no `cancelSpeechRecognition` in the schema |
| Errors | Same codes as 4.1 (`PREMIUM_ACCOUNT_REQUIRED`, `FLOOD_WAIT`, `MSG_VOICE_TOO_LONG` is replaced by the 1 MB limit) | Derived from inherited error handling |

## 5. Compatibility with BeyGram's recorder

**Confirmed** from `app/src/main/java/com/beykus/beygram/ui/screens/chat/AudioNoteRecorder.kt`:

- container `MediaRecorder.OutputFormat.OGG`, codec `AudioEncoder.OPUS`;
- mono, 48 000 Hz, 96 kbit/s;
- output `voice-note.ogg`, `mimeType = "audio/ogg"`, `MediaKind.Voice`.

**Derived:**

- The container/codec already matches what `recognizeSpeechInFile` requires — no re-encoding is needed
  for compatibility, only a size cap.
- At 96 kbit/s, the 1 MB limit is reached after ≈ **87 seconds**. Dictation is usually short, but a long
  dictation would need either a lower bitrate (24 kbit/s ≈ 5 min 50 s) or chunking with several
  requests. Telegram's own voice notes use a much lower bitrate than 96 kbit/s, so a dedicated
  dictation profile is the natural choice.
- The recorder writes into `filesDir/uploads/<uuid>/voice-note.ogg`; a dictation flow would need a
  temp file that is deleted after recognition and never sent.

## 6. What BeyGram already has (reuse map)

The transcription feature for *sent* messages already implements most of the surrounding work, which
makes a future dictation feature cheaper than it looks:

| Existing piece | File | Reuse for dictation |
| --- | --- | --- |
| Trial/quota state | `data/tdlib/SpeechRecognitionTrialStore.kt` | As is |
| Quota model and UI hints | `domain/model/SpeechRecognition.kt` | As is |
| Error classification (Premium, quota, duration, flood, network, secret) | `data/tdlib/SpeechRecognitionErrors.kt` | Add the file-specific reasons (`FILE_TOO_BIG`, unsupported format) |
| Repository contract | `domain/repository/ConversationRepository.kt` (`recognizeSpeech`, `rateSpeechRecognition`, `speechRecognitionTrial`) | Add one method next to it; same `Result`/failure shape |
| UI transcription surface | `ui/screens/chat/MessageTranscription.kt`, `VoiceBubble.kt` | Not reused directly — dictation needs composer UI |
| Recorder | `ui/screens/chat/AudioNoteRecorder.kt` | New dictation mode, same encoder settings |

## 7. Architecture proposal (after an approved TDLib update)

Presentation/domain/data separation as elsewhere in the app; no second TDLib client, no new
`TelegramRepository`.

1. `domain`: `SpeechDictationRepository` (or a method group on the existing conversation contract)
   with `recognizeFile(file: File, wantPartial: Boolean): Flow<DictationResult>` where
   `DictationResult` is `Partial(text)` / `Final(text)` / `Failure(SpeechRecognitionFailure)`.
2. `data/tdlib`: one implementation built on `TdApi.RecognizeSpeechInFile(InputFile.LocalFile(path),
   recognitionId)`, plus a map `recognitionId -> continuation` for
   `UpdateSpeechRecognitionInFile`. Single `recognitionId` per request, monotonic counter, cleared on
   completion, cancellation or client restart.
3. Composer: press-and-hold mic in the message input opens a dictation sheet with partial text, a
   cancel action and a "insert" action. Nothing is inserted without an explicit tap, and no message is
   sent automatically.
4. Gate: `getOption("can_recognize_speech")` (new synchronous option, 1.8.71) or the existing trial
   quota; both are already obtainable through the existing option/trial plumbing.
5. Privacy: the audio file goes to Telegram for server-side recognition. The UI must say so once, not
   imply on-device processing. Delete the temp file on cancel, failure and success.
6. Secret chats: hide the dictation entry, matching the existing local refusal in
   `TdLibTelegramRepository.recognizeSpeech`.

Deliberately **not** proposed: sending the recording as a voice message first and transcribing it,
because that publishes the message to the chat, notifies the other side, and cannot work before the
message exists.

## 8. Complexity estimate

| Part | Size | Notes |
| --- | --- | --- |
| TDLib update 1.8.65 → 1.8.69+ | **Large, separate task** | Rebuild `libtdjni.so` + regenerate bindings; TDLib `Version.h` database upgrade path; full regression of chats, media, calls, secret chats; owner approval required |
| Dictation data layer | Medium | One TDLib call, a partial-update router, temp-file lifecycle |
| Recorder dictation mode | Small–medium | Reuse `AudioNoteRecorder`; add bitrate profile and size guard |
| Composer UI + 5 locales | Medium | New strings in `ru/en/de/es/fr`, insets, IME handling |
| Tests | Medium | Fake repository, partial-result ordering, quota/Premium refusals, 1 MB guard, secret-chat gate, temp-file cleanup |

## 9. Not verified in this research

- Actual quota numbers (`weekly_count`, `max_media_duration`) — these are server values, visible only
  through `updateSpeechRecognitionTrial` on a live account.
- Whether `can_recognize_speech` is true for a free account in the current server configuration.
- Whether Telegram accepts a 48 kHz Opus file produced by Android's `MediaRecorder` — the format matches
  the documented requirement, but only a device test can confirm it.
- Real behaviour of partial updates for a file request (order, duplicates, final update presence).
- Whether an abandoned `recognizeSpeechInFile` request keeps consuming quota.
- On-device recognition (`getSpeechRecognitionModels`) quality, model sizes, and licensing of the
  model documents.
- Any behaviour of TDLib 1.8.69+ beyond the `.tl` documentation — no build of that version was made or
  run here.

Nothing was sent to Telegram, no audio was uploaded, and the owner's account was not used for any
test.

## 10. Recommendations

1. **Do not implement dictation on 1.8.65.** The only available path requires sending a voice message
   first, which changes the product behaviour and cannot be undone.
2. **Treat "TDLib 1.8.69+" as a prerequisite**, tracked as its own task with its own approval, rebuild
   and regression plan. The reproducibility record in
   [`docs/tdlib-reproducibility.md`](tdlib-reproducibility.md) (PR #1162) is the input for that task.
3. **Keep the existing transcription feature untouched** — it already works, and dictation is additive.
4. When approved, implement the data layer first behind a disabled-by-default flag, then the UI, and
   measure real quota and 1 MB behaviour on a device before showing the entry point to users.

## 11. Sources

- `tdlib/td` `td/generate/scheme/td_api.tl` at `a17f87c4cff7` (schema of the shipped bindings) and at
  `master` (newer speech API).
- `tdlib/td` `td/telegram/OptionManager.cpp` — the synchronous `version`/`commit_hash` options.
- Commits `fb273279e8fd`, `6484a38a3821`, `3745d89c79da`, `659c72e47474` — addition of the file-based
  speech API.
- Local: `app/libs/tdlib-java.jar` (`javap` output), `app/src/main/java/com/beykus/beygram/data/tdlib/`,
  `app/src/main/java/com/beykus/beygram/ui/screens/chat/AudioNoteRecorder.kt`.