# Unified Telegram account switcher (issue #357)

Issue #357 replaces the two experimental full-screen account flows
(`SecondaryAccountsScreen` and `AccountPickerScreen`) with one compact switcher.
The goal is one BeyGram UI with several independent Telegram accounts, not an
app-inside-an-app.

## Entry points

- Chats header: the explicit accounts button opens the switcher sheet.
- Settings → «Аккаунты Telegram» opens the same sheet.
- A secondary account's «Управление аккаунтами» action opens the same sheet.
- The recovery screen for a selected-but-unavailable account opens the same
  sheet. There is no second picker or mandatory intermediate management screen.

`AccountSwitcherSheet` is rendered once above every route in `BeyGramApp`.
`AppRoute.AccountPicker`, `SecondaryAccounts` and `SecondaryChats` remain only
as legacy enum values for saved-state compatibility; `appRouteFromStored` maps
them to Chats.

## One-tap selection

Selecting a stored account calls `TdSecondaryAccountController.ensureReady`:

1. A live READY, unpaused session is used directly.
2. A CLOSED or never-opened authorized session is reopened with its last
   explicit route (FOLLOW_PRIMARY by default) and bounded by a 60-second start /
   45-second readiness timeout.
3. The durable registry is changed only after the full repository exists.
4. A failure leaves the current account on screen and reports a retryable
   reason. Legacy chats are never shown under a secondary label.

Selecting an unfinished slot opens its sign-in form instead of pretending it is
a ready account. A not-yet-authorized account is never selected.

## Add account and sign-in

«Добавить аккаунт» reserves one opaque local storage slot, opens its native
TDLib client with FOLLOW_PRIMARY and shows the standard phone → code → cloud
password form in the same sheet. The user does not choose a technical route
before signing in. On native READY the account switches automatically.

- Secrets are cleared after a successful submit and never persisted.
- TDLib error codes are classified into phone/code/password/flood messages;
  unknown errors are not described as a wrong secret.
- Code resend, retry, connection settings and deletion of an unfinished sign-in
  remain available.
- Leaving the sheet keeps an unfinished slot; the list shows
  «Продолжить вход» and the menu offers «Удалить незавершённый вход».
- Deleting an unfinished sign-in closes the client without Logout, waits for
  the database lease to retire, unregisters the slot and removes only that
  account's files and preferences.

## Account states

The state is derived from native TDLib plus durable authorization metadata, not
from the selected row or a checkbox:

| State | Meaning | Primary action |
| --- | --- | --- |
| CURRENT / READY | Authorized and usable | tap row to switch |
| CONNECTING | Session is starting or attaching | wait, retry on timeout |
| OFFLINE | authorized, network currently unavailable | switch; offline UI |
| ROUTE_PAUSED | route policy no longer permits traffic | connection settings |
| AWAITING_PHONE/CODE/PASSWORD | sign-in in progress | continue sign-in |
| DISCONNECTED | signed in, session explicitly stopped | one tap reconnects |
| UNSIGNED | slot reserved, sign-in unfinished | continue or delete |
| UNSUPPORTED | TDLib asked for an unsupported method | delete/try another account |
| ERROR | last operation failed | retry and show the reason |
| LOGGING_OUT | confirmed logout in progress | wait |

Unread badges, display names and avatars come from each account's own full
repository. An unavailable account shows no fallback identity or file ID from
the primary account.

## Account actions

The overflow menu is the only place for destructive or advanced actions:

- «Настроить подключение»: FOLLOW_PRIMARY, SHARED_MIHOMO or EXPLICIT_DIRECT.
  A live session — READY or still waiting for a phone/code/password — is closed
  without Logout and reopened after the route preflight, which also recovers a
  route-policy pause during sign-in. A stopped or not-yet-open account only
  stores the preference; the next explicit reconnect or sign-in uses it, so
  changing a route never clears a manual stop by itself.
- «Остановить сессию»: TDLib Close, keeps the Telegram login and the account.
  It writes the manual-stop marker so automatic restore does not reconnect it.
- «Выйти и удалить данные»: explicit confirmation, then TDLib LogOut, lease
  retirement, durable unregister and deletion of that account only.
- «Удалить незавершённый вход»: only for a slot that never reached READY.

Both Close and Logout failures preserve the account database and the other
accounts.

## Limits and recovery

- At most two secondary TDLib clients run alongside the primary. A third
  selection reports the limit and leaves existing sessions untouched.
- The registry is bounded to ten accounts; adding beyond it reports the limit.
- A selected account whose session cannot start shows `AccountRecoveryScreen`
  with Retry, Choose account, Connection settings and Go to primary. This is a
  recovery state, not an account-management flow.
- Main-account route changes still pause mismatched secondary sessions. The
  switcher shows the route state and offers connection settings instead of
  silently falling back to DIRECT.

Account isolation from PR #355 is unchanged: each selected account uses its own
`TelegramRepository`, TDLib database, drafts, archive state, media, notification
preferences and playback identity.

## Diagnostics

Account lifecycle events feed the existing shareable diagnostics buffers; there
is no second logging system. `MultiAccountDiagnostics` records:

- slot creation and the bounded registry state;
- DB lease/client creation, TDLib parameters and network enablement;
- every authorization transition, including READY and CLOSED;
- route preflight decisions and Mihomo/SOCKS5 enablement;
- `TelegramRepository` attachment, including the READY-but-not-attached gap;
- session stop/reconnect/restore, active-account switch and failures;
- logout, unfinished-slot removal and file cleanup;
- retries, timeouts, durations, operation IDs and classified errors.

Stage-level traces are enabled by default in DEV and can be enabled for Stable
in Settings → Diagnostics → «Подробный журнал аккаунтов». Milestones and
failures are always kept, so a production report still includes the final
result. Each failure carries `last_ok`, so a timeout identifies the stage that
did not finish. Phone numbers, codes, passwords, tokens, TDLib messages, proxy
endpoints and message contents are never recorded; attribute keys and account
IDs are filtered, and values pass through the existing redactor.

For `«Добавить аккаунт» → «Создаём сессию…» → timeout`, inspect the events in
this order:

1. `account_slot_create` — whether the slot was reserved;
2. `tdlib_start` / `tdlib_connect` — `route_preflight_done`, `owner_create`,
   `parameters_await`, `parameters_applied`;
3. `tdlib_request_timeout` — which TDLib function timed out;
4. `auth_*` — the last native authorization phase;
5. `repository_attach` / `repository_not_ready` — whether TDLib became READY
   but its full repository could not attach.

The same text is available through Settings → Diagnostics → «Поделиться
логами» (or «Посмотреть данные»). In a connected DEV build, `adb logcat -s
BeyGramAccounts` shows the same lines.

## Validation

- `AccountSwitcherModelTest` covers selected/connected/auth-phase transitions,
  loading, route pause, offline, retry/error and destructive-action visibility.
- `TdSecondarySessionCoordinatorTest` covers unfinished-slot discard, refusal
  to discard an authorized account, session limits and snapshot authorization.
- `TdSecondaryAuthFailureTest` covers safe credential-error classification.
- `AccountSwitcherContentTest` (Compose instrumentation) checks the compact
  row actions, unread accessibility, add-account action and inline route error.
- CI runs JVM tests and builds Dev/Prod/instrumentation APKs. It does not run
  device tests against a real Telegram account.

Real-device QA still needs: primary account/database preservation on first run,
2–3 authorized accounts with one-tap switching and no foreign data, process
kill/restart/APK update, network/Mihomo/listener failure recovery, logout of one
account while another stays signed in, and notification deep links for a
secondary account while the primary is selected.
