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

ndk_path="${ndk//\\//}"
prefix_path="${prefix//\\//}"
if command -v cygpath >/dev/null 2>&1; then
    ndk_path="$(cygpath -u "$ndk_path")"
    prefix_path="$(cygpath -u "$prefix_path")"
fi

if [ -f "$prefix_path/lib/libssl.a" ] && [ -f "$prefix_path/lib/libcrypto.a" ]; then
    exit 0
fi

export ANDROID_NDK_ROOT="$ndk_path"  # OpenSSL 15-android.conf refuses to configure without it
host_os="$(uname -s)"
host_arch="$(uname -m)"
case "$host_os:$host_arch" in
    Linux:*) host_tag="linux-x86_64" ;;
    Darwin:arm64) host_tag="darwin-arm64" ;;
    Darwin:*) host_tag="darwin-x86_64" ;;
    MINGW*:*|MSYS*:*|CYGWIN*:*) host_tag="windows-x86_64" ;;
    *) host_tag="linux-x86_64" ;;
esac
toolchain_bin="$ndk_path/toolchains/llvm/prebuilt/$host_tag/bin"
if [ ! -d "$toolchain_bin" ]; then
    # Fallback to whichever prebuilt host toolchain exists in this NDK installation.
    fallback_bin="$(find "$ndk_path/toolchains/llvm/prebuilt" -mindepth 2 -maxdepth 2 -type d -name bin 2>/dev/null | head -n 1 || true)"
    if [ -n "$fallback_bin" ] && [ -d "$fallback_bin" ]; then
        toolchain_bin="$fallback_bin"
    else
        echo "NDK toolchain not found at $toolchain_bin" >&2
        exit 3
    fi
fi

jobs="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

archive="$work/openssl-$OPENSSL_VERSION.tar.gz"
curl -fsSL --retry 3 -o "$archive" \
    "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz"
# macOS ships shasum rather than GNU sha256sum; never skip archive verification.
if command -v sha256sum >/dev/null 2>&1; then
    echo "$OPENSSL_SHA256  $archive" | sha256sum -c -
elif command -v shasum >/dev/null 2>&1; then
    echo "$OPENSSL_SHA256  $archive" | shasum -a 256 -c -
else
    echo "No SHA-256 verification tool found (sha256sum or shasum)" >&2
    exit 4
fi
tar -xzf "$archive" -C "$work"

src="$work/openssl-$OPENSSL_VERSION"
mkdir -p "$prefix_path"
cd "$src"
PATH="$toolchain_bin:$PATH" ./Configure "$target" \
    -D__ANDROID_API__="$ANDROID_API" \
    no-shared no-tests no-apps no-docs \
    --prefix="$prefix_path" --openssldir="$prefix_path/ssl"
PATH="$toolchain_bin:$PATH" make -j"$jobs" build_libs
PATH="$toolchain_bin:$PATH" make install_dev
