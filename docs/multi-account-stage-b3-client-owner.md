# #185: Stage B3 — account-bound native TDLib client owner

This is a **backend composition boundary**, not a second account login UI.

`TdAccountNativeClientOwner.create(id, callback, factory)` combines:
- Stage A (#341): a registered, persistent opaque account ID and a unique TDLib
  database and media directory; old legacy database stays in place.
- Stage B1 (#342): one canonical database lease during the entire native TDLib
  lifetime, automatically released at `AuthorizationStateClosed`, not logout.
- Stage B2 (#343): immutable source account ID, generation-guarded TDLib
  callbacks, no delivery to another account or a replacement client.

The supplied factory takes exactly the config paths for the registered ID and
the scoped callback. If it throws, the lease and route are rolled back. If
authorization closes, they retire even when the consumer throws. A configuration
which aliases another account's directories is refused before native creation.

**Important networking invariant:** This owner does **not** invoke
`Client.create` or TDLib network startup on its own. Its future caller must
first establish the process-owned Mihomo listener and configure each client to
use the chosen routing policy. Secondary sessions must not silently bypass the
user's existing proxy; that work remains a separate part of #185. Do not wire
this to the add-account UI until that policy and shutdown ownership are ready.

## Tests
- Two simultaneous fake client owners: distinct database/files paths and
  account-tagged callbacks even when active UI account changes.
- Same account cannot be opened twice, including while logging out; an old
  terminal callback cannot release the replacement lease.
- Native client factory or consumer failure does not leak the account database.
- Unknown/unregistered account or forged alias of a different account's
  database is rejected before the native factory can run.

Run `./gradlew :app:testDevDebugUnitTest`; CI also compiles DEV, Prod,
and instrumentation APKs. These unit tests use fake client factories and
**never connect to Telegram or modify the phone's existing TDLib database**.

Actual TDLib multi-session launch, auth, UI, notifications, app-intent routing,
Mihomo integration and on-device two-account tests are not implemented here.
Keep #185 open.
