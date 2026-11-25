#!/usr/bin/env bash
# Fills a Robolectric dependency directory with the android-all jars the unit tests need, before the tests run.
#
# The tests then read the jars from that directory (robolectric.dependency.dir, set through BEYGRAM_ROBOLECTRIC_DIR
# in app/build.gradle.kts), so no test asks Maven for an SDK jar. Every jar is checked against its SHA-1 from
# scripts/robolectric-sdks.txt. A failed download is retried with backoff, honoring Retry-After on HTTP 429; a jar
# that still fails, or fails its checksum, stops the step, so an incomplete directory is never used or cached.
#
# Usage: scripts/robolectric-prefetch.sh <dependency dir>
# Run from the repository root. Environment overrides for tests: ROBOLECTRIC_MAVEN_REPO, ROBOLECTRIC_FETCH_ATTEMPTS,
# ROBOLECTRIC_FETCH_BASE_DELAY (seconds).
set -euo pipefail

dir=${1:?usage: robolectric-prefetch.sh <dependency dir>}
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
manifest="$here/robolectric-sdks.txt"
repo=${ROBOLECTRIC_MAVEN_REPO:-https://repo1.maven.org/maven2}
attempts=${ROBOLECTRIC_FETCH_ATTEMPTS:-6}
base_delay=${ROBOLECTRIC_FETCH_BASE_DELAY:-20}
max_delay=300

# The manifest is for one Robolectric version. A mismatch means the jars do not match the tests, so stop here.
manifest_version=$(sed -n 's/^# robolectric //p' "$manifest" | head -n 1)
catalog_version=$(sed -n 's/^robolectric *= *"\([^"]*\)".*/\1/p' "$root/gradle/libs.versions.toml" | head -n 1)
if [ "$manifest_version" != "$catalog_version" ]; then
  echo "::error::robolectric-sdks.txt is for Robolectric '$manifest_version', but the catalog has '$catalog_version'. Update the manifest." >&2
  exit 1
fi

verify() { # file sha1
  printf '%s  %s\n' "$2" "$1" | sha1sum -c --status
}

fetch() { # url sha1 destination
  local url=$1 sha=$2 dest=$3 try=1 status headers retry_after delay
  headers=$(mktemp)
  trap 'rm -f "$headers"' RETURN
  while :; do
    status=$(curl -sS --connect-timeout 30 --max-time 900 -L -D "$headers" -o "$dest.part" -w '%{http_code}' "$url") || status=000
    if [ "$status" = 200 ] && verify "$dest.part" "$sha"; then
      mv -f "$dest.part" "$dest"
      return 0
    fi
    rm -f "$dest.part"
    if [ "$status" = 200 ]; then
      reason="checksum mismatch"
    else
      reason="HTTP $status"
    fi
    if [ "$try" -ge "$attempts" ]; then
      echo "::error::$(basename "$dest"): $reason after $attempts attempts. Stopping; no partial directory is used or saved." >&2
      return 1
    fi
    # Backoff doubles per attempt. A Retry-After from the server is honored when it asks for longer.
    delay=$(( base_delay * (1 << (try - 1)) ))
    retry_after=$(tr -d '\r' < "$headers" | sed -n 's/^[Rr]etry-[Aa]fter: *\([0-9][0-9]*\).*/\1/p' | tail -n 1)
    if [ -n "$retry_after" ] && [ "$retry_after" -gt "$delay" ]; then delay=$retry_after; fi
    [ "$delay" -gt "$max_delay" ] && delay=$max_delay
    delay=$(( delay + RANDOM % 10 ))
    echo "::warning::$(basename "$dest"): $reason (try $try of $attempts). Waiting ${delay}s, then trying again." >&2
    sleep "$delay"
    try=$(( try + 1 ))
  done
}

mkdir -p "$dir"
count=0
while read -r api file sha _; do
  case "$api" in ''|'#'*) continue ;; esac
  target="$dir/$file"
  if [ -f "$target" ] && verify "$target" "$sha"; then
    echo "present  $file"
  else
    rm -f "$target"
    version=${file#android-all-instrumented-}
    version=${version%.jar}
    fetch "$repo/org/robolectric/android-all-instrumented/$version/$file" "$sha" "$target"
    echo "fetched  $file"
  fi
  count=$(( count + 1 ))
done < "$manifest"
echo "Robolectric dependency directory ready: $count jars in $dir"
