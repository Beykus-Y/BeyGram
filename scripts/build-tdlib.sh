#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT=${1:?Pass the Android SDK path as the first argument.}
NDK_VERSION=${2:-30.0.15729638}
OPENSSL_VERSION=${TDLIB_OPENSSL_VERSION:-openssl-3.3.0}
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd -P)
TD_ANDROID_DIR="$PROJECT_ROOT/td-master/example/android"
TOOLS_DIR="$PROJECT_ROOT/.tools"

: "${TDLIB_GPERF_DIR:?Set TDLIB_GPERF_DIR to the directory containing gperf.exe.}"
: "${TDLIB_PHP_DIR:?Set TDLIB_PHP_DIR to the directory containing php.exe.}"
: "${TDLIB_PERL_LIB:?Set TDLIB_PERL_LIB to the Strawberry Perl library directory.}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK installation.}"

JAVA_HOME_UNIX=$(cygpath -u "$JAVA_HOME")
WRAPPERS_DIR="$TOOLS_DIR/android-gcc-wrappers"
mkdir -p "$WRAPPERS_DIR"
NDK_ROOT_UNIX="$SDK_ROOT/ndk/$NDK_VERSION"
for target in aarch64-linux-android arm-linux-androideabi x86_64-linux-android i686-linux-android; do
  rm -f "$WRAPPERS_DIR/$target-gcc"
  cp "$NDK_ROOT_UNIX/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" "$WRAPPERS_DIR/$target-gcc.exe"
done

export PATH="$WRAPPERS_DIR:$TOOLS_DIR/make/bin:$JAVA_HOME_UNIX/bin:$(cygpath -u "$TDLIB_GPERF_DIR"):$(cygpath -u "$TDLIB_PHP_DIR"):$PATH"
export PERL5LIB="$(cygpath -u "$TDLIB_PERL_LIB")${PERL5LIB:+:$PERL5LIB}"

cd "$TD_ANDROID_DIR"
source ./check-environment.sh

if [ ! -d third-party/openssl ]; then
  ./build-openssl.sh "$SDK_ROOT" "$NDK_VERSION" third-party/openssl "$OPENSSL_VERSION"
fi

./build-tdlib.sh "$SDK_ROOT" "$NDK_VERSION" third-party/openssl
