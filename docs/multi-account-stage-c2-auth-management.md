# Issue #185 — Stage C2: secondary account authorization management

This PR is intentionally **a limited beta of secondary login**, not a completed
multi-account Telegram client.

## What works

- Settings → Telegram accounts shows the legacy account and registered secondary
  storage slots, with an explicit beta notice.
- The user can reserve a secondary ID, choose *Follow primary*, *Mihomo* or
  *Explicit Direct*, connect a **real** isolated TDLib client, and complete
  supported phone/code/2FA-password authorization steps.
- `TdSecondaryAccountController` lives in the **application process**, not an
  Activity or Compose route. The underlying `TdSecondarySessionCoordinator`
  holds native client handles across navigation so an account does not lose
  its database owner when leaving Settings.
- The controller uses the verified process-level Mihomo listener. When its
  runtime changes, secondary clients using shared Mihomo are instructed to go
  offline with `NetworkTypeNone`; resume is allowed only after a new
  authenticated SOCKS ownership and current-primary-policy check. The existing
  legacy TDLib client still owns its original proxy policy and listener.
- Stop sends native `Close` (not `LogOut`), so stored Telegram credentials
  and the TDLib database are not erased; a replacement session cannot be
  started until native `AuthorizationStateClosed`.
- Account lists contain opaque local IDs and auth phases, **not phone numbers,
  passwords, codes or chat history**.

## Deliberate limitations

- The main Chats, Profile, Calls, saved messages, drafts, media and notifications
  are **still the legacy account**, even after a secondary account signs in.
  UI explicitly says switching those features is not implemented.
- The secondary auth flow currently supports phone/code/password. Other TDLib
  authorization states (email, QR, registration) show an unsupported-state
  message; no credentials are deleted by leaving the screen.
- Secondary sessions are **not automatically resumed on a process restart**.
  The registry retains their storage slots and users can reconnect explicitly.
- The runtime route guard monitors Mihomo service state changes. It does not
  yet monitor manual primary proxy changes continuously; returning to the
  account screen triggers a policy recheck. A full multi-client network-policy
  owner and logout-safe failure recovery remain prerequisites to making
  secondary chats generally available.
- Closing a secondary connection is not Telegram logout and does not remove
  registry slots or clear its cache. Deliberate per-account logout/deletion
  is separate work requiring a confirmation workflow.
- No actual Telegram credentials should be used by instrumentation on the
  user's existing installed app. DEV and Prod app data must never be cleared.

## Validation

`TdSecondarySessionCoordinatorTest` uses fake native handles to check
independent sessions, rejection of duplicate IDs and legacy ID reuse, no
premature reconnection before native CLOSED, metadata preservation on failure,
and SOCKS pause/resume only for shared-proxy sessions.

Build and run `./gradlew :app:testDevDebugUnitTest` and assemble both app
variants and androidTest APK. Unit tests do not talk to Telegram. Follow up
with a separate emulator with a distinct application ID to verify the
TDLib login handshake; do not use `connectedAndroidTest` against the user's
existing installed DEV/Prod packages.

## Remaining acceptance for #185

- True per-account chat/profile/media repository and selected-account navigation.
- Notifications/deep links/inline replies keyed by account ID.
- Full email/QR TDLib authentication, logout, session recovery after restart.
- Fully reactive network policy, independent unread counts and background
  resource controls, then physical-device acceptance for two real accounts.
