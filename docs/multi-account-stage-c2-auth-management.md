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
- The controller compares the *current* effective route with the route
  fixed at secondary-client startup. This applies to **all** secondary clients:
  FOLLOW_PRIMARY originally DIRECT is taken offline if the primary switches to
  Mihomo, just as a shared-SOCKS client is taken offline if the listener is lost.
  TDLib `NetworkTypeNone` is sent on mismatch; resumption requires the
  original route to become valid again. An unsuccessful pause triggers native
  `Close`, **never** `LogOut`. The primary client's settings are untouched.
- The route is rechecked on listener status transitions, upon entering the
  account screen, and every 15 seconds *only while a secondary is active*
  because the legacy repository does not expose a primary-route StateFlow.
  An instantaneous change can precede the next poll; production chat support
  needs synchronous routing policy changes at their source.
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
- This beta limits RAM use to **one running secondary client** alongside
  the legacy TDLib client; additional account IDs may be reserved but cannot
  run concurrently until resource profiling.
- Secondary sessions are **not automatically resumed on a process restart**.
  The registry retains their storage slots and users can reconnect explicitly.
- Primary route transitions not originating from the Mihomo listener are
  detected by the bounded polling interval, not instantaneously. A future
  integrated route-owner must dispatch synchronous changes to all TDLib
  sessions before switching the primary path.
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
