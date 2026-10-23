# Multi-account #185: Stage A — persistent registry and safe legacy storage

**Status:** preparatory backend, not user-facing multi-account. One running TDLib
client, the current session and the existing UI remain unchanged. This PR must
not be described as "you can add and switch Telegram accounts" yet.

## File layout

| Account | Database directory | TDLib files |
|---|---|---|
| Original install (`legacy`) | `filesDir/tdlib/database` | `filesDir/tdlib/files` |
| Future `a-<random UUID>` | `filesDir/tdlib-accounts/<id>/database` | `filesDir/tdlib-accounts/<id>/files` |

The old login **stays in place**: zero-copy migration avoids renaming a live
SQLite database, WAL, or TDLib file cache, and avoids free-space and interrupted
move failure modes. First boot writes only `filesDir/tdlib-accounts/.registry-v1`,
containing a format version, opaque IDs and the selected ID. No Telegram user IDs,
phone numbers, passwords or tokens go into registry metadata.

### Persistence/recovery

- Registry creation, new IDs and active metadata selection share one in-process
  lock and use same-directory pending write → file descriptor sync → atomic rename.
- An orphan `.registry-v1.pending` is ignored (may be from process kill).
  **Never** use it to overwrite committed state.
- On malformed committed metadata or unsafe symlink, initialization reports an
  error rather than resetting, replacing or removing existing account storage.
- Startup has fail-safe behavior: a registry error is logged, but the pre-existing
  single TDLib repository still opens its original legacy paths.
- A secondary account ID is a *reservation*, **not** an authorization or a live
  session. No secondary database is created by the registry alone.
- The existing `TdAccountDatabaseLeases` contract must wrap future live client
  lifecycles; this PR does not create extra clients.

### Backup & rollback

The registry is under `filesDir` alongside the future secondary TDLib account
directories. Both must stay consistent in Android backup/device transfers.
Never manually restore a registry without its matching account databases.

To roll back **before the multi-client stages are implemented**, do NOT
uninstall the app or clear its data. Previous versions ignore registry metadata
and keep reading `filesDir/tdlib`, but Android rejects a lower `versionCode`
unless the package supports a downgrade and the signatures match.

- For **DEV debug** with an APK signed by the *same* debug signing certificate,
  use `adb install -r -d BeyGram-dev-debug.apk`: `-r` retains data and `-d`
  requests a version-code downgrade. If the prior DEV APK has another CI debug
  signing key, Android rejects the replacement; **do not uninstall** to bypass
  the signature mismatch.
- For **Stable/release**, Android commonly restricts `-d` to debuggable
  packages. Use a rollback/hotfix build with a **higher versionCode**, signed
  with the same release key, while reverting the incompatible code. Never
  recommend data-destructive uninstall as a rollback procedure.
- Before any risky migration, keep a user-authorized, tested app-data backup.
  Preserve the entire TDLib root plus matching registry and secondary paths.
  Avoid deleting `tdlib-accounts`: future multi-client versions may store
  account databases there.

## Automated checks

- `./gradlew :app:testDevDebugUnitTest`: preserve original DB/file bytes;
  idempotent first boot; account-specific paths; durable active selection;
  rejection of unknown/duplicate/path-traversing IDs; refused corrupt registry;
  interrupted pending write; symlink escape; concurrent registration without
  lost updates; original storage leases.
- `./gradlew :app:assembleDevDebugAndroidTest` compiles on-device tests;
  `TdAccountLegacyMigrationTest` asserts the default live config still targets
  the old paths. CI does **not** run on-device tests.

## Manual device check

1. On the current Stable/DEV installation, confirm Telegram authorization,
   chats and local media without clearing app data.
2. Update to a build containing Stage A, restart app/force-stop/relaunch.
   Confirm **no Telegram relogin**, same chats/media, and no extra account UI.
3. Repeat with low storage and a forced stop; do not intentionally corrupt TDLib.
   When modifying registry metadata in a controlled test fixture, verify malformed
   metadata does not erase TDLib files and is surfaced as an error.
4. Check the existing Mihomo localhost proxy still works; no second TDLib
   connection/port has been introduced.
5. Test separate DEV and Stable app packages; they have distinct app private dirs.

## Next stages, intentionally NOT implemented

- **B:** per-account client owners and immutable account identity on events,
  storage caches, streams, and repositories; database leases until client fully
  closes. Correctly manage TDLib authorization state and logout.
- **C:** account add/login/switch, navigation and scoped drafts/settings.
- **D:** notifications, inline reply, external/share intents routed to correct ID.
- **E:** one process-level Mihomo listener, resource budgeting and recovery.

Track all remaining acceptance criteria in #185; do not close the issue after
merging Stage A.
