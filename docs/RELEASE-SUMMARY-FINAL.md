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
| CI on the exact head | ci.yml | pending | |

## Review APK
## Install and test guide for the owner
