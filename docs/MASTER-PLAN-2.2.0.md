# Yumeiro 2.2.0: master plan (one branch, one release)

Owner order 2026-11-22 ~22:30 +06: release as **2.2.0** tomorrow; "do everything"; one separate branch; do not wait
on CI or device runs after every item; test in batches, read the log once, fix what failed. This file is the plan
and the live progress list. Every agent reads it first, then docs/HANDOVER-LIVE.md.

## Rules for this work
- Branch: `release-2.2.0` (from r15-stability d0c5a1d2 + docs). All work lands here. PR release-2.2.0 -> main at the end.
- Version 2.2.0, code 20 (2.1.0 was never released; users go 2.0.0 -> 2.2.0, versionCode still grows, so no problem).
- Keep coding while a run is going. One CI per batch, not per item. Device tests in ONE run with all classes
  (device-check `-f test=A,B,C`), plus one TV run. Read the log once, fix every failure in one commit, rerun only
  the failed classes.
- Every item still gets a unit test (runs in the same CI, costs no extra run). No test is weakened, no limit raised.
- Secrets: never in git. Local copy /home/user/workspace/.secrets (outside the repo). App build keys are GitHub
  secrets (already set 2026-11-21). Worker keys are Cloudflare Worker secrets. gitleaks must stay green.
- Legal sources only, no 18+, no DRM / paywall bypass. Never copy code; Hikari (GPLv3) read only.
- Honest status: Done (run ID) / Coded, run pending / Blocked (reason) / Not checked (reason).

## Batches and status

| # | Batch | Items | Status |
|---|---|---|---|
| B0 | Setup | branch, this plan, version 2.2.0/20, docs renamed to 2.2.0 | In progress |
| B1 | Bugs | OWN-15 (locked profile: logo stays after PIN, reproduced 45039700220); failures from CI 45041431667 | Coded, run pending: OWN-15 fix (flag own15_gate_skips_logo: after the PIN gate the second logo is skipped; decide() and open() can no longer crash). CI 45041431667: 194 failures, all one cause (Robolectric android-all download IOException from Maven Central, no code failure); fix: CI caches ~/.m2/repository/org/robolectric |
| B2 | Owner decisions 4 + 6 | X13-6 Jackson 2.19.4 -> 2.18.11 on phone; SEC-1 Last.fm sign-in on (Worker secrets set from .secrets) | Jackson: already 2.18.11 in libs.versions.toml (run pending: ExtensionsDeviceTest in B7). Last.fm: Done 2026-11-22: LASTFM_API_KEY + LASTFM_SHARED_SECRET set as Worker secrets; POST api.yumeiro.click/v1/lastfm/sign now answers bad_params (was lastfm_off) |
| B3 | E2-ALL Yumeiro Live | first E2 publish to `streams`; one merged list (market lists + iptv-org index, adult groups dropped, deduped); 3-strike dead-stream remover; app ships Yumeiro Live as default with auto-update; failed-channel count report to the Worker | Coded, run pending: .github/scripts/build_live_all.py (iptv-org only, NSFW/blocklist/adult names/Xtream panel links dropped, 1 stream per channel, 3-strike state in live/state.json), tests test_build_live_all.py (6 pass locally), stream-sync.yml daily step (kill switch var YUMEIRO_LIVE_ALL=false); app: default playlist yumeiro_live (flag live_default), existing users get it once. Market lists NOT merged (some carry pirated sports: legal-only rule). E2-ALL-D failed-channel report: To do |
| B4 | E1 Yumeiro Hub rest | E1-A catalog from the Worker (TMDB key stays on the Worker) into the one "Yumeiro" source; E1-B resolver fan-out over installed sources; E1-E first open ready; E1-D ranking; E1-C recipes only if time (honest status) | E1-A Worker: Done 2026-11-22 (deployed version 08594cef; /v1/hub/shelves, /catalog, /search, /title answer 200 from TMDB; TMDB_TOKEN is a Worker secret; 5 new Worker tests, 45/45 pass). App: Coded, run pending: sources/hub/YumeiroCatalog.kt (catalog part first in the one Yumeiro source, 18 shelves; E1-B fan-out over installed sources, title + year + type match, 30 s per source, 45 s total; E1-D answer ranking; E1-E on by default, flag hub_catalog), test own/E1CatalogTest. E1-C recipes: Not in 2.2.0 (no time to write and verify legal recipes) |
| B5 | Vega providers | research the format first; build a runner only if it can be done legally and in time, else Not in 2.2.0 with the reason | Done before 2.2.0: Vega providers already import and run (ADR-047; device 44985807462 Vega 3/3 on api36 + api33-lowram). No new code. Left: a real Vega repo on a real phone (owner test OMT-75) |
| B6 | UPG-1 | upgrade-check.yml: 2.0.0 -> 2.2.0 phone and TV | To do |
| B7 | One test pass | one CI (review_apk=true) + one device run (all touched classes) + one TV run; fix; rerun failed only | To do |
| B8 | Release | remove debug code, CHANGELOG, release kit, PR -> main (merge commit), release.yml, APK sha256 check, delete old ab/* branches, owner message | To do |

## Owner-only
- OMT-22 Firebase Test Lab: the service account still gets 403 (checked 2026-11-22 16:10 UTC: "Not authorized for
  project", and it may not enable APIs). Owner: enable Cloud Testing API + Cloud Tool Results API and give
  firebase-adminsdk-fbsvc@yumeiro-ab29e.iam.gserviceaccount.com the role "Firebase Test Lab Admin". Not a blocker.
- Trakt login needs TRAKT_CLIENT_SECRET (not given). Trakt sign-in stays as it is until then.
- Real-phone tests listed in docs/OPEN-ITEMS-2.1.0.md (not blockers).
