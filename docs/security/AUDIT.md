# Security audit: chore/hardening (2026-11-07)

Scope: the repo, the GitHub Actions workflows, the market/streams catalogs, the in-app updater, and the Android app.

## Fixed in this branch
- CI/CD: least-privilege `permissions`, all actions pinned to SHAs, `persist-credentials: false`, keystore written with
  umask 077 and shredded with `if: always()`, a pinned JDK, a gitleaks job, dependency review, and a CodeQL workflow.
- Catalogs: HTTPS plus an allowlist of `raw.githubusercontent.com/siamform/yumeiro/{market,streams,extensions}`,
  with a final-URL check after redirects, size and time limits, and a fallback to the cached copy on failure.
- Updater: assets must come from this repo's release URL, the SHA-256 must match the GitHub digest and/or
  `SHA256SUMS.txt` (the install fails closed when no checksum exists), and the dialog shows the source.
- App: a permission-guarded exported receiver, tokens kept in a backup-excluded prefs file, WebView file/content
  access disabled, and vulnerable jsoup/guava versions overridden.

## Proposals (not implemented; need approval)
1. **Signed catalogs.** CI signs `index.json` with minisign (ed25519). The app embeds the public key and rejects
   unsigned or invalid indexes, so a compromised branch or CDN can't inject entries. Cost: one secret
   (`MINISIGN_KEY`) and about 1 KB of verification code using the platform Ed25519 (API 33+) or a small pure-Kotlin verifier.
2. **Mirror.** Add `https://cdn.jsdelivr.net/gh/siamform/yumeiro@market/index.json` as a second source, used only
   when the primary fails, and only with (1), because the mirror is a separate host. A GitHub takedown also takes
   jsDelivr down, so the existing last-known-good cache stays the real fallback.
3. **Tag-based releases.** Release only on `v*` tags (already protected by a ruleset) rather than every push to `main`.
4. **Split market jobs.** Build/validate third-party data in a job with `contents: read`, then publish in a separate job
   that has the write token and only receives the artifact.
5. **Extensions.** Require a sha256 pin for community/discovered origins on auto-update, or ask before applying.
6. **AniList OAuth.** Add a `state` nonce to the implicit-grant flow, or switch to the authorization-code flow.
7. **Playlist integrity.** Verify the `sha256` from the market index when downloading a playlist file.
8. ~~Seamless updates~~. **Done (maintainer decision):** silent install was removed. Every update shows the version
   and source, and Android's install confirmation always appears (`USER_ACTION_REQUIRED`).

## Accepted risks (maintainer decisions, 2026-11-07)
- **HTTP streams and extensions (maintainer decision).** Many IPTV streams and popular CloudStream extensions use plain
  HTTP and are harmless, so cleartext stays allowed in the base config. App code, updates and catalogs never use HTTP:
  GitHub, githubusercontent and OpenSubtitles are HTTPS-only in `network_security_config.xml`, and `TrustedSources`
  rejects non-HTTPS catalog and update URLs.
- **In-process extensions (maintainer decision).** CloudStream extensions run inside the app; sandboxing would break the
  extension ABI. Mitigation is curation: the market lists established, popular extensions (stars, community use),
  installs show trust warnings, and the sha256 is verified whenever the source publishes one.
- `MainActivity` (VIEW/SEND) and `MediaSessionService` are exported by design.

## CodeQL suppressions (2026-11-09)

`.github/codeql/suppressions.jq` drops these results before upload. Each one is a rule **and** an exact path; any new
result elsewhere still fails the gate.

