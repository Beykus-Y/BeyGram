# Chat export (#379)

The chat overflow menu offers **Export chat**. The export is written to a document chosen through Android's
Storage Access Framework (`CreateDocument`). BeyGram never writes plaintext export data into shared Downloads,
the app cache, or a private temporary file.

## What is exported

- JSON or HTML, selected in the dialog. Both formats contain message id, UTC/ISO and local time, sender, text,
  service events, edited flag, reply target and media metadata.
- An optional date range with the same half-open semantics as chat search: the start day is inclusive and the
  end day is exclusive.
- Media is off by default. When enabled, the chosen document is a ZIP containing `messages.json` or
  `messages.html` and a `media/` folder. Files already downloaded are streamed from Telegram's private store.
  Missing files are downloaded only when they are at most 25 MB each, with a 100 MB total download budget and
  at most 10,000 assets; otherwise the attachment is listed with metadata only.
- The source account label is shown in the setup dialog, the progress dialog and the exported header, so a
  user with several accounts always knows which account is being exported.

## Privacy rules

- Secret chats are refused before the target is opened.
- Chats with `Chat.hasProtectedContent` are refused before the target is opened.
- Individual messages with `Message.canBeSaved == false` are skipped and counted in the export summary.
- The setup dialog warns that the exported document is outside BeyGram's protection and may be read by other
  applications or cloud synchronization.

## Streaming, progress and cancellation

The TDLib source pages history with `GetChatHistory` from newest to oldest, 100 messages per request. Pages are
never accumulated: each message is serialized immediately into the target stream, and media bytes are copied
with a 64 KB buffer. When media is requested the history is walked twice (media first, then the document), so a
large export does not need a temporary plaintext copy; the UI receives progress after every page and every media
file and can cancel the job.

## Background behaviour

The export runs in `ChatExportViewModel.viewModelScope`, i.e. in the app process while the UI is alive. It
survives activity recreation (rotation) because the job belongs to the ViewModel. It does **not** run as a
foreground service and may stop if Android kills the process in the background. The progress dialog therefore
asks the user to keep BeyGram open.

On cancellation or any error, the exporter closes the stream and deletes the SAF document. If the provider refuses
deletion, it truncates the document to zero bytes. All cleanup runs in `NonCancellable` context, so it is not
skipped when the coroutine is already cancelled. Full history and media are never loaded into memory, so a large
chat does not cause OOM.

An Android process kill cannot execute cleanup. In that case the user-selected document may remain incomplete,
which is why the progress dialog explicitly asks the user to keep BeyGram open. BeyGram creates no plaintext
export copy in its cache or private storage; the only export artifact is the SAF document the user chose.
Downloaded media may stay in TDLib's normal media cache, as it does after any other media view.

## Multi-account isolation

`TdChatExporter` is constructed by each `TdLibTelegramRepository` and uses only that repository's TDLib client.
The export job is keyed by account id and chat id in Compose, and no data is read through another account.
