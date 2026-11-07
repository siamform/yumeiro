# Secrets scan (Phase 1)

Date: 2026-11-07. Tool: gitleaks 8.28.0, default rules, `--redact` (no values were printed).
Scope: all 67 commits on every remote branch (`git log --all`) plus the working tree of `main`.

```bash
gitleaks git --log-opts="--all" --redact -c .gitleaks.toml .
gitleaks dir --redact -c .gitleaks.toml .
```

## Results

| # | Finding | File | Commit | Severity | Action |
|---|---------|------|--------|----------|--------|
| 1 | 1,528 `generic-api-key` + 4 `jwt` matches | `playlists/*.m3u` on branch `market` | `b031eef9` (branch tip, single commit) | Info | None. These are query tokens inside **third-party** public IPTV URLs that the market workflow mirrors verbatim. They are not Yumeiro credentials and cannot be rotated by us. Allow-listed in `.gitleaks.toml` by path only. |
| 2 | 117 `generic-api-key` matches | `playlists/*.m3u` on branch `streams` | `86094c97` (branch tip, single commit) | Info | Same as #1. |
| 3 | Keystores (`*.jks`, `*.keystore`, `*.p12`), `keystore.properties`, `.env`, `google-services.json`, `local.properties` | never committed (checked every added path in history) | - | - | None. Only `keystore.properties.example` (fake values) exists. |
| 4 | Passwords / tokens / admin URLs in Gradle, Kotlin, workflows | none found | - | - | None. Signing values come only from GitHub Secrets / env. |
| 5 | Updater token mechanism (`MT_UPDATER_TOKEN` -> `BuildConfig.UPDATER_TOKEN_BYTES`) | `app/build.gradle.kts:50`, `:76-77`; `update/UpdateConfig.kt:25-29` | - | Medium (latent) | The secret is **not set** today, so no token ships. If it is ever set, the XOR obfuscation is trivially reversible and the token is public to anyone with the APK. Keep it unset while the repo is public (see `SECURITY.md`). |

Repository secrets that exist (names only): `MT_KEYSTORE_BASE64`, `MT_KEYSTORE_PASSWORD`, `MT_KEY_ALIAS`,
`MT_KEY_PASSWORD`. Nothing needs rotating as a result of this scan.

## History rewrite

Not needed: no Yumeiro secret was ever committed to any branch. `market`, `streams` and `extensions`
are single-commit branches that the bots force-push every run, so old mirror content is already gone
from branch history (GitHub may keep unreachable objects until its own GC).

If a real secret is ever committed, **rotate it first**, then (maintainer only, after a backup):

```bash
git clone --mirror https://github.com/siamform/yumeiro.git yumeiro-mirror && cd yumeiro-mirror
# remove a file everywhere
git filter-repo --invert-paths --path path/to/leaked.file
# or replace a literal value everywhere (one value per line in replacements.txt: VALUE==>REDACTED)
git filter-repo --replace-text ../replacements.txt
git push --force --mirror
```

Then ask GitHub Support to purge cached views and unreachable objects, and tell collaborators to re-clone.
