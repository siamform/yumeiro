# Release summary: Yumeiro 1.9.0 release candidate

Status: in preparation. This is the release candidate for 1.9.0, tested on three targets across two passes each
(when the final test completes), with the remaining items documented. It is not called stable.

## What this release is
## What changed since 1.8.1
## What was tested
## What is queued
## Known to be imperfect
## Security checks (§9)

| Check | How | Result | Date |
|---|---|---|---|
| Secret scan, full history | gitleaks 8.28.0 (CI's pinned version and SHA-256), `git --log-opts=--all`, all 1087 commits, `.gitleaks.toml` | No leaks. Found 4 hits first: a fake test signature in two SEC-LFM test mocks (not a credential); mocks changed to a low-entropy fake, the two commits allow-listed by SHA and path only (a71cf85) | 2026-11-19 |
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
