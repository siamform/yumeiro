# Extension Market curation (B10)

How Yumeiro decides which CloudStream extensions the in-app Extension Market lists, how each file is checked, and how
anyone can propose a change. Every decision is based on evidence the workflow records; nothing is listed or removed
by hand without a written reason in this repository.

Files:
- `.github/extensions/sources.json` - curated repositories, seeds and the block list (this is the only hand-edited list).
- `.github/scripts/build_extension_market.py` - discovery, ranking, mirroring and the health check (runs every 3 days,
  `.github/workflows/extension-market.yml`).
- `.github/scripts/extension_vetting.py` - the file checks below. Tests: `.github/scripts/test_extension_vetting.py`.
- `.github/scripts/check_extension_health.py <index.json|URL>` - the same checks on a published index (a report).
- Published evidence on the `extensions` branch: `index.json` (what the app shows), `summary.md` (top 60 and the
  removal counts), `removed.json` (every removed file, with its kind and reason).

## Tiers
| Tier | Where it comes from | How it is installed |
|---|---|---|
| curated | `repositories` in sources.json, reviewed by a maintainer | file mirrored on the `extensions` branch, hash enforced |
| community | CloudStream's community list (recloudstream/cs-repos) | from the author's live file |
| discovered | GitHub search (topics, keywords) and `seeds` | from the author's live file |

The app labels the tier and shows its own on-device safety report (`ExtensionScanner`, `ExtensionReview`) before a
tapped install, so the market checks and the phone checks are two separate layers.

## Listing criteria (all tiers)
A repository is read only when it is a public CloudStream `repo.json` with reachable plugin lists. Discovered
repositories are skipped when archived, disabled, not pushed for 900 days, or a fork with fewer than 2 stars. The
market keeps at most 250 extensions per repository. NSFW extensions are never listed. Extensions made for another
app (known foreign plugin classes) get a "won't load" note and rank last.

## Health check and security vetting (every file, every run)
Each listed file is downloaded and read, never run. It leaves the market when it is:

| Kind | Rule | Why |
|---|---|---|
| dead | download fails (HTTP error such as 404, Codeberg's 403 "not a CDN", timeout, empty), or the author marks it status 0 | a user could not install it |
| broken | not a zip, no `classes.dex`, no `manifest.json` or no `pluginClassName`, a dex without the dex header, a dex over 40 MB, or a curated file whose hash does not match | it cannot load |
| unsafe | native libraries (`.so`), nested `.apk`/`.jar`/extra `.dex` payloads, or class loaders / `ProcessBuilder` in its code | it runs code the market can't inspect |
| hostile | SMS, contacts, device accounts, messages or device-admin APIs in its code | a video or manga source never needs them |

Live community/discovered files are often rebuilt after their listing, so their listed hash is not enforced (the app
does the same), but the live file itself must pass. If more than 40% of all files are unreachable in one run the
workflow stops and the previous index stays published, so a network outage on the runner can't empty the market.
A removed extension returns by itself on the next run once its file passes again.

Evidence on 2026-11-28 (published index of 2026-11-26, 2601 files): 2216 passed; 314 downloads failed (262 HTTP 404,
52 Codeberg 403), 68 were marked down by their authors, 2 load more code at run time (BaseDexClassLoader) and 1 ships
native libraries. Those 385 files were still listed before B10.

## Ranking
Stars of the repository, days since its last push, the extension's status and version, and how many repositories
carry the same extension (the best copy is shown, the others are grouped under it). Curated copies win a tie.

## Proposing a change
1. Add a repository: open a pull request adding it to `repositories` in sources.json with a `note`, and in the pull
   request say: the license, who maintains it, the last release, and that its extensions install and play in
   Yumeiro (a short screen recording or the log of one install). A maintainer merges it after checking the same.
2. Block a repository: add `{"repo": "owner/name", "reason": "..."}` to `blocked` with the evidence (link to the
   issue, the removed.json entry, or the file and what it does). Plain `"owner/name"` entries still work but new
   ones must carry a reason.
3. Report one extension: open an issue with the extension name, repository and what goes wrong. If the health check
   can detect it, extend `extension_vetting.py` and its tests rather than blocking by hand.
4. Run the report yourself: `python3 .github/scripts/check_extension_health.py
   https://raw.githubusercontent.com/siamform/yumeiro/extensions/index.json report.json`.
