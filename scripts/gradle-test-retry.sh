#!/usr/bin/env bash
# Runs one Gradle unit-test invocation and runs it again only when the failure is a transient Maven download failure.
#
# Two kinds of transient failure are retried:
# - Robolectric fetches the android-all jars from Maven on first use. A network error there fails each test that
#   starts the download, with an IOException under MavenArtifactFetcher. The jars that did arrive stay in ~/.m2, so a
#   rerun normally gets past them.
# - Gradle downloads its own dependencies (such as org.robolectric:nativeruntime-dist-compat) while it configures and
#   resolves the build, before any test runs. Maven Central answers HTTP 429 (Too Many Requests) under load, and the
#   build then fails without a test result. A rerun after a pause normally gets the artifact.
#
# Any other failure fails the script at once. A failed run whose output shows no such transient error is not retried.
#
# Usage: scripts/gradle-test-retry.sh <test results dir> <gradle arguments...>
# Example: scripts/gradle-test-retry.sh app/build/test-results/testDevDebugUnitTest :app:testDevDebugUnitTest
set -uo pipefail

results=${1:?usage: gradle-test-retry.sh <test results dir> <gradle arguments...>}
shift
attempts=${GRADLE_TEST_ATTEMPTS:-3}
log=$(mktemp)
trap 'rm -f "$log"' EXIT

# Exits 0 only when the results contain at least one failure and all of them are Robolectric download failures.
# A run that failed before any test ran (no results) is not retried by this check.
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

# Exits 0 when Gradle reported that a Maven repository answered HTTP 429 for a dependency it was downloading.
maven_rate_limited() {
  grep -Eq "Could not GET '[^']*(maven|robolectric)[^']*'\. Received status code 429" "$1"
}

for attempt in $(seq 1 "$attempts"); do
  # tee keeps Gradle's output in the job log and in $log; pipefail keeps Gradle's exit status.
  if ./gradlew --no-daemon "$@" 2>&1 | tee "$log"; then
    exit 0
  fi
  if [ "$attempt" -lt "$attempts" ] && { only_download_failures "$results" || maven_rate_limited "$log"; }; then
    # The failures came back within seconds of each other, so wait before the next try.
    wait_seconds=$((attempt * ${GRADLE_TEST_BACKOFF:-60}))
    echo "::warning::A Maven download failed with a transient error (attempt $attempt of $attempts). Waiting ${wait_seconds}s, then running the same Gradle command again."
    sleep "$wait_seconds"
    continue
  fi
  exit 1
done
