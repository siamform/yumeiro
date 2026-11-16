# MASVS v2 write-up (G12 X7)

Branch `r15-stability`, 2026-11-16. Reference: OWASP MASVS v2.1 control groups
(https://mas.owasp.org/MASVS/). Each control lists what the app does, where (file), what tests it,
and the gap if there is one. "Unit" = JVM/Robolectric test in `app/src/test`; "emulator" = CI device
run; "code" = code review only, no test. Nothing here is claimed for real hardware.

Threat model in one line: an open-source (GPL) media app that runs third-party extension code by
design (CloudStream `.cs3`, Aniyomi/Tachiyomi, JavaScript sources). An extension is code in our
process; the controls below protect the user's accounts, data and updates from networks, other apps,
hostile web pages and repackaged copies, not from an extension the user chose to install
(ADR-014, ADR-015 in `docs/SYSTEM-DESIGN.md`).

## Summary

| Group | Status | Main gap |
|---|---|---|
| STORAGE | met | backup files are plain JSON (no secrets in them, by design) |
| CRYPTO | met | — (legacy ciphers only inside stream extractors, not for our data) |
| AUTH | met (no own backend accounts) | app lock is local only |
| NETWORK | partly | cleartext allowed by default for user IPTV/LAN streams; no pinning |
| PLATFORM | met | exported media services are reachable by any media controller (intended) |
| CODE | partly | extension code is arbitrary by design; TV build on Jackson 2.13.5 (ADR-015) |
| RESILIENCE | partly (by choice) | no root/emulator/debugger checks (open source) |
| PRIVACY | met | — |

## MASVS-STORAGE

- **STORAGE-1 (sensitive data stored securely).** Account tokens, API keys, Last.fm session, home
  server passwords and YouTube account cookies are sealed with an AES-256-GCM key generated in the
  Android Keystore (`security/SecretBox.kt`, format `v1:` + base64(iv‖ct‖tag)); `seal` returns null
  rather than falling back to plaintext. Callers: `online/OnlineSettings.kt` (`secret`/`setSecret`,
  every `*_token`, `*_refresh`, `*_client`, `lastfm_*`), `sources/servers/HomeServers.kt`,
  `sources/tachiyomi/TachiyomiExtensions.kt`, `youtube/YtAccount.kt`. App lock key requires strong
  biometric or device credential (`security/LockKey.kt`, `setUserAuthenticationRequired(true)`).
  Tests: `online/OnlineSecretsTest.kt` (unit). Verified: unit + code.
- **STORAGE-2 (no leak through backups, logs, shared storage).** `android:allowBackup="false"` plus
  `backup_rules.xml` / `data_extraction_rules.xml` (`AndroidManifest.xml` 75–77). The in-app backup
  excludes `mt_updater`, `online_secrets`, `privacy`, WebView/Chromium, FCM and push state and the
  downloads index (`backup/Backup.kt` `excludedPrefs`, `excludedFiles`); sealed values could not be
  opened on another phone anyway. Release builds strip `Log.v/d/i` (`app/proguard-rules.pro`
  `-assumenosideeffects`); no `Log` call prints a token/password/secret (grep over `app/src/main/java`,
  0 hits). Crash reports stay on the phone and are shared only by the user through a non-exported
  FileProvider (`shared_files.xml`). Tests: `backup/BackupV2Test.kt` (unit), `BackupDeviceTest`
  (emulator). Gap: a backup file is plain JSON (library, history, settings) — it holds no secrets.

## MASVS-CRYPTO

- **CRYPTO-1 (strong, current crypto).** Our own data: AES-GCM 128-bit tag, Keystore keys, random IV
  from the Cipher (`SecretBox`, `LockKey`). SHA-256 for update and extension integrity
  (`update/ApkInstaller.kt`, `online/ExtensionManager.kt`). AES-CBC appears only where a stream format
  demands it (HLS AES-128 segments, `online/download/HlsDownloader.kt`; JS source crypto helpers,
  `sources/js/JsCrypto.kt`) and RC4 only inside third-party site extractors
  (`cloudstream3/extractors/Vidplay.kt`, `VidSrcTo.kt`) — decoding what a site serves, never
  protecting user data. `java.util.Random` is used only for shuffles (HomeMoods, HomeOnline).
  Verified: code.
- **CRYPTO-2 (key management).** Keys never leave the Keystore and are not backed up; a restored
  phone signs in again (`SecretBox` KDoc). No hard-coded keys: ADR-013 key classes, `ApkSecretsTest`
  fails the build if a class-3 secret (Trakt/Kitsu secret, TMDB key, updater token) is in the APK;
  `app/build.gradle.kts` refuses `MT_UPDATER_TOKEN` in release/preview builds. Verified: unit + CI.

## MASVS-AUTH

- **AUTH-1 (secure auth/authorisation protocols).** Third-party sign-in uses each service's OAuth
  (AniList, MAL with PKCE `mal_verifier`, Trakt, Simkl, Kitsu), tokens sealed as above; Trakt refresh
  tokens are single-use and refreshed under a lock (`tracking/Trakt.kt` `validToken`). Token hosts are
  HTTPS-only (`network/TokenHostsHttpsOnlyTest.kt`, unit). There is no Yumeiro account; the Cloud
  Worker keeps only a hash of each device token (`token_hash`, `server/src/index.ts`; `server/test/handlers.test.ts`).
- **AUTH-2 (local auth).** App lock and private mode use `BiometricPrompt` with a Keystore key bound
  to user authentication (`security/Privacy.kt`, `security/LockKey.kt`). Local only by design.
- **AUTH-3 (step-up for sensitive operations).** N/A: no payments or remote account changes.

## MASVS-NETWORK

- **NETWORK-1 (secure traffic).** `network_security_config.xml`: system CAs only (user CAs not
  trusted) plus ISRG Root X1 for Android 7 (`BundledRootTest`, unit). `cleartextTrafficPermitted`
  is **true** in the base config because users add IPTV, camera and LAN media server URLs that are
  plain HTTP; every service that carries a token or an update (GitHub, OpenSubtitles, SubDL,
  SubSource, Simkl, Last.fm, LRCLIB, Trakt, AniList, MAL, Kitsu, yumeiro.click) is pinned to
  `cleartextTrafficPermitted="false"` in a domain config, and the updater/subtitle clients refuse
  non-HTTPS URLs in code. DNS: DoH/DoT with resolver rotation; UDP replies accepted only from the
  resolver (X11-5, `DnsWireClientTest`). **Gap:** a user-entered plain-HTTP stream can be read or
  altered on the network; maintainer decision 2026-11-07 (`docs/security/AUDIT.md`, accepted risks).
- **NETWORK-2 (pinning where needed).** No certificate pinning. Updates are protected end to end
  instead: SHA-256 must match the digest published with the release, package name, a higher version
  code and the installed signing certificate are all checked before `PackageInstaller`
  (`update/ApkInstaller.kt`, `update/GitHubReleaseClient.kt`; `UpdaterLogicTest`, unit). Pinning
  GitHub would break on their certificate rotation; not proposed.

## MASVS-PLATFORM

- **PLATFORM-1 (IPC).** Exported: `MainActivity` (launcher, VIEW/SEND filters), `PlaybackService`
  and `VideoPlaybackService` (media3 session; system UI, Bluetooth and Android Auto must bind),
  `DownloadCompleteReceiver` (guarded by `SEND_DOWNLOAD_COMPLETED_INTENTS`, payload checked against
  our own download id). Everything else is `exported="false"` (alarm, package-replaced, install
  result, push services, widget, restart activity, FileProvider). Incoming VIEW/SEND links are
  filtered (`stream/IncomingMedia.kt`; `IncomingMediaGuardTest`, unit; H7b shared link, emulator).
- **PLATFORM-2 (WebViews).** `online/web/SafeWebView.harden`: no file or content access, no
  automatic windows, `X-Requested-With` suppressed; pop-ups dropped; renderer crashes contained
  (`SafeWebViewClient`). JavaScript is on only where a site needs it (sign-in, Cloudflare check,
  stream finder). Two pages get a JS bridge (`MTFinder`: `onMedia(url)`, `onDrm(system)`) in
  `ui/screens/stream/PageStreamFinderDialog.kt` and `online/web/HeadlessCapture.kt`; it can only
  report a URL into the capture list and a DRM system name. **Fixed in this review:**
  `HeadlessCapture.onDrm` stored the page's string as given (any length, no lock); it is now bounded
  to 40 safe characters under the capture lock, like the stream finder (code; CI compile).
- **PLATFORM-3 (UI).** Optional `FLAG_SECURE` for private mode (`security/Privacy.kt`). Third-party
  extension pop-ups: Show / Quiet / Block promotional (ADR-014; `ExtensionPopupsDeviceTest` p1–p8,
  emulator 43825449454).

## MASVS-CODE

- **CODE-1 (current platform).** Phone minSdk 28, TV minSdk 24 (Android TV boxes), targetSdk 37 (`app/build.gradle.kts`).
- **CODE-2 (updates).** Signed in-app updater (above); one constant release key, V2+V3 signing
  (`app/build.gradle.kts`). Upgrade data parity is tested from v1.8.0 (`upgrade-check.yml`, X10).
- **CODE-3 (no known-vulnerable dependencies).** Dependabot + dependency review in CI; OSV checks
  recorded in `docs/security/DEPENDENCIES.md` and X13-6. **Gap:** the TV build pins Jackson 2.13.5
  (14 advisories) because ≥ 2.15 cannot load on Android 7; exposure analysis and containment test in
  ADR-015 (`JacksonTvExposureTest`, unit). Phone Jackson 2.19.4: proposal X13-6 (owner condition).
- **CODE-4 (input validation).** JSON files read through `data/SafeJsonFile` (bad files kept as
  `.bad`, temp files used); regexes precompiled; extension calls wrapped (`ExtensionGuard`,
  `runCatching`). **By design:** extensions are arbitrary code; mitigations are the install-time
  scanner (`online/ExtensionScanner.kt`, `ExtensionScannerTest`), SHA-256 verification against the
  repository manifest (`ExtensionManager`), and loading only into our own class loaders.

## MASVS-RESILIENCE

- **RESILIENCE-1/2 (platform integrity, tamper).** `security/IntegrityGuard.kt` compares the running
  signature with the official certificate digest baked into the build; a re-signed copy gets a
  notice and no updater. No root, emulator, hook or debugger detection: the app is open source and
  such checks would block legitimate users without protecting a secret (owner policy). R8
  obfuscation and resource shrinking in release.
- **RESILIENCE-3/4 (static/dynamic analysis).** Not pursued (open source).

## MASVS-PRIVACY

- **PRIVACY-1 (minimise data).** No analytics: `firebase_analytics_collection_deactivated`, FCM
  auto-init off until the user opts in, BigQuery delivery metrics off (`AndroidManifest.xml`). Crash
  reports stay local. Permissions are runtime-requested media/notification permissions only.
- **PRIVACY-2 (no tracking identifiers).** FCM uses Firebase installation IDs only after opt-in; the
  Cloud Worker stores device tokens hashed (`token_hash`).
- **PRIVACY-3 (transparency).** Push channels are opt-in (FCM / UnifiedPush, `docs/PUSH-FCM.md`); YouTube
  sign-in is optional and the cookies are sealed and removed on sign-out (`YtAccount.signOut`).
- **PRIVACY-4 (user control).** Private mode (`security/Privacy.kt`), per-service sign-out, backup
  export/restore and reset (`backup/`).

## Changes and follow-ups from this write-up

| Item | Result |
|---|---|
| X7-4 `HeadlessCapture.onDrm` unbounded page string | fixed (this commit): bounded + locked |
| NETWORK-1 cleartext base config | accepted risk (user HTTP streams); recorded, no change |
| CODE-3 TV Jackson 2.13.5 | ADR-015 conditions; `JacksonTvExposureTest` corrected d14754d |
