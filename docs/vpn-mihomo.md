# Mihomo local proxy

The proxy is available in every build variant (dev and prod); no build flag gates it.

## Runtime model

BeyGram embeds the pinned `libmihomo-android` core and starts it as a loopback SOCKS proxy on `127.0.0.1:17891`. It does not create an Android `VpnService`, TUN interface, or system-wide VPN profile. Mihomo's external controller listener is not exposed; app-to-core control uses the in-process JNI action API.

TDLib can be assigned the local SOCKS proxy through its proxy API. BeyGram-owned HTTP and TCP requests use the same local proxy client while it is running; subscription retrieval stays direct until the proxy is active. Requests to the subscription URL send an opaque random installation identifier in `X-BeyGram-HWID`; no IMEI, serial number, Android ID, or Telegram account data is used as HWID.

The backend lives in the process, not in a screen: `VpnBackend` is created once with the application scope (`BeyGramApplication`). When TDLib still points at the local entry after a process restart, the listener is started again; if that fails, TDLib is switched back to its previous proxy instead of keeping a dead local port.

## Local SOCKS credentials

Every app on the device can open a connection to `127.0.0.1`, so the listener requires credentials. A random pair is generated on first use and kept in app-private preferences (`mihomo-loopback`). The Mihomo profile gets the pair as top-level `authentication`, and TDLib's local entry is created with the same pair. If the pair ever changes, the stale TDLib entry is removed and recreated. Java clients (subscription download, probes) use a process-wide `Authenticator` that answers only for `127.0.0.1:17891`.

## Profile handling

Subscription YAML is normalized to disable remote listeners, external controller access, and Mihomo TUN mode. One `BeyGram Telegram` selector is added for the selectable proxy group. The profile is validated before replacing the stored profile in app-private, non-backed-up storage. A plain list of proxy URIs is read before the text is treated as Base64.

The selected node is stored per group in `mihomo-selection` and passed back as `selected-map` whenever the core is initialized, so the choice survives core re-initialization and process restarts.

The DNS resolver for proxy-node hostnames is pinned to Android's `system` resolver (`dns.proxy-server-nameserver`). This bootstrap lookup must work before a proxy can be reached; provider DNS endpoints may be blocked by the current Wi-Fi or mobile network.

Profiles stored by earlier builds are migrated during core initialization. Migration re-normalizes the existing YAML, restores the app-local SOCKS port and credentials, and adds the `BeyGram Telegram` group, preserving the subscription's proxies. This is needed because an in-place APK update preserves app data, including the previously saved profile.

## Proxy screens

- **Прокси** (Settings → «Прокси для Telegram», or the start and login screens): the subscription entry with its best server, a «Без прокси» row, and saved proxies. Selecting a row applies it immediately. Saved proxies are stored by TDLib; their latency is a TCP connect to the proxy server.
- **Подключение к Telegram**: tabs MTProto, SOCKS5, Web (HTTP) and Подписка. Manual tabs save the proxy in TDLib and enable it at once. The subscription tab loads the link (refresh button), shows the servers with their delay, and starts the listener with the chosen server.
- The segmented control «Автоматически / Вручную» on the subscription tab switches automatic server selection (see below). The design file does not describe more than that; the mapping is an interpretation.

## Connectivity checks and automatic selection

The connection list is read from Mihomo's in-process `getProxies` action. **Проверить задержку** on the subscription tab runs a per-node `testDelay` against `https://web.telegram.org/`. It checks that endpoint through each proxy; it does not guarantee reachability to every Telegram MTProto data center.

Automatic selection is off by default. When enabled, it watches TDLib's sustained `Connecting` state, waits eight seconds, checks available validated Wi-Fi/cellular network routes, tests the available nodes, and selects the lowest-delay successful node while Mihomo is active. The network indicators describe validated routes reported by Android, not the device's radio-toggle settings.

## Diagnostics

Every build records subscription and Mihomo lifecycle events in the shareable connection log. Debug builds additionally write them to logcat, enable Mihomo's debug log level, and print captured core warnings. Probe results record the sanitized node label, delay, and elapsed time. TLS failures include a short, sanitized exception/cause chain. URLs, request headers (including HWID), response bodies, secrets, and proxy endpoints are removed. Exported diagnostics include connection state, network route state, timestamps, and recent events.

## Dependency and local build

`app/build.gradle.kts` fetches the exact `libmihomo-android` v0.3.11 AAR into `app/build/` and verifies its pinned SHA-256 before packaging. The wrapper bundles Mihomo v1.19.32 under GPL-3.0; see `THIRD_PARTY_NOTICES.md` and `LICENSES/libmihomo-GPL-3.0.txt`.

```powershell
.\\gradlew.bat :app:assembleDevDebug
```
