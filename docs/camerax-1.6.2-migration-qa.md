# CameraX 1.5.3 → 1.6.2: migration and regression checks

Scope: the five CameraX artifacts in `gradle/libs.versions.toml` move together.
The app continues using the existing CameraX `Preview + VideoCapture` session,
the shared square `ViewPort`, its normal-session FPS policy, and Media3 export.
The upgrade **does not** automatically enable 60 FPS, widen the sensor FOV,
or resolve #234/#237.

References:
- https://developer.android.com/jetpack/androidx/releases/camera (1.6.0–1.6.2)
- https://github.com/Beykus-Y/BeyGram/issues/234 (FPS investigation)
- https://github.com/Beykus-Y/BeyGram/issues/237 (FOV investigation)

## Automated checks

- `python3 -m unittest discover -s scripts -p 'test_*.py'` includes
  `test_camerax_dependency_alignment.py`: all five CameraX libraries align on
  1.6.2 and `BuildConfig.CAMERAX_VERSION` reflects the runtime version.
- `./gradlew :app:testDevDebugUnitTest`: existing CameraX-facing FPS decision,
  camera mode and geometry tests plus added 1.6 regression cases for normal AE
  vs high-speed-only FPS, encoder/stream support, rotated ViewPort geometry and crop.
- `./gradlew :app:assembleDevDebug :app:assembleProdRelease :app:assembleDevDebugAndroidTest`:
  checks the application and instrumented-test APK **compile** with the new API.
- On a device/emulator capable of running instrumentation:
  `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.ui.screens.chat.CameraX162CompatibilityTest`.
  These tests construct the same `Preview + VideoCapture`, mirror, FPS and
  shared 1:1 ViewPort setup as the recorder without requiring a real camera.
  CI currently **does not execute** device/emulator instrumentation.

## Required hands-on recording matrix before shipping to main

Run the baseline DEV build with 1.5.3 and the PR build with 1.6.2 on the
**same physical device**, fixed scene and available camera lenses.

| Check | Expected / evidence to collect |
| --- | --- |
| Startup / leaving chat / returning | Preview starts without blank frame, leaks or camera-in-use error; camera releases on exit |
| Front / rear + switch mid-recording | Persistent recording continues where supported; unsupported switch is refused without corrupting the take |
| Default zoom & pinch | Initial requested zoom 1×; actual Camera2 zoom/crop evidence if diagnostics is enabled; no unrequested zoom |
| SD vs HD (480 vs 640 output) | Compare Preview/VideoCapture actual `stream` and `cropRect`, RAW and exported dimensions and visible grid markers; do not call expected square crop a defect |
| Maximum and Smoothness FPS | Compare selected range and sensor/RAW/exported frame timing; 30-only lens stays at 30; 60-capable lens attempts 60 only when the normal stream and encoder permit it |
| Rotation + mirror | Correct preview/RAW/output orientation; front mirroring follows settings |
| Torch + screen lighting | Both lights toggle appropriately; camera switch/stop clears them |
| Audio and interrupted recording | Audible audio and stable A/V sync; interrupt via app backgrounding/force-stop; no corrupted or accidentally sent media |
| Telegram send + cancel + long segments | Valid ≤640-square output and ≤60-second chunks; cancel does not send; status reflects final TDLib delivery |
| Thermal / low light | Compare measured FPS and failure paths; no assumption that advertised 60 is sustained |

Preferred devices: Samsung M21, Galaxy A55 and a second chipset/vendor where
available. Include both front and rear lenses. Record Android version, phone
model, profile, tested CameraX version, logcat errors and diagnostics report;
do **not** include video, account IDs or private chat contents in public issues.

**Merge gate:** green CI and a human review for DEV; real camera, FPS, FOV and
recording-interruption validation is still required before claiming regression-free
operation or promoting the upgrade to main.
