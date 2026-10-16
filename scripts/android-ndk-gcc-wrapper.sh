#!/usr/bin/env bash
set -euo pipefail

case "$(basename "$0")" in
  aarch64-linux-android-gcc) target=aarch64-linux-android21 ;;
  arm-linux-androideabi-gcc) target=armv7a-linux-androideabi19 ;;
  x86_64-linux-android-gcc) target=x86_64-linux-android21 ;;
  i686-linux-android-gcc) target=i686-linux-android19 ;;
  *) echo "Unknown Android compiler wrapper: $0" >&2; exit 1 ;;
esac

exec "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" "--target=$target" "$@"
