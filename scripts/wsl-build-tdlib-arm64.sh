#!/usr/bin/env bash
set -euo pipefail

cd /mnt/f/BeyGram/td-master/example/android
cp build-tdlib.sh build-tdlib-arm64.sh
sed -i 's/for ABI in arm64-v8a armeabi-v7a x86_64 x86 ; do/for ABI in arm64-v8a ; do/' build-tdlib-arm64.sh
./build-tdlib-arm64.sh /opt/android-sdk 27.3.13750724 third-party/openssl
