# Release summary: Yumeiro 1.9.0 release candidate

Status: release candidate for 1.9.0, waiting for the owner's review and "go". It is not called stable. Tested once
per feature (P1: cleared data and a cold start before every class) on four emulator targets; the second, warm-start
pass (P2) was dropped on the owner's decision of 2026-11-20 to release in time. Nothing is merged, published or
deployed.

## What this release is

Yumeiro 1.9.0 (version code 17), branch `r15-stability`, built and signed by CI as a review APK for the owner. It
was planned as v1.8.2 and ships as 1.9.0 because it adds features (profiles, private downloads, script repos,
Telegram and share-link playback, DNS fallback, own proxy). Phone and TV APKs come from the same commit.

## What changed since 1.8.1

The user-facing list is CHANGELOG.md, section "v1.9.0: yours, and fast" (profiles and privacy, look and player,
faster start, Security (SEC-LFM), sources and network, fixes). Final-test fixes in the app itself (not only tests):

- FINAL-30 (ADR-050): Android TV boxes without an AC-3 / E-AC-3 decoder play those audio tracks through FFmpeg
  instead of failing with DECODER_INIT_FAILED (tv-api24, pass-after 44664839512).
- FINAL-17 / FINAL-18 / FINAL-20 / FINAL-13 / FINAL-16 (earlier in the window): full-disk WorkManager no longer
  crashes, manga metadata refresh off the main thread, slow probes never read as a hang, an extension's quiet card
  comes back after an app message, hold-for-2x while the finger is still.

## What was tested

- Device tests: every androidTest class, P1 once, on api33-lowram (2 GB, 192 MB heap), api36 (Pixel 6),
  tv-api34 and tv-api24, through `device-check.yml` (CI emulators; labels: emulator / TV emulator, no real
  hardware). Results per feature with run IDs: docs/MASTER-CHECKLIST-FINAL.md; raw lines per run:
  docs/evidence/final/<run>.txt; lane log: docs/evidence/final/lanes.log.
- Unit tests, lint and the build: `ci.yml` on the review-APK commit (see Review APK).
- Server: docs/SERVER-TEST-FINAL.md. Code audit (checks only): docs/CODE-AUDIT-FINAL.md.
- Findings of the final test and their fixes: docs/HANDOVER-FINAL-LANES.md (FINAL-16c … FINAL-34), each with a
  fail-before run and a pass-after run where one finished.

## What is queued

- Owner tests that need real hardware, accounts, a home network or the Worker: docs/OWNER-MANUAL-TESTS.md
  (OMT-14 … OMT-56) and the community programme docs/USER-TEST-PROGRAM.md.
