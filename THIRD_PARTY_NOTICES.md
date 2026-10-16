# Third-party components

## TDLib

`app/libs/tdlib-java.jar` and `app/src/main/jniLibs/arm64-v8a/libtdjni.so` are generated/built from [TDLib](https://github.com/tdlib/td), the Telegram Database Library by Telegram Messenger and contributors. TDLib is provided under the Boost Software License 1.0; see [LICENSES/TDLib-Boost-1.0.txt](LICENSES/TDLib-Boost-1.0.txt).

The generated Java bindings and the native library must be updated together. The local source checkout and intermediate native builds are excluded from Git.

## OpenSSL

The native build scripts use [OpenSSL 3.3.0](https://github.com/openssl/openssl/tree/openssl-3.3.0), licensed under Apache License 2.0. See [LICENSES/OpenSSL-Apache-2.0.txt](LICENSES/OpenSSL-Apache-2.0.txt).

## Other build/runtime dependencies

AndroidX/Jetpack Compose, Kotlin, coroutines and the Android Gradle tooling are resolved through Gradle. Their versions and repositories are declared in `gradle/libs.versions.toml`, the Gradle build scripts and the wrapper configuration; they retain their respective upstream licenses.

These notices describe third-party components and do not assign a license to BeyGram's original application code.
