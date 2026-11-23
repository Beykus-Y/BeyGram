# Android PROF channel

PROF is an independent Android installation, not an update of DEV or Stable.

- Package: `com.beykus.beygram.prof` and label **BeyGram PROF**.
- Workflow: `.github/workflows/android-prof-channel.yml` on pushes to the `PROF` branch.
- Build: `:app:assembleProfRelease -PprofBuildNumber=<run-number>`.
- Release: GitHub prerelease `prof-<run-number>` with `BeyGram-prof-beta.apk` and `manifest.json`.
- Signing: same verified release certificate as DEV / Stable, but a different package ID.
- In-app updater: selects only `prof-N`; checks package ID, versionCode, SHA-256, size and pinned signing certificate.
- Profiling: `<profileable android:shell="true" />` only in the PROF flavor.
- VPN listener port 17893 (DEV 17892; Stable 17891).
- PROF has a distinct TDLib database; sign in independently.
- Never uninstall or clear data of existing DEV / Stable applications for PROF tests.
- GitHub release is a prerelease with `--latest=false` and never replaces Stable `latest`.
- Validate successful CI and a PROF-to-PROF upgrade before claiming auto-update is functional.
