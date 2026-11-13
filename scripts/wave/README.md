# Canonical BG Wave interop check

Run `bash scripts/test_wave_codec_interop.sh` on Linux with a complete JDK 21,
GNU C++20 compiler, initialized `third_party/bg-media`, and the pinned Gradle wrapper
already provisioned (`JAVA_HOME` and `GRADLE_USER_HOME` select those tools).

The check compiles the **production JNI source** with the pinned BG-Media
`WaveSignaling.cpp`, then calls the production Kotlin `BgWaveNative` wrapper.
Fixtures are read directly from the pinned submodule: eight URI wrappers are decoded,
five malformed binary vectors rejected, and Cancel/End encoded through JNI must
match both canonical URI/byte fixtures exactly. No Kotlin wire serializer or second
set of protocol constants is maintained here. Temporary host build outputs are removed.

This is an E1 codec interoperability check, not E0 live peer-connection validation.
The shared v1 starting format remains subject to real SDP/ICE sizing and Telegram
text-URL round-trip checks before freezing the protocol. The test does not load TDLib,
connect peers, send Telegram messages, or validate Android window/device behavior.
