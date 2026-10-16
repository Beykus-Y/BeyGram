# TDLib integration notes

The project includes generated Java bindings (`app/libs/tdlib-java.jar`) and an Android ARM64 native library (`app/src/main/jniLibs/arm64-v8a/libtdjni.so`). Other ABIs require their own native build. The local `td-master` source checkout and build tool downloads are not part of the repository.

## Current project hooks

- `TelegramRepository` is the app-facing contract.
- `MockTelegramRepository` keeps the UI usable while TDLib is not packaged.
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
Without complete artifacts it continues to use `MockTelegramRepository`, so the project remains buildable.
