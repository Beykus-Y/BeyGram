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
# Each round tries every mirror in order; a round that gets nothing waits, then the next round starts. ROBOLECTRIC_MAVEN_REPO
# replaces the list with one repository (tests). Every mirror serves the same Maven Central files, and each jar is checked
# against its SHA-1 whichever mirror served it, so a mirror cannot substitute different content.
if [ -n "${ROBOLECTRIC_MAVEN_REPO:-}" ]; then
  mirrors=("$ROBOLECTRIC_MAVEN_REPO")
else
  mirrors=(
    "https://repo1.maven.org/maven2"
    "https://maven-central.storage-download.googleapis.com/maven2"
    "https://repo.maven.apache.org/maven2"
  )
fi
attempts=${ROBOLECTRIC_FETCH_ATTEMPTS:-4}
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

fetch() { # path under the repository, sha1, destination
  local rel=$1 sha=$2 dest=$3 round=1 mirror status headers retry_after delay reason
  headers=$(mktemp)
  trap 'rm -f "$headers"' RETURN
  while :; do
    reason=""
    for mirror in "${mirrors[@]}"; do
      status=$(curl -sS --proto '=https' --proto-redir '=https' --connect-timeout 30 --max-time 600 -L -D "$headers" -o "$dest.part" -w '%{http_code}' "$mirror/$rel") || status=000
      if [ "$status" = 200 ]; then
        # A checksum mismatch is not retried or failed over: the content is wrong, so the step stops at once.
        if verify "$dest.part" "$sha"; then
          mv -f "$dest.part" "$dest"
          echo "from     $mirror" >&2
          return 0
        fi
        rm -f "$dest.part"
        echo "::error::$(basename "$dest"): checksum mismatch from $mirror. Stopping; no partial directory is used or saved." >&2
        return 1
      fi
      rm -f "$dest.part"
      reason="HTTP $status from $mirror"
    done
    if [ "$round" -ge "$attempts" ]; then
      echo "::error::$(basename "$dest"): no mirror served it after $attempts rounds (last: $reason). Stopping; no partial directory is used or saved." >&2
      return 1
    fi
    # Backoff doubles per round. A Retry-After from the last answer is honored when it asks for longer.
    delay=$(( base_delay * (1 << (round - 1)) ))
    retry_after=$(tr -d '\r' < "$headers" | sed -n 's/^[Rr]etry-[Aa]fter: *\([0-9][0-9]*\).*/\1/p' | tail -n 1)
    if [ -n "$retry_after" ] && [ "$retry_after" -gt "$delay" ]; then delay=$retry_after; fi
    [ "$delay" -gt "$max_delay" ] && delay=$max_delay
    delay=$(( delay + RANDOM % 10 ))
    echo "::warning::$(basename "$dest"): $reason, and no other mirror served it (round $round of $attempts). Waiting ${delay}s, then trying every mirror again." >&2
    sleep "$delay"
    round=$(( round + 1 ))
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
    fetch "org/robolectric/android-all-instrumented/$version/$file" "$sha" "$target"
    echo "fetched  $file"
  fi
  count=$(( count + 1 ))
done < "$manifest"
echo "Robolectric dependency directory ready: $count jars in $dir"
