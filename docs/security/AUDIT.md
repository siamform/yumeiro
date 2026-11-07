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
