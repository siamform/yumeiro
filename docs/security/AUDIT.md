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
| same | `app/src/main/java/eu/kanade/**` | Vendored Tachiyomi/Aniyomi source API (Apache-2.0). Its signatures are the extension ABI and can't change. |
| `java/android/missing-certificate-pinning` | `tracking/Kitsu.kt`, `tracking/Trakt.kt`, `sources/servers/PlexProvider.kt`, `notify/PushWake.kt`, `youtube/YtAccount.kt` | Kitsu and Trakt rotate certificates without notice (a pin would break sign-in for every user); Plex servers are the user's own hosts with arbitrary certificates; the UnifiedPush server is whatever the user's push app uses (ntfy.sh or self-hosted) and only receives empty "wake" messages; the pasted-cookie check goes to youtube.com, whose certificates Google rotates. System trust + HTTPS-only applies. |
| `java/android/websettings-javascript-enabled` | `ui/screens/settings/YouTubeAccountScreen.kt` | Google's own sign-in page needs JavaScript. The WebView loads only Google/YouTube sign-in, with file and content access off and non-http schemes blocked (`SafeWebView.harden`). Content-access alerts on the same WebView were fixed (explicit `allowFileAccess`/`allowContentAccess = false`). |

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
