# Mihomo local proxy (dev)

## Runtime model

BeyGram embeds the pinned `libmihomo-android` core and starts it as a loopback SOCKS proxy on `127.0.0.1:17891`. It does not create an Android `VpnService`, TUN interface, or system-wide VPN profile. Mihomo's external controller listener is not exposed; app-to-core control uses the in-process JNI action API.

TDLib can be assigned the local SOCKS proxy through its proxy API. BeyGram-owned HTTP and TCP requests use the same local proxy client while it is running; subscription retrieval stays direct until the proxy is active. Requests to the subscription URL send an opaque random installation identifier in `X-BeyGram-HWID`; no IMEI, serial number, Android ID, or Telegram account data is used as HWID.

## Profile handling

Subscription YAML is normalized to disable remote listeners, external controller access, and Mihomo TUN mode. One `BeyGram Telegram (dev)` selector is added for the selectable proxy group. The profile is validated before replacing the stored profile in app-private, non-backed-up storage.

The DNS resolver for proxy-node hostnames is pinned to Android's `system` resolver (`dns.proxy-server-nameserver`). This bootstrap lookup must work before a proxy can be reached; provider DNS endpoints may be blocked by the current Wi-Fi or mobile network.

Profiles stored by earlier builds are migrated during core initialization. Migration re-normalizes the existing YAML and restores the app-local SOCKS port, preserving the subscription's proxies and groups. This is needed because an in-place APK update preserves app data, including the previously saved profile.

## Connectivity checks and automatic selection

The connection list is read from Mihomo's in-process `getProxies` action. **Проверить Telegram через все** runs a per-node `testDelay` against `https://web.telegram.org/`. It checks that endpoint through each proxy; it does not guarantee reachability to every Telegram MTProto data center.

Automatic selection is off by default. When enabled, it watches TDLib's sustained `Connecting` state, waits eight seconds, checks available validated Wi-Fi/cellular network routes, tests the available nodes, and selects the lowest-delay successful node while Mihomo is active. The network indicators describe validated routes reported by Android, not the device's radio-toggle settings.

## Dev diagnostics

The Dev build records subscription and Mihomo lifecycle events. It also enables Mihomo debug level and captures warning/error events during proxy probes. Probe results record the sanitized node label, delay, and elapsed time. TLS failures include a short, sanitized exception/cause chain. URLs, request headers (including HWID), response bodies, secrets, and proxy endpoints are removed. Exported diagnostics include connection state, network route state, timestamps, and recent events.

## Dependency and local build

`app/build.gradle.kts` fetches the exact `libmihomo-android` v0.3.11 AAR into `app/build/` and verifies its pinned SHA-256 before packaging. The wrapper bundles Mihomo v1.19.32 under GPL-3.0; see `THIRD_PARTY_NOTICES.md` and `LICENSES/libmihomo-GPL-3.0.txt`.

```powershell
.\\gradlew.bat :app:assembleDevDebug
```
