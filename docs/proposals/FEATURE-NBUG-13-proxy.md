# NBUG-13 Proxy option (ADR-045)

Status: **Built, not tested** on devices (batch C). Unit tests are in CI. Flag `nbug13_proxy`. It's on in
this build so the device tests can reach it; it's turned off if a batch C device run fails.

## 1. Problem (evidence)

- OMT-33 / NBUG-13: some hosts (adult sites, some mirrors) are blocked by IP or SNI on the owner's ISP.
  They played only with a system VPN. DNS can't fix an IP block, and the shipped automatic DNS bypass
  (SmartDns) only changes the address lookup.
- The app had no proxy option. `NetworkClient` used the system proxy selector, and five
  `HttpURLConnection` calls (subtitles, update check) used the default.
- ADR-037 left the proxy out of the first plan. The new master prompt makes it mandatory (item 5), so
  ADR-045 replaces that row.

## 2. Competitors (ideas only; nothing copied)

| Project | Commit | Files read | How it proxies |
|---|---|---|---|
| [alexch33/super-video-downloader](https://github.com/alexch33/super-video-downloader) (GPL-3.0) | 1ae2300ade26c1e749527180ebd5c800175078bc | `app/src/main/java/com/myAllVideoBrowser/util/proxy_utils/CustomProxyController.kt` | Sets JVM-wide `http.proxyHost`/`https.proxyHost` system properties and `Authenticator.setDefault`. Runs a local relay on 127.0.0.1:8888 so WebView can use a proxy that needs a login. WebView uses the `ProxyController` override |
| [nextcloud/android](https://github.com/nextcloud/android) (AGPL-3.0 file header) | 0c1060cefca2404fdb01fa4b2af210e6586b1a22 | `app/src/main/java/com/owncloud/android/utils/ProxyUtil.kt` | WebView-only `ProxyController` override with host:port. No login for web pages |

What Yumeiro does differently:

- It changes no JVM-wide system properties, so only Yumeiro's own clients see the proxy.
- It runs no local relay, so there's no open port on the phone and no extra thread.
- The Java login hook is installed only while a proxy with a login is on, and it answers only for that
  host and port.
- The WebView proxy login is answered from the sealed secret, with a limit of 3 answers a minute.

## 3. Design (built)

- `network/AppProxy`: a `ProxySelector` plus an OkHttp proxy authenticator, installed on the shared
  `NetworkClient`. That client covers extensions, catalog, images, playback and downloads. SmartDns keeps its own
  DoH client, which isn't proxied; with a proxy on, target hosts aren't looked up locally at all.
  - Off: delegates to the system selector, exactly as before.
  - Loopback, private ranges and `.local` hosts stay direct.
  - The target host is passed unresolved, so the proxy resolves it and a local DNS block doesn't matter.
  - If the configured proxy fails, there is **no** silent fallback to direct.
- `AppProxy.open(URL)` replaces `URL.openConnection()` in `OnlineSubtitles` (2 calls) and
  `GitHubReleaseClient` (3 calls).
- Storage:
  - Type, host, port and user name go in prefs `mt_network`.
  - The password is sealed with `SecretBox` (AndroidKeyStore) in `mt_proxy_secret`.
  - `Config.toString()` never shows the login.
- WebView:
  - `ProxyController` override with bypass `<local>` and private rules, applied from `SafeWebView.harden`.
    If several changes happen while one is being applied, the last one wins.
  - `SafeWebViewClient.onReceivedHttpAuthRequest` answers only the proxy's own host.
  - If the WebView has no `PROXY_OVERRIDE` support, Settings says "not supported" and doesn't claim
    web pages are covered.
- Startup: nothing is read on the main thread at launch. Settings load on the first request or in an
  idle handler.

## 4. Feature profile

| Field | Value |
|---|---|
| Purpose | Reach hosts blocked by IP/SNI without a system VPN, through the user's own HTTP or SOCKS5 proxy |
| Entry points | Settings → Network → Proxy (type Off/HTTP/SOCKS5, address, port, optional login, Test, Save) |
| Storage / migration | New prefs keys in `mt_network` (`proxy_type`, `proxy_host`, `proxy_port`, `proxy_user`) and the new `mt_proxy_secret` file. No migration: missing keys mean Off |
| Permissions | None new (INTERNET) |
| Settings | Off by default; changes apply immediately without restarting |
| Backup / restore | Type/host/port/user are backed up with the other settings. `mt_proxy_secret` is excluded (`Backup.excludedPrefs`), so a restored phone asks for the password again |
| Reset | Reset → Settings clears the proxy and its sealed password |
| Errors / offline | Test shows a plain message (unreachable, wrong login 407, timeout, bad address). A failing proxy fails requests visibly and never leaks to direct. A wrong login stops after 2 tries, with no loop |
| Accessibility | Every field has a label; the status and web-page state are text, read by TalkBack; tags `proxy_*` |
| TV | Same card in the TV build, focusable with the D-pad; device test on both TV images |
| Low-end | No extra threads or sockets (no relay); one shared client |
| Performance budget | Off: one volatile read per connection, same as the system selector. On: one proxy hop (the network's cost, not the app's). Startup: 0 ms on the main thread (lazy load) |
| Inventory | INVENTORY rows NBUG-13 proxy (core, WebView, UI) |

## 5. Side effects

- Off (the default): `select()` returns the system selector's answer, so the behaviour is unchanged.
  `AppProxyTest.off…` checks this.
- On: everything in §3 goes through the proxy. **Not covered**, and the card says so:
  - torrent peer connections (the engine has its own sockets);
  - SmartDns's own DoH/DoT lookups (only needed for the proxy's own address);
  - DNS lookups by other tools.
- SOCKS5 with a login: Android WebView can't send a SOCKS login, so web pages show "not supported with a
  login", while app requests (OkHttp) work. HTTP proxies with a login work for both.
- The Java login hook is set only while needed, and it answers only for the proxy's own host and port,
  so other sites never receive the proxy password.

## 6. Service terms

- This is the user's own proxy. Yumeiro provides no proxy server and bundles no proxy list.
- It doesn't bypass DRM, logins, paywalls or access controls. It only changes the network path, the same
  as a system VPN that the user already uses.

## 7. Tests

- Unit `AppProxyTest` (MockWebServer acting as the proxy), covering:
  - Off;
  - routing, with local hosts going direct;
  - validation;
  - the shared client logging in once on 407;
  - a wrong login stopping after 2 tries;
  - the URLConnection path;
  - the Test button.
- Fail-before: compile failure on the old code (no `AppProxy`).
- Device `ProxyDeviceTest` (log tag YuProxy), on both phones and both TVs:
  - a: the login is sealed and survives a reload;
  - b: OkHttp, HttpURLConnection and WebView go through a local test proxy with a login, or WebView is
    reported UNSUPPORTED;
  - c: Off clears everything.
- Network regression on device, in the same batch.
- Owner/community test: a real proxy against a blocked host, including SOCKS5 for web pages.
