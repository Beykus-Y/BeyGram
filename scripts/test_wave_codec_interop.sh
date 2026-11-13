#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
: "${JAVA_HOME:?Select a complete JDK (21 or later)}"
# Use Kotlin shipped with the repository's pinned Gradle wrapper, without downloading a second compiler.
compiler_lib=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-9.4.1-bin" -type d -path '*/gradle-9.4.1/lib' -print -quit)
[[ -n "$compiler_lib" ]] || { echo 'Run ./gradlew --version first' >&2; exit 1; }
output=$(mktemp -d)
trap 'rm -rf "$output"' EXIT
core="$repo/third_party/bg-media"
g++ -std=c++20 -shared -fPIC -O2 -DHAVE_BGMEDIA=1 \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -I"$core/include" \
    "$repo/app/src/main/cpp/bgwave_jni.cpp" "$core/src/wave/WaveSignaling.cpp" \
    -o "$output/libbgbeat_jni.so"
"$JAVA_HOME/bin/java" -cp "$compiler_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -classpath "$compiler_lib/kotlin-stdlib-2.3.0.jar" \
    "$repo/app/src/main/java/com/beykus/beygram/media/bgwave/BgWaveNative.kt" \
    "$repo/scripts/wave/CodecInterop.kt" -d "$output/interop.jar"
"$JAVA_HOME/bin/java" -Djava.library.path="$output" \
    -cp "$output/interop.jar:$compiler_lib/kotlin-stdlib-2.3.0.jar" \
    com.beykus.beygram.media.bgwave.CodecInteropKt "$core/tests/fixtures/wave_v1"
