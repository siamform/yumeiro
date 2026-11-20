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

- FINAL-39 / 39b (ADR-051): on Android TV, opening a sheet with a search box (Yumix "Show videos from",
  extension and market filters) no longer pops the full-screen keyboard by itself; the keyboard opens when OK is
  pressed on the box, and one Back closes the sheet (tv-api34 and tv-api24, fail-before 44666813073 / 44669263390).
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
- Findings of the final test and their fixes: docs/HANDOVER-FINAL-LANES.md (FINAL-16c … FINAL-44), each with a
  fail-before run and a pass-after run where one finished.

## What is queued

- Owner tests that need real hardware, accounts, a home network or the Worker: docs/OWNER-MANUAL-TESTS.md
  (OMT-14 … OMT-61) and the community programme docs/USER-TEST-PROGRAM.md.
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
- FINAL-35 (open, not reproduced): once, on tv-api24 (run 44663218206, StabilityStress pagerFocusAndScrollStress), the
  Java heap grew from 61 to 479 of 512 MB in about 90 s while the drama source's server (onshort.net) answered 503
  during feed paging; Compose never went idle and the next broadcast hit an ANR. The same class passed on the same
  app code in 44666637483 (peak heap 94 MB, no ANR) and earlier TV runs; a further tv-api24 run on the final head is
  listed in docs/HANDOVER-FINAL-LANES.md. No heap dump was captured, so the cause is not known. Owner check: OMT-57.
- Android TV's permission dialog keeps asking after every "Don't allow" (no "don't ask again"), so the "blocked →
  Open settings" path is verified on phones only. In three tv-api34 runs (44662694767, 44666813073, 44669616027) the
  app's "Notifications not allowed. Tap again…" message was not seen within 10 s after the second "Don't allow"; in
  the run with diagnostics (44672818277, after the FINAL-42 main-thread fix) it showed after each of 5 denials. The
  cause of the three misses is not proven (FINAL-43, OMT-58).
- FINAL-45 (open, not reproduced): once, on api33-lowram (44665076936, UpscaleFreeze 7d), the main thread waited
  2781 ms in a window-manager binder call (IWindowManager.getWindowInsets) while the player opened its More menu;
  the same class passed 3/3 there in 44673996183 and on api36 in both runs.
- FINAL-46: a release build made without the Last.fm, LRCLIB, OpenSubtitles and FCM build values had a video
  player that Android refused to load (VerifyError, crash on play). The review APKs were built with all values and
  pass; why R8 does this was not reduced (queue FINAL-46b). Any rebuild must pass the upgrade check before it is
  published. On TV the "your data is kept" comparison could not run on the emulator (OMT-61).
- CI: Gradle's configuration cache twice failed to store `processNavigationResources` state in "Build APKs" (runs
  44667182892, 44668053699; no test ran, both re-run). An infrastructure fault, not an app or test result.
- The Timestamp lines in the bodies of 099689d, a5bc4da and d91f052 are 10–25 min ahead of the real commit time
  (the commit dates are right); 039ca61f says "06:1x UTC", the commit was made at 05:58 UTC. b84b934's body says "disk-full IO", which is not what that commit fixed.

## Security checks (§9)