- The warm-start pass (P2) for every class (dropped for time, owner's decision).
- Everything in docs/UNVERIFIED-QUEUE.md.

## Known to be imperfect

- YouTube's "Sign in to confirm you're not a bot" wall on the CI network blocks YouTube watch and endless YouTube
  Music checks there (MusicEndlessNetwork, YtChannelUi on tv-api24, PlayerSwitch c): Blocked, OMT-29 on a home
  network.
- TV-FP-1: FeedPositionDeviceTest live / YouTube on tv-api24 is a known test fault (same on old code
  44438504785); the feature itself passes on tv-api34 and phones.
- tv-api24 (Android 7 TV) cannot turn its Ethernet off, so network-drop checks are skipped there with that reason
  (NetworkState, YmPagingNetwork); phones run them.
- Phone with Layout = TV in portrait is not verified (FINAL-31: the BUG-5 TV checks need a landscape TV window).
- LIC-1: NOTICE does not yet name the Google SDKs linked into the APK; owner's wording needed.
- SEC-LFM and F-AIR need the Worker deploy that comes with "go"; until then built-in Last.fm sign-in waits.
## Security checks (§9)

| Check | How | Result | Date |
|---|---|---|---|
| Secret scan, full history | gitleaks 8.28.0 (CI's pinned version and SHA-256), `git --log-opts=--all`, all 1172 commits (re-run 2026-11-20 03:17 UTC on 1a22844), `.gitleaks.toml` | No leaks. Found 4 hits first: a fake test signature in two SEC-LFM test mocks (not a credential); mocks changed to a low-entropy fake, the two commits allow-listed by SHA and path only (a71cf85) | 2026-11-20 |
| Secret scan, working tree | gitleaks `dir` | 7 hits, all in the git-ignored `.env` and `secrets/firebase-admin.json` (local only, never committed: checked against every commit) | 2026-11-19 |
| Secrets in the APK | `ApkSecretsTest` (BuildConfig fields) + string scan of the review APK | SEC-LFM: the Last.fm shared secret was in BuildConfig; removed (b57e155, ADR-048). Trakt client secret, TMDB key, Kitsu id/secret already out. Cloudflare token and Firebase service account are server-only. APK string scan: pending the review APK | 2026-11-19 |
| Dependencies | Dependabot alerts (enabled), `npm audit` in server/ | 0 open Dependabot alerts (5 fixed earlier); npm audit 0 vulnerabilities | 2026-11-19 |
| Licences | NOTICE vs. `implementation(...)` list | LIC-1 (owner decision): NOTICE lists the vendored and open-source runtime libraries but not the Google SDKs linked into the APK: Firebase Messaging (Apache-2.0, pulls in Google Play services), Cast framework and ML Kit text recognition / translation (Google's own terms, closed binaries). GPL-3.0 projects that link closed Google SDKs usually state it; this needs the owner's choice of wording or a build without them | 2026-11-19 |
| Virus scan | VirusTotal (or equivalent) on the review APK | pending the review APK | |
| APK integrity | apksigner verify, cert, ABIs, R8 (X10-1) | pending the review APK | |
| CI on the exact head | ci.yml | 44610265729 success on 6f11509; to repeat on the review-APK commit | 2026-11-20 |

## Review APK
## Install and test guide for the owner

The review APK is signed with the release certificate (checked in CI against
`.github/release-signing-cert.sha256`), so it installs over 1.8.x on the same phone without losing data.
Version: 1.9.0 (version code 17). Files: `yumeiro-1.9.0-<hash>-review.apk` (phone),
`yumeiro-tv-1.9.0-<hash>-review.apk` (TV), `SHA256SUMS`, `INTEGRITY.txt` (artifact `yumeiro-review-apk` of the
CI run in the Review APK section).

### Before installing

1. In the installed 1.8.x: Settings → Backup & restore → Back up now, and copy the file off the phone. This is the
   rollback point.
2. Check the download: `sha256sum -c SHA256SUMS` (Linux/macOS) or `certutil -hashfile <apk> SHA256`
   (Windows) must match the line for the APK.

### Install

- Phone: open the APK on the phone and allow "Install unknown apps" for the file manager, or
  `adb install -r yumeiro-1.9.0-<hash>-review.apk`.
- TV: `adb connect <tv-ip>` then `adb install -r yumeiro-tv-1.9.0-<hash>-review.apk`.
- Android refuses the update with "App not installed" only if the installed copy was signed with a
  different key (for example a debug or preview build); that copy has to be backed up and uninstalled first.

### Test

1. Work through docs/OWNER-MANUAL-TESTS.md (owner rows first: they need real hardware, accounts or the
   Worker). Each row says what to do, what should happen, and where to write the result.
2. Spot-check the features in "What changed since 1.8.1" with your own sources and accounts.
3. Leave the app running for a day of normal use (music, a video, a download, an airing alert).

### Report

- Settings → Crash reports: open a report and tap Share to send the whole file (read it first; it holds
  the stack trace and device details).
- Write findings as GitHub issues on siamform/yumeiro with: device, Android version, what you did, what you
  expected, what happened, and the shared crash report if any. Or tell the agent; each finding gets an ID,
  a root-cause fix with a regression test and a new review APK.

### Roll back

Android does not install an older version over a newer one. To go back to 1.8.1:

1. Settings → Backup & restore → Back up now in 1.9.0 (keeps anything new since the update).
2. Uninstall Yumeiro, install the 1.8.1 APK from the v1.8.1 GitHub release.
3. Settings → Backup & restore → Choose a backup file, pick the file from step 1 (or the one made before installing). Both versions write and read
   backup format 2 (`Backup.FORMAT` is 2 in v1.8.1 and in 1.9.0), so 1.8.1 accepts the file.

Nothing here publishes, merges or deploys: the store listing, `main`, the Worker and the website stay as they
are until the owner says "go".
