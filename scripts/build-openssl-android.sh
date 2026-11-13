#!/usr/bin/env bash
# Builds static OpenSSL for one Android ABI, used by the optional BG Wave WebRTC adapter (libsrtp needs it).
# Usage: build-openssl-android.sh <abi> <ndk_root> <install_prefix>
# Idempotent: exits at once when the prefix already holds libssl.a and libcrypto.a, so CI can cache it.
set -euo pipefail

readonly OPENSSL_VERSION="3.4.0"
readonly OPENSSL_SHA256="e15dda82fe2fe8139dc2ac21a36d4ca01d5313c75f99f46c4e8a27709b7294bf"
readonly ANDROID_API="29"

abi="${1:?abi}"
ndk="${2:?ndk root}"
prefix="${3:?install prefix}"

case "$abi" in
    arm64-v8a) target="android-arm64" ;;
    armeabi-v7a) target="android-arm" ;;
    x86_64) target="android-x86_64" ;;
    *) echo "unsupported Android ABI: $abi" >&2; exit 2 ;;
esac

if [ -f "$prefix/lib/libssl.a" ] && [ -f "$prefix/lib/libcrypto.a" ]; then
    exit 0
fi

toolchain_bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [ ! -d "$toolchain_bin" ]; then
    echo "NDK toolchain not found at $toolchain_bin" >&2
    exit 3
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

archive="$work/openssl-$OPENSSL_VERSION.tar.gz"
curl -fsSL --retry 3 -o "$archive" \
    "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz"
echo "$OPENSSL_SHA256  $archive" | sha256sum -c -
tar -xzf "$archive" -C "$work"

src="$work/openssl-$OPENSSL_VERSION"
mkdir -p "$prefix"
cd "$src"
PATH="$toolchain_bin:$PATH" ./Configure "$target" \
    -D__ANDROID_API__="$ANDROID_API" \
    no-shared no-tests no-apps no-docs \
    --prefix="$prefix" --openssldir="$prefix/ssl"
PATH="$toolchain_bin:$PATH" make -j"$(nproc)" build_libs
PATH="$toolchain_bin:$PATH" make install_dev
