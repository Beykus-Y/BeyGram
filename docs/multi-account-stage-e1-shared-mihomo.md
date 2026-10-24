# Issue #185 — Stage E1: shared Mihomo network policy (fail-closed preflight)

This PR **does not start a secondary TDLib client or Mihomo listener**.
It defines the preflight that the future multi-session manager MUST consult
before creating/configuring another native client. It leaves the authorized
legacy account, proxies, subscriptions and port untouched.

## Existing process-level invariants

- `MihomoProxyService` / `VpnBackend` already own **one** process-wide
  listener. Do not start a new listener, allocate a second port or instantiate
  a new backend per Telegram account.
- The installation has credentials stored by `LoopbackProxyCredentials`,
  with Dev/Stable ports separated by `BuildConfig.MIHOMO_PROXY_PORT`.
- Merely observing `ProxyRuntimeStatus.RUNNING` or an open TCP port is not
  sufficient to authenticate the listener's owner.
- The preflight probes the same SOCKS endpoint with this installation's
  credentials using `LoopbackSocksProbe` and requires `OWN_LISTENER`.
- Socket probe is short and runs on `Dispatchers.IO`; unknown/offline/foreign
  statuses are **blocked**, never silently converted to direct.
- No account IDs, TG credentials, user proxy secrets or subscription URLs
  are logged or added to reports by this component.

## Policy matrix

| Secondary preference | Primary TDLib route | Outcome |
| --- | --- | --- |
| Follow primary | Direct | Direct (intentional inheritance) |
| Follow primary | Subscription | Shared Mihomo only with verified own listener |
| Follow primary | Manual proxy | Block: independent per-account proxy credentials must be configured later |
| Follow primary | Unknown / failed query | Block, not Direct |
| Explicit direct | Any | Direct; only a future user action may select this |
| Explicit shared Mihomo | Any | Shared Mihomo only with verified own listener |
| Any shared Mihomo route | Foreign listener, stopped, or failed probe | Block; never try direct as fallback |

No network-policy shortcut may override primary account's manually configured
proxy or automatically restart/toggle its Mihomo backend. A future client owner
must query **current** primary routing, evaluate this gate, create secondary
TDLib only when allowed, configure the client with the same local SOCKS port
and credentials, and re-check routing on proxy restarts.

## What is NOT implemented

No add-account/switch UI, active secondary TDLib client, per-account saved
proxy setting, cross-account notifier, or per-account proxy activation.
The existing user's authorization and network path remain unchanged.

JVM regression tests in `TdSecondaryAccountRoutingTest` validate each
fail-closed branch, including foreign listener and probe failure.

After this isolated preflight lands, next integration needs to enforce these
decisions in the real multi-client lifecycle (stage B3) and monitor transitions
from RUNNING → FAILED/STOPPED, preventing accidental direct fallback while
maintaining legacy session availability. Keep #185 open.
