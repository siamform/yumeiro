# NBUG-6 — Per-feature DNS routing with DNS-over-HTTPS (research note, no code)

Status: **Research note — gate not passed, nothing built.** Owner gate: "if it can be built without any side
effect, only then we build it." Must not regress SmartDnsResilienceTest, DnsWireClientTest, DataCacheBoundsTest
(verified CI 44067269319). Group G12, touches G11. Code read at head 1123e1d.

## Competitors (ideas only)

- **Mihon v0.21.3** (`core/common/.../network/NetworkHelper.kt:55–64`, `DohProviders.kt:28,45`): one app-wide DoH
  provider chosen in settings, applied to the one client; bootstrap by IP literals. No per-feature routing, no
  fallback chain. Same bootstrap idea as ours.
- **RethinkDNS v0.5.8**: per-app rules through a local VPN — a system-level design Yumeiro can't use without
  taking the user's VPN slot (rejected for Yumeiro: VPN is owner-blocked, G10 → OMT-18).

## 0. What exists today

- One shared OkHttp client, `network/NetworkClient.kt` (`.dns(SmartDns)`, one dispatcher 16/32 requests, one
  connection pool).
- `network/SmartDns.kt`: app-wide policy from `NetworkSettings.dns` — System (with **auto** bypass: a host the
  ISP blocks or sinkholes is resolved over DoH, rotating Cloudflare → Quad9 → Google, remembered 12 h), Fallback
  (system first, configured resolver after a real failure) or Always (configured resolver, system as safety net).
  A resolver that can't be reached is skipped for 2 min (`resolverDownUntil`, X11-1).
- So a per-host, sequential, single-engine fallback **already exists** (auto mode). NBUG-6 asks for a per-feature
  choice on top of it.

## 1. Is per-feature routing possible without forking the system resolver?

Yes, for everything that goes through OkHttp. `NetworkClient.get().newBuilder().dns(X).build()` gives a client
with its own `Dns` that still shares the dispatcher and the connection pool. Stacks found (rg over app/src/main):

| Stack | Path | Through NetworkClient? |
|---|---|---|
| Extensions (CloudStream `Requests`) | `com/lagradost/cloudstream3/network/RequestsHelper.kt` → `OnlineRuntime.extensionClient` | yes (`newBuilder()`) |
| Tachiyomi sources | `eu/kanade/tachiyomi/network/NetworkHelper.kt:21` | yes (`newBuilder()`) |
| Images (Coil) | `MTApplication.kt:103` | yes |
| Playback (Media3) | `stream/StreamMediaFactory.kt:258` `OkHttpDataSource` | yes |
| YouTube audio | `youtube/YouTubeAudioDataSource.kt:74` | yes |
| Drama (ONShort) | `sources/drama/OnShortCatalog.kt:284` | yes |
| Subtitles search/download | `subtitles/OnlineSubtitles.kt:101,135` | **no** — `HttpURLConnection`, system DNS |
| App update | `update/GitHubReleaseClient.kt:94,135` | **no** — `HttpURLConnection`, system DNS |
| WebView (browser checks, logins, headless capture) | `online/web/*` | **no** — Chromium resolves names itself; Android has no per-WebView DNS API (only an app-wide proxy override) |
| DoH lookups themselves | `SmartDns.kt:236` bootstrap client | separate client by design (no recursion) |

Side effect found (evidence, not a guess): OkHttp 5.5.1 `Address.equalsNonHost` compares `this.dns == that.dns`
(okhttp/src/commonJvmAndroid/kotlin/okhttp3/Address.kt:194–195, tag parent-5.5.1). Two features with different
`Dns` objects calling the **same host** can't share pooled connections → extra TCP + TLS handshakes and extra pool
slots. Same `Dns` object for every feature that keeps the default avoids it; only features with a non-default
policy pay it.

Second side effect: `SmartDns.cache` is keyed by host only. With per-feature resolvers, feature A's DoH answer
would be served to feature B (which asked for system DNS) for up to 10 min. Any build must key the cache by
(resolver, host).

## 2. Cost of concurrent engines (the no-side-effect proof)

From code: a DoH "engine" is one `DnsOverHttps` + one bootstrap `OkHttpClient` per provider, created lazily and
kept in `dohClients` (at most one per provider, so at most 3 for auto plus the user's choice). Idle cost: no
threads (OkHttp dispatcher threads exist only while calls run; pool cleanup runs only with idle connections),
no wake-ups, no radio. Per-feature routing does not need more engines than providers — features share them.

Not measured yet (needed before any claim): heap of one DoH client after first use, connection count with two
features on one host, and radio-on time. Plan: a device test (emulator, api33-lowram) that resolves 20 hosts via
two policies and logs `Debug.getNativeHeapAllocatedSize`, `ConnectionPool.connectionCount()`, and thread count
before / after. Until then the proof is **not** given.

## 3. DoH bootstrap

`SmartDns.doh()` builds `DnsOverHttps` with `.bootstrapDnsHosts(provider.bootstrap.map { InetAddress.getByName(it) })`
(`SmartDns.kt:242`). The bootstrap entries are IP literals (`NetworkSettings.kt:17,22`: "the resolver's own IP
addresses so it can be reached without DNS"); `InetAddress.getByName` on a literal does no lookup. So
cloudflare-dns.com is **not** resolved by system DNS. A DNS-only ISP block can't stop the bootstrap. An ISP that
blocks the resolver's IP or SNI blocks DoH itself → the next provider is tried (Quad9, Google) and the dead one is
skipped for 2 min. Not handled: an ISP that blocks all three by IP — then system DNS answers (existing behaviour).
`eu/kanade/tachiyomi/network/DohProviders.kt` uses the same bootstrap pattern (lines 29, 46, 60).

## 4. Sequential exhaustion timing

Bound from code (not a measurement): bootstrap client connect 5 s + read 5 s per provider; 3 providers in
sequence → up to ~30 s worst case for the first lookup of a blocked host on a network that blackholes all DoH;
then each dead provider is skipped for 2 min, so later lookups fall back at once. Measurement plan: JVM unit
test on CI with `dohFactory` replaced by resolvers that hang for a fixed time, plus one real run against a
non-routable bootstrap address (10.255.255.1) to time the actual connect timeout. Result goes here before any
build decision.

## 5. Per-feature DNS policy source

Owner's call. Options: (a) settings screen per feature; (b) per-integration defaults in code (e.g. drama →
auto, extensions → auto, playback → system); (c) hard-coded map. Recommendation for the least side effect:
(b) with one shared default `Dns` object so nothing changes for features left on default.

## 6. In-flight requests on a DNS switch

OkHttp resolves only when it opens a connection (RouteSelector). A call already connected keeps its socket;
pooled connections are reused only by calls with an equal `Address` (same `Dns`). So switching a feature's
policy affects new connections only: no cancel needed, no retry. Race to handle: the shared host cache (§1)
and `resolverDownUntil` (keyed by provider, shared — fine, a dead resolver is dead for everyone).

## Verdict for the gate

Not yet "without any side effect": (i) no connection reuse across features with different policies on the same
host, (ii) the host cache must be split, (iii) WebView, subtitles and app update can't follow per-feature DNS
without a WebView proxy (app-wide) or moving the two `HttpURLConnection` users to NetworkClient. Cost of engines
(§2) and exhaustion timing (§4) still need measurement. Cheaper option already shipped: the single-engine
sequential auto bypass. Proposed next step (owner approval needed): measure §2 and §4; then decide.