| Check | How | Result | Date |
|---|---|---|---|
| Secret scan, full history | gitleaks 8.28.0 (CI's pinned version and SHA-256), full history, `.gitleaks.toml` | No leaks: CI job "Secret scan (gitleaks)" 136084973320 in run 44675081410 (1383 commits, fetch-depth 0, 2026-11-20 04:32 UTC); local re-run on 88e2208 with `--log-opts=--all` (1390 commits scanned, all refs) no leaks. `git log --all -S` for every pasted credential value: 0 commits. Earlier: 4 hits on a fake test signature in two SEC-LFM test mocks (not a credential), mocks changed and the two commits allow-listed by SHA and path only (a71cf85) | 2026-11-20 |
| Secret scan, working tree | gitleaks `dir` | 7 hits, all in the git-ignored `.env` and `secrets/firebase-admin.json` (local only, never committed: checked against every commit) | 2026-11-19 |
| Secrets in the APK | `ApkSecretsTest` (BuildConfig fields) + byte scan of both review APKs (raw, inflated zip entries and dex) for every credential value | Not in either APK: Last.fm shared secret, Cloudflare API token, Firebase service-account key id, client e-mail, client id and private key, TMDB key. Present as designed (public client identifiers that every user's app sends): Trakt/Simkl client IDs, OpenSubtitles API key, Last.fm API key, LRCLIB app id, Firebase project id/number, app id and Android API key. SEC-LFM: the shared secret left BuildConfig in b57e155 (ADR-048) and is injected only into the Worker from CI | 2026-11-20 |
| Dependencies | Dependabot alerts (enabled), `npm audit` in server/ | 0 open Dependabot alerts (5 fixed earlier); npm audit 0 vulnerabilities | 2026-11-19 |
| Licences | NOTICE vs. `implementation(...)` list | LIC-1 (owner decision): NOTICE lists the vendored and open-source runtime libraries but not the Google SDKs linked into the APK: Firebase Messaging (Apache-2.0, pulls in Google Play services), Cast framework and ML Kit text recognition / translation (Google's own terms, closed binaries). GPL-3.0 projects that link closed Google SDKs usually state it; this needs the owner's choice of wording or a build without them | 2026-11-19 |
| Virus scan | ClamAV 1.5.4 (3,628,199 signatures, databases updated 2026-11-20) on both review APKs. VirusTotal was not used: uploading would make the unpublished APK available to third parties, and there is no API key | 0 infected (phone and TV) | 2026-11-20 |
| APK integrity | apksigner verify in CI (INTEGRITY.txt in the artifact) + local androguard 4.1.4 | Signed with the release certificate SHA-256 2ecdfd04…413f6b = `.github/release-signing-cert.sha256` (CI gate + v2 and v3 blocks checked locally, both APKs). Phone: v3 verified (minSdk 28), TV: v2 + v3 (minSdk 24). versionCode 17, versionName 1.9.0, targetSdk 37. ABIs arm64-v8a, armeabi-v7a, x86, x86_64. R8 on: 6 dex files, mapping.txt for mobileRelease and tvRelease | 2026-11-20 |
| CI on the review-APK commit | ci.yml `review_apk=true` | 44675081410 success on 8cef8a6: Build, unit tests & lint (job 136084973118), gitleaks (136084973320), review APK (136096382496) | 2026-11-20 |

## Review APK

Built and signed by CI, not published (artifact only, kept 14 days).

- CI run: [44675081410](https://github.com/siamform/yumeiro/actions/runs/44675081410) (ci.yml, `review_apk=true`), commit
  8cef8a6. App code is the same as df4ba9d; commits after 8cef8a6 change only docs, images and device tests.
- Artifact: `yumeiro-review-apk` (both APKs, SHA256SUMS.txt, INTEGRITY.txt).

| APK | SHA-256 |
|---|---|
| yumeiro-1.9.0-8cef8a6-review.apk (phone, 131.6 MB) | d890e8ab1d950a24b18e1c926eb1326a687a6325b6ac4aa4868905c87852f877 |
| yumeiro-tv-1.9.0-8cef8a6-review.apk (TV, 96.2 MB) | df3ec66c503b9cdc1ee9b39bcef0221b605cf4a3b5a6595f278b9b92d3d92204 |

Install over 1.8.x (upgrade check from the published v1.8.1 to these exact APKs, downloaded from the artifact and
checked against SHA256SUMS):

- Phone, api33 emulator, [44683540047](https://github.com/siamform/yumeiro/actions/runs/44683540047): same
  certificate on both, install over 1.8.1 Success, 0 files or preferences lost (4 values changed by the app itself:
  FCM token app version, push fingerprint and sync time, updater check time), no welcome guide again, the clip
  played in 1.8.1 resumed from 50.2 s, 0 FATAL/ANR, 0 ART verifier rejections.
- TV, android-tv api34 emulator, [44684601986](https://github.com/siamform/yumeiro/actions/runs/44684601986):
  install over TV 1.8.1 Success, the 1.9.0 player plays the clip, 0 FATAL/ANR, 0 verifier rejections. The data
  comparison is Blocked: the TV emulator image gives no root, so its app data cannot be read (OMT-61).
- FINAL-46: the first upgrade run (44681019595) built its own APK with fewer build values than the release and
  that build's video player was rejected by ART (VerifyError); the check now uses the release inputs, fails on
  any verifier rejection and can install the exact review APK. The review APKs are not affected (above).

## Final findings and numbers

- FINAL-42 (app, fixed in df4ba9d): app messages are changed on the main thread only (a worker-thread error broke a
  Compose frame and crashed the process). Pass-after: tv-api34 RetryActions 5/5 (44672818277), phones (44673996183).
  Fail-before on the old code: [44680944481](https://github.com/siamform/yumeiro/actions/runs/44680944481) (only AppMessagesMainThreadTest fails: "changed off the main thread").
- FINAL-43: closed as Blocked (not reproduced after FINAL-42; TV dialog never offers "don't ask again"), OMT-58.
- FINAL-44: test fault on tv-api34 (MiniPlayer swipe under the TV rail, MusicAccess TV tab tag), fixed in 0687e72,
  Pass in 44674391443.
- FINAL-45 (open, not reproduced): one 2.8 s binder stall in the window manager on api33-lowram (44665076936);
  3/3 Pass in 44673996183.
- FINAL-46: see Review APK (upgrade check harness fixed; review APKs clean on phone and TV).
- FINAL-35, third run: tv-api24 StabilityStress peak heap 95/512 MB, no ANR (44670841174). Still not reproduced, OMT-57.
- Checklist (docs/MASTER-CHECKLIST-FINAL.md): 0 Fail, 0 P1 cell Not run; every P1 cell is Pass with run IDs or
  Blocked with a reason and a queue item; every P2 cell is "Not run (owner's decision 2026-11-20: P1 only)".

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
