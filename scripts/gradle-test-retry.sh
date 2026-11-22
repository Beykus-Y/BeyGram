#!/usr/bin/env bash
# Runs one Gradle unit-test invocation and runs it again only when every failure is a Robolectric download failure.
#
# Robolectric fetches the android-all jars from Maven on first use. A network error there fails each test that
# starts the download, with an IOException under MavenArtifactFetcher. The jars that did arrive stay in ~/.m2, so a
# rerun normally gets past them. Any failure that is not such a download failure fails the script at once.
#
# Usage: scripts/gradle-test-retry.sh <test results dir> <gradle arguments...>
# Example: scripts/gradle-test-retry.sh app/build/test-results/testDevDebugUnitTest :app:testDevDebugUnitTest
set -uo pipefail

results=${1:?usage: gradle-test-retry.sh <test results dir> <gradle arguments...>}
shift
attempts=${GRADLE_TEST_ATTEMPTS:-3}

# Exits 0 only when the results contain at least one failure and all of them are Robolectric download failures.
# A run that failed before any test ran (no results) is not retried.
only_download_failures() {
  python3 - "$1" <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

failed = 0
download = 0
for path in glob.glob(sys.argv[1] + "/TEST-*.xml"):
    for case in ET.parse(path).getroot().iter("testcase"):
        for child in case:
            if child.tag not in ("failure", "error"):
                continue
            failed += 1
            text = (child.get("message") or "") + (child.text or "")
            if "MavenArtifactFetcher" in text:
                download += 1
sys.exit(0 if failed and failed == download else 1)
PY
}

for attempt in $(seq 1 "$attempts"); do
  if ./gradlew --no-daemon "$@"; then
    exit 0
  fi
  if [ "$attempt" -lt "$attempts" ] && only_download_failures "$results"; then
    echo "::warning::Robolectric could not download android-all for some tests (attempt $attempt of $attempts). Running the same tests again."
    continue
  fi
  exit 1
done
