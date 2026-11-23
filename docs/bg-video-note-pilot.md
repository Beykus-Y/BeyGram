# BG Video pilot for video notes

The opt-in Dev build routes reads of completed, non-private video notes through
BG Video's read-only file source. Media3 still decodes the MP4, plays its audio
and renders to the existing TextureView. This pilot does not copy decoded
frames across JNI. TDLib still owns the media file. The pilot does not copy,
delete or store it in a cache. Growing downloads, secrets and view-once media
use the current reader. If the optional native library or a native read fails,
the existing reader continues from the same byte offset.

```sh
./gradlew :app:testDevDebugUnitTest --tests '*BgVideoNoteFileTest'
./gradlew :app:assembleDevDebug -PbeygramBgVideoNotePilot=true
```

Device validation belongs on an isolated emulator or dedicated test app.
Check play/pause, seek and replay on a completed note, then compare a note
still downloading and a private attachment using the existing path. Repeat
with the opt-in disabled. The pilot leaves recording, camera, export and
TDLib upload on their existing paths.
