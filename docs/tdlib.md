# TDLib integration notes

The project includes generated Java bindings (`app/libs/tdlib-java.jar`) and an Android ARM64 native library (`app/src/main/jniLibs/arm64-v8a/libtdjni.so`). Other ABIs require their own native build. The local `td-master` source checkout and build tool downloads are not part of the repository.

## Current project hooks

- `TelegramRepository` is the app-facing contract.
- `UnavailableTelegramRepository` stands in while TDLib cannot start: the start screen explains why, and no chats are shown.
- `TdLibConfig` reads `api_id`, `api_hash`, TDLib database path, files path, device model, language, and app version.
- `TdLibRuntime` checks whether the native `tdjni` library can be loaded.

## Local credentials

Add these values to `secrets.properties` in the project root. The file is ignored by Git:

```properties
telegramApiId=123456
telegramApiHash=your_api_hash
```

The values are exposed to app code as:

```kotlin
BuildConfig.TELEGRAM_API_ID
BuildConfig.TELEGRAM_API_HASH
```

Gradle reads the same two keys from `~/.gradle/gradle.properties` too. That file is shared by every `git worktree` of the repository, so a new worktree needs no copied `secrets.properties`. `git clean -x` deletes ignored files, including `secrets.properties`.

Assembling or installing a Dev or Prod APK without these values stops the build with a message. Pull request builds in CI are exempt, because GitHub does not pass secrets to them. Pushes to `main` and manual runs receive the repository secrets `TELEGRAM_API_ID` and `TELEGRAM_API_HASH`. To build without credentials anyway, pass `-PallowMissingTelegramCredentials=true`.

## Build and install native artifacts

Build TDLib with JNI enabled and package the result into the Android app:

To rebuild, first obtain the official [TDLib source](https://github.com/tdlib/td) in the ignored `td-master` directory. The following commands describe the local development environment; adapt SDK/NDK paths and versions for another machine.

- Java bindings under package `org.drinkless.tdlib`.
- Native `libtdjni.so` for Android ABIs such as `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
Build the Java interface using the official TDLib Android scripts from a Bash environment
(WSL is suitable on Windows). The current TDLib checkout requires `gperf`, `php`, a JDK,
Ninja, and OpenSSL in addition to the Android SDK/NDK. The Android script also expects
CMake 3.22.1; install that SDK component or update the script's CMake path before running it.

```bash
cd /mnt/f/BeyGram/td-master/example/android
./build-openssl.sh /mnt/c/Users/Beykus/AppData/Local/Android/Sdk 30.0.15729638
./build-tdlib.sh /mnt/c/Users/Beykus/AppData/Local/Android/Sdk 30.0.15729638
```

For the current Windows setup, Git Bash can be used instead of WSL. After installing
`gperf`, PHP, GNU Make, and a JDK, run `scripts/build-tdlib.sh` with `TDLIB_GPERF_DIR`,
`TDLIB_PHP_DIR`, and `JAVA_HOME` set to their installation directories.

Then install the generated output into the Android module from PowerShell:

```powershell
.\scripts\install-tdlib.ps1 -TdLibOutput .\td-master\example\android\tdlib
```

The installer creates `app/libs/tdlib-java.jar` and copies every required `.so` into
`app/src/main/jniLibs/<abi>`. Gradle already includes JARs from `app/libs`.

The app now checks this on the start screen:

- `telegramApiId` / `telegramApiHash` from `secrets.properties`.
- Java classes `org.drinkless.tdlib.Client` and `org.drinkless.tdlib.TdApi`.
- Native library `tdjni`, loaded by `System.loadLibrary("tdjni")`.

Expected Android location for native libraries:

```text
app/libs/tdlib-java.jar
app/src/main/jniLibs/arm64-v8a/libtdjni.so
app/src/main/jniLibs/armeabi-v7a/libtdjni.so
app/src/main/jniLibs/x86/libtdjni.so
app/src/main/jniLibs/x86_64/libtdjni.so
```

After those files are present, `BeyGramApp` automatically selects `TdLibTelegramRepository`.
Without complete artifacts it uses `UnavailableTelegramRepository`, so the project remains buildable and the start screen explains what is missing.
