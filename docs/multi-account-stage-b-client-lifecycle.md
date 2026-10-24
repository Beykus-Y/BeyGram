# Multi-account #185 — Stage B1: TDLib database ownership

This PR is a **runtime safety integration**, not a multi-account UI, and does
not start a second TDLib client automatically.

## Runtime invariant

- The current, single `TdLibTelegramRepository` creates the **legacy** client
  through `TdAccountClientLifecycle.open("legacy")`. Its existing database and
  media paths are unchanged.
- `TdAccountDatabaseLeases` keeps a process-wide canonical database path
  locked for the **whole native TDLib client lifetime**, across different
  instances of the lifecycle factory.
- On TDLib `UpdateAuthorizationState(AuthorizationStateClosed)`, the
  lease callback runs in a `finally` block, so an update-dispatch failure
  cannot strand it. It is idempotent and a repeated late close cannot unlock
  a replacement client. Sending a logout/Close request does NOT release it.
- If `Client.create` throws, the factory releases the lease so a later
  start can retry; no cleanup, moving or deletion of TDLib databases occurs.
- A second account can eventually use its distinct storage path, but must be
  registered through the account registry first (Stage A). This first B PR
  introduces a reusable lifecycle primitive and protects the existing client.
  **It does not yet implement secondary account auth/streams/notifications.**

## How to verify

Run JVM regression suite with `./gradlew :app:testDevDebugUnitTest`.
The new tests assert: unchanged legacy paths and database bytes; mutually
exclusive access even across separate lifecycle owner instances; no release
on UI cancellation/logout; duplicate callbacks cannot free another owner's
lease; failure recovery; and independent paths for different account IDs.

CI also assembles the application and Android instrumentation test APK, but
does not log in two real Telegram accounts. Real dual-client tests must be
implemented before Stage C (login + switcher).

## Remaining #185 steps

1. Build a true per-account TDLib repository/session manager that accepts
   the registered account ID and scopes **every** chat state, cache, drafts,
   events and media operation. Never route secondary session updates through
   the singleton legacy repository.
2. Scope notifications and deep links by account ID and isolate logout.
3. Apply one process-level Mihomo listener policy across TDLib sessions.
4. Only then expose add-account/switch UI and test two authorized accounts.
