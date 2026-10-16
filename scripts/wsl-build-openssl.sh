#!/usr/bin/env bash
set -euo pipefail

cd /mnt/f/BeyGram/td-master/example/android
./build-openssl.sh /opt/android-sdk 27.3.13750724 third-party/openssl openssl-3.3.0
