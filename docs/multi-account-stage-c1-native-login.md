# Issue #185 — Stage C1: real secondary TDLib client and login backend

This stage implements a *real*, opt-in secondary native TDLib client. It does
**not** enable multi-account UI, automatically start secondary clients at app
boot, or reconfigure the current legacy TDLib repository. There are no actual
devices or Telegram accounts exercised by CI.

## Explicit launch contract

1. The account must already exist in `TdAccountRegistry`
   (`prepareSecondaryAccount()`). The legacy ID is rejected: its client is
   permanently owned by the existing `TdLibTelegramRepository`.
2. A future UI/service calls `TdSecondaryNativeLoginLauncher.forApplication(
   context, primaryRoute = { legacyRepository.currentRoute().getOrNull() })`,
   then explicitly calls `openRegisteredSecondary(accountId, preference)`.
3. The route policy is checked **before** any native client is created. Unknown
   route, unconfigured manual proxy, stopped/foreign Mihomo listener all fail
   closed, unless the user explicitly selected direct mode.
4. The client is created with `TdAccountNativeClientOwner`, so it receives
   its own registered TDLib directories, process-wide database lease, and
   immutable account-scoped native callbacks.
5. On startup the transport issues `SetNetworkType(NetworkTypeNone)` and
   awaits TDLib's `AuthorizationStateWaitTdlibParameters`. Only then does it
   send `SetTdlibParameters` **while offline**.
6. For explicitly allowed DIRECT the transport disables any existing TDLib
   proxy; for the verified shared Mihomo route it installs/enables the precise
   local SOCKS entry using installation credentials. **No secondary listener**.
7. The live primary route + listener ownership are checked again before
   `SetNetworkType(NetworkTypeOther)` resumes networking. An error, changed
   route, or timeout causes `Close` (not `LogOut`) and never a direct fallback.
   The existing TDLib database is preserved and the native-close callback
   eventually releases the lease.
8. The caller can observe a per-session authorization `StateFlow` and submit
   phone number, confirmation code, resend request, or 2FA password. TDLib
   request errors propagate as exceptions without logging secrets.

## Limitations and prerequisites

- Only the phone/code/password auth paths are exposed. Telegram may demand
  newer steps such as email challenge, QR login or registration; state OTHER
  needs its own UI/commands before general availability.
- While open, each secondary session currently provides only authorization
  state. Chat/media/profile repositories, notifications, switching screen,
  drafts, account-scoped settings and multiple running sessions recovery remain
  separate work.
- The route is checked at startup, **not continuously** after the user stops or
  changes Mihomo. The next stage must subscribe to process listener transitions,
  put affected secondary sessions offline first, and reconfigure before resuming.
  Do not expose this as production UI until that protection is implemented.
- NetworkTypeOther is a conservative network re-enable; actual Android
  connectivity changes require the future session manager to apply the same
  network-monitor policy to each client.
- A committed registry entry is not a Telegram login; a failed login or
  `Close` never deletes the TDLib database. Do not use `LogOut` for switching.

## Automated tests

`TdSecondaryNativeTransportTest` uses a fake TDLib command sink. It asserts:
network OFF before parameters; DIRECT requires proxy removal; shared Mihomo
requires AddProxy+EnableProxy before network ON; recheck of listener ownership;
and that blocked routes, missing API credentials, TDLib proxy errors and
changing route cannot re-enable networking.

The existing owner/registry/routing tests additionally verify distinct
database paths and generation safety. Run
`./gradlew :app:testDevDebugUnitTest` and CI APK builds. No tests should be
run against the user's installed DEV/Prod application; use a disposable
emulator or a distinct test application ID for eventual native integration QA.
