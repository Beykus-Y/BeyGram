# Third-party components

## TDLib

`app/libs/tdlib-java.jar` and `app/src/main/jniLibs/arm64-v8a/libtdjni.so` are generated/built from [TDLib](https://github.com/tdlib/td), the Telegram Database Library by Telegram Messenger and contributors. TDLib is provided under the Boost Software License 1.0; see [LICENSES/TDLib-Boost-1.0.txt](LICENSES/TDLib-Boost-1.0.txt).

The generated Java bindings and the native library must be updated together. The local source checkout and intermediate native builds are excluded from Git.

## OpenSSL

The native build scripts use [OpenSSL 3.3.0](https://github.com/openssl/openssl/tree/openssl-3.3.0), licensed under Apache License 2.0. See [LICENSES/OpenSSL-Apache-2.0.txt](LICENSES/OpenSSL-Apache-2.0.txt).

## Source Sans 3

`app/src/main/res/font/source_sans_3.ttf` is Source Sans 3 by Adobe, licensed under the SIL Open Font License 1.1. See [LICENSES/SourceSans3-OFL-1.1.txt](LICENSES/SourceSans3-OFL-1.1.txt).
## Mihomo Android core

The Dev build resolves `libmihomo-android` **v0.3.11**, bundling Mihomo **v1.19.32**, from the upstream GitHub Release during Gradle builds. The AAR is pinned by SHA-256 `01bc41748840c8200bbaf08c044ba8704e35303a3179b75a3cce8517c8944251` and cached under `app/build/`; it is not committed as a binary. The bridge and core are licensed under GPL-3.0. See [LICENSES/libmihomo-GPL-3.0.txt](LICENSES/libmihomo-GPL-3.0.txt) and the copy included in the APK at `assets/licenses/libmihomo-GPL-3.0.txt`.

Upstream sources: [libmihomo-android v0.3.11](https://github.com/oviron/libmihomo-android/tree/v0.3.11) and [mihomo v1.19.32](https://github.com/MetaCubeX/mihomo/tree/v1.19.32). Before distributing an APK that contains this linked native core, prepare the corresponding source and satisfy the GPL-3.0 terms for the combined work. This project currently does not declare a license for BeyGram's original code.

## Other build/runtime dependencies

AndroidX/Jetpack Compose, Kotlin, coroutines and the Android Gradle tooling are resolved through Gradle. Their versions and repositories are declared in `gradle/libs.versions.toml`, the Gradle build scripts and the wrapper configuration; they retain their respective upstream licenses.

These notices describe third-party components and do not assign a license to BeyGram's original application code.
