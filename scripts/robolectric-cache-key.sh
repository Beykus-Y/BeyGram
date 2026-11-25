#!/usr/bin/env bash
# Prints the cache key for Robolectric's android-all jars (~/.m2/repository/org/robolectric).
#
# The key depends only on the Robolectric and Roborazzi versions, which decide which jars are fetched. An unrelated
# change to gradle/libs.versions.toml keeps the key, so the jars stay cached. Bump the schema version (v3) to drop
# every cache written under the old layout.
#
# Usage: scripts/robolectric-cache-key.sh [path to libs.versions.toml]
set -euo pipefail

toml=${1:-gradle/libs.versions.toml}

version_of() {
  sed -n "s/^$1 *= *\"\([^\"]*\)\".*/\1/p" "$toml" | head -n 1
}

robolectric=$(version_of robolectric)
roborazzi=$(version_of roborazzi)
if [ -z "$robolectric" ] || [ -z "$roborazzi" ]; then
  echo "::error::robolectric or roborazzi version is missing from $toml" >&2
  exit 1
fi

# The jar list is part of the key too, so a change to robolectric-sdks.txt starts a fresh cache.
here=$(cd "$(dirname "$0")" && pwd)
sdks=$(sha256sum "$here/robolectric-sdks.txt" | cut -c1-12)
os=${RUNNER_OS:-$(uname -s)}
echo "beygram-robolectric-v3-${os}-robolectric-${robolectric}-roborazzi-${roborazzi}-sdks-${sdks}"
