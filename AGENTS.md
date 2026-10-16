# BeyGram: preserve the user's Android session

- The connected phone contains the user's real Telegram account. Preserve application data and the TDLib database during all development work.
- Update the existing Dev application only with `adb install -r`. Keep its application ID and signing key stable.
- Never uninstall the user's application, clear its data (`pm clear`), delete/reset its TDLib database, or fall back to a clean install unless the user explicitly authorizes that exact destructive action.
- Do not run `connectedAndroidTest`, `connectedDevDebugAndroidTest`, or other instrumentation/install workflows against the user's existing Dev/Prod package. Such workflows may uninstall/reinstall the target and erase its account. Run device tests only on an emulator or a separate test application ID with isolated data.
- If an update fails because of a signature/version conflict, stop the installation and explain the conflict. Do not fix it by removing the installed application.
- Building an APK does not authorize resetting application data. Do not claim the account/session was preserved without verifying it.

## Local Android tools

- SDK: `C:\Users\Beykus\AppData\Local\Android\Sdk`
- ADB: `C:\Users\Beykus\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- Dev package: `com.beykus.beygram.dev`
- Prod package: `com.beykus.beygram`