| Rule | Path | Why it is accepted |
|---|---|---|
| `java/weak-cryptographic-algorithm`, `java/potentially-weak-cryptographic-algorithm` | `app/src/main/java/dev/asfakul/yumeiro/sources/js/JsCrypto.kt` | MD5 key derivation (OpenSSL `EVP_BytesToKey`) and AES-CBC are what CryptoJS and Mangayomi sources call; the sites encrypt with them. Used only to decode third-party page data, never to protect Yumeiro data. |
| same | `app/src/main/java/dev/asfakul/yumeiro/youtube/YtAuthHash.kt` | YouTube's `SAPISIDHASH` request signature is SHA-1 by YouTube's own definition (`SHA1("<ts> <SAPISID> <origin>")`). It signs requests for the optional YouTube account; nothing of ours is protected by it. The cookie itself is sealed with `SecretBox` and excluded from backups. |
| same | `app/src/main/java/dev/asfakul/yumeiro/scrobble/LastFmSignature.kt` | Last.fm's `api_sig` is MD5 by Last.fm's own definition (https://www.last.fm/api/authspec). It signs scrobble requests; nothing of ours is protected by it. The session key is sealed with `SecretBox` and excluded from backups. Added 2026-11-09 (§22). |
| same | `app/src/main/java/eu/kanade/**` | Vendored Tachiyomi/Aniyomi source API (Apache-2.0). Its signatures are the extension ABI and can't change. |
| `java/android/missing-certificate-pinning` | `tracking/Kitsu.kt`, `tracking/Trakt.kt`, `sources/servers/PlexProvider.kt`, `notify/PushWake.kt`, `youtube/YtAccount.kt` | Kitsu and Trakt rotate certificates without notice (a pin would break sign-in for every user); Plex servers are the user's own hosts with arbitrary certificates; the UnifiedPush server is whatever the user's push app uses (ntfy.sh or self-hosted) and only receives empty "wake" messages; the pasted-cookie check goes to youtube.com, whose certificates Google rotates. System trust + HTTPS-only applies. |
| `java/android/websettings-javascript-enabled` | `ui/screens/settings/YouTubeAccountScreen.kt` | Google's own sign-in page needs JavaScript. The WebView loads only Google/YouTube sign-in, with file and content access off and non-http schemes blocked (`SafeWebView.harden`). Content-access alerts on the same WebView were fixed (explicit `allowFileAccess`/`allowContentAccess = false`). |
| `java/android/websettings-javascript-enabled` | `ui/screens/online/BrowserCheckHost.kt` | The captcha / site sign-in page: Cloudflare, Turnstile, hCaptcha and site logins only work with JavaScript (Mihon and CloudStream use the same kind of page). No JS bridge; file and content access set off explicitly; navigations, pop-ups and sub-requests go through `AdShield`; the pop-up probe runs with JavaScript off and is destroyed after 5 s. Added 2026-11-09 (PR #17). |
| `java/android/missing-certificate-pinning` | `online/subtitles/OpenSubtitles.kt`, `online/subtitles/SubDL.kt`, `online/subtitles/SubSource.kt` | Third-party subtitle APIs behind CDNs that rotate certificates without notice; a pin would break subtitle search for every user. HTTPS-only for these domains in `network_security_config.xml`, system CAs only, and the user's key header is removed from any hop that leaves the site or drops to HTTP (`SubtitleNet.keyScoped`, `SubtitleKeyScopeTest`). Added 2026-11-09 (PR #17). |
| `java/android/missing-certificate-pinning` | `notify/CloudAlerts.kt` | Yumeiro Cloud (`server/`, a Cloudflare Worker at `api.yumeiro.click`). Cloudflare issues and rotates the edge certificate automatically, so a pin would silently break push alerts for every user. Only the push endpoint and AniList ids are sent; HTTPS-only for `yumeiro.click` in `network_security_config.xml`. Added 2026-11-09. |
| same | `tracking/Simkl.kt`, `scrobble/LastFm.kt`, `lyrics/LrcLib.kt` | Third-party APIs (Simkl, Last.fm, LRCLIB) that rotate certificates; a pin would break tracking, scrobbling and online lyrics for every user. HTTPS-only for `simkl.com`, `audioscrobbler.com`, `last.fm`, `lrclib.net` in `network_security_config.xml`, system CAs only. Tokens and session keys are sealed with `SecretBox`. Added 2026-11-09 (§22). |
| `java/weak-cryptographic-algorithm` | `app/src/main/java/com/lagradost/cloudstream3/extractors/Vidplay.kt`, `.../VidSrcTo.kt` | CloudStream v4.5.2 extractors, restored in R11b because real extensions call them. The sites encode their stream ids with RC4, so decoding has to use RC4; it only reads third-party page data and protects nothing of ours. Added 2026-11-13 (PR #27). |
| `java/android/websettings-javascript-enabled`, `java/android/webview-addjavascriptinterface` | `ui/screens/stream/PageStreamFinderDialog.kt` | The stream finder opens a site's own video player, which needs JavaScript. The `MTFinder` bridge exposes only two `@JavascriptInterface` methods that take a string (a media URL to record; a DRM system name cut to 40 safe characters). With minSdk 24 no other Java method is reachable from the page. File and content access are off, non-http schemes are blocked, and ads are filtered (`SafeWebView`, `AdFilter`). Added 2026-11-13 (PR #27). |
| `java/android/missing-certificate-pinning` | `online/anime/AniList.kt`, `AniSkip.kt`, `AnimeIds.kt`, `youtube/music/YtMusicApi.kt`, `notify/FcmPush.kt`, `online/ExtensionMarket.kt`, `sources/UniversalAdd.kt`, `subtitles/OnlineSubtitles.kt` | Hosts Yumeiro doesn't control (AniList, AniSkip, the anime id mapping API, YouTube Music, Google FCM, GitHub / user extension repos, links the user pastes, subtitle sites). They rotate certificates; a pin would break them for every user. HTTPS, system CAs only. Added 2026-11-14 (PR #30). |
| `java/android/websettings-javascript-enabled`, `java/android/webview-addjavascriptinterface` | `online/web/HeadlessCapture.kt`, `online/web/BrowserCheck.kt` | Off-screen versions of the stream finder and the site check: they load a site's own JavaScript player / check page. HeadlessCapture's `MTFinder` bridge is the same two string-only methods as `PageStreamFinderDialog`; BrowserCheck has no bridge. File and content access off, `SafeWebView.harden`, ads filtered. Added 2026-11-14 (PR #30). |
| `java/android/websettings-allow-content-access` | `ui/screens/stream/PageStreamFinderDialog.kt`, `ui/screens/settings/YouTubeAccountScreen.kt`, `online/web/HeadlessCapture.kt` | False positive: every WebView there gets `allowFileAccess`/`allowContentAccess = false` on the line after it is created (plus `SafeWebView.harden`). The query treats later reads of the WebView from a variable or Compose state as new WebViews. Added 2026-11-14 (PR #30). |
| `java/weak-cryptographic-algorithm` | `online/download/HlsDownloader.kt` | HLS `#EXT-X-KEY METHOD=AES-128` is AES-128-CBC with PKCS7 padding by the HLS spec (RFC 8216 §5.2); the site encrypted the segments that way, so decrypting needs it. Protects nothing of ours. Added 2026-11-14 (PR #30). |

Fixed instead of suppressed (PR #30, 2026-11-14): `java/unsafe-cert-trust` in `network/DnsMessage.kt` (DNS-over-TLS
socket now sets endpoint identification explicitly and checks the hostname with the system verifier after the
handshake), `java/local-temp-file-or-directory-information-disclosure` in `online/ExtensionManager.kt` (the manifest
is read from memory, no temp file) and `java/android/backup-enabled` (`allowBackup="false"`: Yumeiro's own Backup v2
in Settings covers backups, and Android 12+ device-to-device transfer still follows `data_extraction_rules.xml`).

Fixed instead of suppressed (PR #17): `websettings-allow-content-access` x4 in `BrowserCheckHost.kt` (explicit
`allowFileAccess`/`allowContentAccess = false` on the page and the pop-up probe; no WebView held in Compose state). Rule of thumb
for new WebViews: `val page = WebView(ctx)`, set `allowFileAccess`/`allowContentAccess = false` on `page` in the same
function, configure the rest in `with(page) { }` and return `page`. Never `WebView(ctx).apply { }`: the query treats
`apply`'s return value as another WebView source and doesn't credit settings made inside it or in `harden()`. (Full
scans on `main` happened to report 0; diff-informed PR scans flag every such line the PR touches.)

Fixed instead of suppressed: cleartext trusted-extension set (now sealed with `SecretBox`) and the app-lock
`insecure-local-authentication` finding (prompt bound to a Keystore `CryptoObject`, `security/LockKey`).

## UnifiedPush components (2026-11-09)

The UnifiedPush connector adds an exported `MessagingReceiverImpl` and `RaiseToForegroundService` (required by the
spec so any distributor app can deliver). Broadcasts without the registration's random token are dropped by the
library; message content is ignored by Yumeiro, and a wake-up only starts the throttled background checks. The push
endpoint is stored with `SecretBox` and never backed up.

## Extension safety check and site sign-in (2026-11-09)

Every extension install/update is read before it is loaded (`online/ExtensionScanner`): the `.cs3` zip entries and
its DEX string/type/method tables. Shell commands, hidden/dynamic code loading, native code, SMS, contacts, location,
camera/mic, package install, accessibility/device admin, account reads, miners and shared-storage deletes are DANGER;
trackers, device IDs, clipboard, installed-app lists, JS bridges and Yumeiro-internal references are CAUTION. The
user sees the report before installing; DANGER needs a double confirm and is refused in automatic paths. It is a
heuristic (reflection or downloaded code can hide behaviour) and complements, not replaces, trusted repos.

Site sign-in (`BrowserCheck.openSite`, `SiteLogins`) keeps cookies in the app's WebView cookie store and sends them
only to the same host (and its subdomains). Signing out clears that host's cookies. No passwords are stored.

## Historical secret findings

| Finding | Status |
|---|---|
| Kitsu OAuth client secret, commit `ba30fae` on `feat/universal-sources` (PR #13, never merged) | Removed from code: it now comes from `BuildConfig` and the `KITSU_CLIENT_*` repo secrets. The branch was deleted (2026-11-09). GitHub keeps `refs/pull/13/head`, so **rotate the secret** in the Kitsu developer settings and update the repo secrets. |

## Subtitle API keys and redirects (2026-11-09, PR #17)

OkHttp strips only `Authorization` when a redirect changes host; custom key headers are resent to the new host.
OpenSubtitles (`Api-Key`) and SubSource (`X-API-Key`) now go through `SubtitleNet.keyScoped`, a network interceptor
that removes the key from any hop that is not HTTPS on the site's own domain (or a subdomain). SubDL's key is a query
parameter of the API URL only; redirects get a new URL, so it is not forwarded.

## Why PR CodeQL can pass for months and then fail on an old line

The CodeQL action is diff-informed on pull requests: it reports only alerts on lines the PR adds or changes.
Re-indenting or moving old code (as PR #17 did in `BrowserCheckHost.kt`) makes its old findings "new". `main` is
only fully scanned by the monthly schedule. Run the CodeQL workflow by hand (`workflow_dispatch`) on `main` after a
big refactor to see the full picture.

## Antivirus heuristics (2026-11-11, v1.5.1)

**What was flagged.** On VirusTotal, v1.5.0 (`release-Yumeiro-v1.5.0.apk`, SHA-256 `2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924`) was flagged by 2 of 67 engines:
- Kaspersky: `Not-a-virus:HEUR:RiskTool.AndroidOS.Miner.b`
- BitDefenderFalx: `Android.Riskware.Agent.gHMOM`

VirusTotal put the file in the pua/miner categories and tagged it `checks-gps`, `telephony`, `contains-elf` and `reflection`. The full write-up is in [VT-REPORT-v1.5.1.md](VT-REPORT-v1.5.1.md).

**Root cause.** The extension safety check (`online/ExtensionScanner.kt`) stored its threat signatures as plain strings, for example `stratum+tcp`, `xmrig`, `coinhive`, `cryptonight`, `content://sms`, `Landroid/telephony/SmsManager;`, `ContactsContract` and `android/app/admin/`. Nothing else in Yumeiro references these strings, so the APK looked as if it contained a miner and an SMS or contacts stealer. Heuristic engines match those strings without knowing that the app only *looks for* them in third-party extensions.

**Fix (no feature removed):**
1. The scanner now stores a 64-bit FNV-1a fingerprint of every normalised indicator rather than the indicator itself. This is the same approach antivirus signature databases use. Detection is unchanged:
   - `ExtensionScannerTest.everyIndicatorStillTriggersItsRule` feeds every readable indicator into the scanner.
   - `fingerprintsMatchReadableIndicators` checks that the shipped table equals the readable list.
   - `lookalikesDoNotTrigger` checks that near-miss strings do not match.

   The readable list lives in `ExtensionScannerTest.INDICATORS` and in the "Readable indicator list" below. Nothing is hidden from reviewers.
2. Removed `DOWNLOAD_WITHOUT_NOTIFICATION`. It only served a silent self-update path that was already unreachable (`canGoSilent()` always returned false). That path is deleted, so update downloads always show the system notification, and installs still go through the system installer confirmation (`USER_ACTION_REQUIRED`).
3. Commented the remaining sensitive permissions in the manifest.

**Kept on purpose (legitimate, documented):**
- `REQUEST_INSTALL_PACKAGES`: the in-app updater. The user always confirms the install.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: an opt-in in the Permission Center, needed for on-time episode alerts.
- `RECEIVE_BOOT_COMPLETED`: WorkManager reschedules jobs after a reboot.
- `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE`: DLNA discovery and Watch together on the local network.
- `checks-gps`: androidx.appcompat `TwilightManager` (day/night) calls `LocationManager` guarded by a permission check. Yumeiro does not request any location permission, so the call always falls back. This is library code.
- `telephony`: Media3 and androidx use `TelephonyManager` for network-type detection (adaptive streaming). No phone number, IMEI or SIM data is read, and `READ_PHONE_STATE` is not requested.
- `contains-elf`: native libraries for FFmpeg decoding (nextlib), QuickJS (JavaScript sources), zstd and libass (subtitles).
- `reflection` and `DexClassLoader`: needed to load user-installed Aniyomi, Mihon and CloudStream extensions. Each one is checked by the extension safety check first.

**Readable indicator list** (what the fingerprints stand for). Prefixes: `t:` type, `c:` class prefix, `p:` package, `m:` method, `s:` URI scheme, `u:` scheme and authority, `h:` host suffix, `w:` word, `x:` exact string.

| Rule | Indicators |
|---|---|
| Runs system commands | `m:Ljava/lang/Runtime;->exec`, `m:Ljava/lang/ProcessBuilder;-><init>`, `m:Ljava/lang/ProcessBuilder;->start` |
| Loads hidden code | `t:Ldalvik/system/DexClassLoader;`, `t:Ldalvik/system/InMemoryDexClassLoader;`, `m:Ldalvik/system/PathClassLoader;-><init>`, `t:Ldalvik/system/DexFile;` |
| Loads native code | `System`/`Runtime` `load` and `loadLibrary` |
| Reads or sends SMS | `t:Landroid/telephony/SmsManager;`, `u:content://sms`, `u:content://mms` |
| Reads contacts or call log | `ContactsContract`, `CallLog`, `content://contacts`, `content://call_log`, `content://com.android.contacts` |
| Tracks location | `LocationManager` `requestLocationUpdates` / `getLastKnownLocation` / `getCurrentLocation`, `com/google/android/gms/location/` |
| Records camera or microphone | `android/hardware/camera2/`, `Camera`, `AudioRecord`, `MediaRecorder` |
| Installs apps or controls the device | `PackageInstaller`, `android/app/admin/`, `android/accessibilityservice/`, `application/vnd.android.package-archive` |
| Reads your accounts | `AccountManager` `getAccounts` / `getAccountsByType` / `getPassword` / `peekAuthToken` |
| Crypto miner | `stratum+tcp`, `stratum+ssl`, `coinhive`, `coin-hive`, `xmrig`, `cryptonight` |
| Reads device identifiers | `TelephonyManager`, `android_id` (with a `Settings$Secure` read) |
| Lists your installed apps | `PackageManager` `getInstalledPackages` / `getInstalledApplications` / `queryIntentActivities` |
| Reads the clipboard | `ClipboardManager` `getPrimaryClip` / `getText` |
| Defines code at run time | `ClassLoader->defineClass` |
| Analytics or ad trackers | google-analytics.com, googletagmanager.com, app-measurement.com, appsflyer.com, doubleclick.net, graph.facebook.com, adjust.com, branch.io, mixpanel.com, onesignal.com |
| JavaScript bridge in a web page | `WebView->addJavascriptInterface` |
| Notifications or services | `NotificationManager->notify`, `androidx/work/`, `startService`, `startForegroundService` |

To add or change an indicator, edit `ExtensionScannerTest.INDICATORS`. Then regenerate the fingerprint table with `ExtensionScanner.fingerprint(key)`; the test prints any mismatch.
