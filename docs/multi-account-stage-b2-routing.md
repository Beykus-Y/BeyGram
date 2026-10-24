# Multi-account #185 — stage B2: account-scoped TDLib update routing

This PR adds a **safe routing primitive and per-account auth/connection state**.
It does not start another TDLib client, change the current singleton repository,
or expose a multi-account UI. It is meant for the later multi-client owner.

## Invariants

1. `TdAccountUpdateRouter.register(id, handler)` refuses IDs absent from the
   persistent `TdAccountRegistry` (stage A, #341); it also refuses a second
   listener for an account already registered in the same routing owner.
2. The callback attached to **each native TDLib client** captures the account
   ID and a unique generation at creation, not the mutable active account
   selected by the UI.
3. The router serializes delivery and the terminal close transition.
   `AuthorizationStateClosed` retires the registration in `finally` even when
   consumer processing fails. An old callback can never update a new
   generation or another account.
4. Use `TdAccountChatKey`, `TdAccountMessageKey` and `TdAccountFileKey`
   rather than bare TDLib numeric IDs when caching/navigation is made
   multi-account. Plain chat/message/file IDs may overlap.
5. `TdAccountSessionState` contains no personal data, authentication
   credentials, chat contents, or global singleton state. It rejects
   mismatched account IDs and generations and treats CLOSED as terminal.
6. This is **not** a substitute for `TdAccountClientLifecycle` (stage B1,
   #342): the TDLib database lease must also be released only on native
   `AuthorizationStateClosed`. The next client owner must wire both and
   maintain account-specific caches, notification policies, and VPN routing.

## Test coverage

JVM tests simulate interleaved TDLib updates from two accounts, switching
the active registry ID during delivery, identical numeric identifiers,
duplicate registration, unknown IDs, a late callback from a closed client,
and a re-opened generation. Auth and connectivity are kept in distinct
`StateFlow` instances; CLOSED never revives from delayed updates.

Run `./gradlew :app:testDevDebugUnitTest`; the ordinary PR Android CI builds
both APK variants and the instrumented-test APK. No native clients are created
or Telegram credentials exercised in these unit tests.

## Remaining #185 implementation

- Real per-account TDLib repository/client owners and isolated chat/media/draft
  caches; no shared global objects for sessions.
- Explicit network policy: **one Mihomo listener**; never silently let a
  secondary session bypass the user's proxy.
- Sign-in and account switching UI, with consistent notifications/shortcuts and
  correctly scoped logout/inline replies.
- Two-account real-device test matrix and RAM/CPU/battery measurements.

Keep the issue open until those user-facing stages are implemented and tested.
