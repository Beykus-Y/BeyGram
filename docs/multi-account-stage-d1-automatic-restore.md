# Issue #185 — D1: automatic secondary session recovery

## Runtime contract

- The process-owned `TdSecondaryRestoreStore` records only a non-sensitive,
  installation-private opaque account ID, the user-chosen enum route preference,
  and proof that TDLib reached `AuthorizationStateReady`. No Telegram
  credentials, OTP, passwords, SOCKS secrets, chat contents or phone numbers.
- A reserved but never authorized account is NEVER auto-started. A corrupted
  or unknown route value fails closed.
- After a process restart, eligible sessions can reconnect using their
  existing TDLib databases without re-entering credentials. Every attempt
  passes the original `TdSecondaryAccountRoutePreflight`: same Mihomo
  listener, proof of ownership, original explicit user DIRECT policy.
- A failed preflight preserves the database and authorized state. The
  process can retry at a Mihomo runtime transition or after legacy auth READY,
  rather than falling back to DIRECT.
- Explicit user **Stop** stores a durable stop flag *before* sending
  asynchronous TDLib `Close`, preventing listener events from reopening
  the account behind the user's back. An explicit subsequent login with
  native READY clears the stop flag.
- Never open the legacy database from a secondary factory; source ID and
  native-close/generation guards remain unchanged.
- A bounded maximum of two additional running TDLib clients (three accounts
  total) prevents unbounded CPU, network and RAM use when the registry has
  many reserved IDs.
- Process initialization for background sessions happens only if the user
  enabled background notifications; the UI can also initialize the manager
  when opened normally. This PR does NOT invent secondary OS notifications.

## Test coverage

Pure JVM tests cover confirmation gating and reject malformed/unknown saved
routes. Coordinator tests check two concurrent secondary owners but reject
a third; native READY triggers the persistence callback, while an explicit
Stop records the user's intent. Other TDLib lifecycle isolation tests remain
in force.

## Remaining acceptance criteria for full #185

- Independent Android notification renderer, IDs, deep links, reply/action
  receivers and background service ownership for secondary TDLib clients.
- Per-account logout and safe database removal after native CLOSED.
- Full secondary Chat/Media/Contacts/Calls/Profile/Folder parity.
- Email/QR authentication, user-visible session identity/avatars, process
  death recovery on both emulator and actual low-RAM devices.
- Resource measurements and real two/three-account validation.

Keep #185 open; do not claim production-complete multi-accounting from this
backend recovery stage alone.
