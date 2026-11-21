# Yumeiro: start here (universal handover and resume prompt for any new agent)

This is the one document a new agent reads first. It holds the resume prompt, every rule the owner has set, how
the work is done, where things are, and what is next. Other docs hold the details; this one tells you which.
Trust the repo and CI run IDs over any summary, including this one. Last updated 2026-11-21.

---

## 1. Resume prompt (paste this to a new agent)

> You are taking over Yumeiro (Android phone + Android TV media app), repo `siamform/yumeiro`, working branch
> `r15-stability`. You are release engineer, QA lead and systems architect. Read `docs/AGENT-START-HERE.md` in
> full, then `docs/R15-STATUS.md` line 3 ("Resume here"), `docs/BUGS-AND-FIXES-2.0.0.md` (the ledger),
> `docs/MAJOR-BUGS-AND-FEATURES-2.x.md` (the must-do list) and the bottom sections of
> `docs/HANDOVER-NEXT-AGENT-2026-11-20.md`. Follow every rule in section 3 of AGENT-START-HERE.md. Do not claim
> what you have not verified with a run ID. Do not reopen owner decisions (section 4). Do not release unless the
> owner says so. Continue with the first open item in section 7. When the owner says "add", write it into the repo
> docs and push at once.

---

## 2. The product and the owner

- Yumeiro: Android media app (phone + TV flavours): local video/music, online extensions (CloudStream-compatible
  runtime, Nuvio/SkyStream/Vega detection), Live TV/IPTV, YouTube/YouTube Music (login-free extractor), manga,
  Telegram, TeraBox/DiskWala/torrent links, profiles, vault, sync. Package `dev.asfakul.yumeiro`.
- Owner: Asfakul Siam (GitHub `siamform`), Bangladesh, time zone GMT+6. Writes short messages, often with typos;
  read for intent. Wants it stable, no crashes, everything tested, and clear docs. Communities report bugs to him.
- The README keeps the "made in Bangladesh" line.
- Latest release: v2.0.0 (2026-11-20, main e47f450f). version.properties is still 2.0.0 / 18.
- Next release: 2.1.0, only when the owner says. Owner, 2026-11-21: no release for now; fix, implement and test
  everything first.

---

## 3. Rules (all of them, from the owner)

### Truth and evidence
- Do not claim what you have not verified. "Pass" needs a run ID.
- Every test cell or checklist row is one of: Pass (with a run ID), Fail (with a finding ID), Blocked (with a
  written reason), Not run (with a written reason). "Partial" is not allowed.
- Do not weaken an assertion or raise a limit to go green (e.g. the GATE-1 700 ms limit stays).
- Do not generate an image for a feature that is queued or does not work as shown.

### How a fix is made
- Root cause, not symptom. Measure first (logs, device stacks, real network answers), then fix.
- A regression test that fails before and passes after. To prove "fails before": push a branch `ab/<id>-before`
  holding the new test (plus only what it needs to compile) on the old code, and run CI there. Record both run IDs.
- One logical change per commit. Commit body sections: Cause / Fix / Tests / Evidence / Timestamp / Ledger.
- Push after every job. In the same push, update the ledger (`docs/BUGS-AND-FIXES-2.0.0.md`), the must-do list
  (`docs/MAJOR-BUGS-AND-FEATURES-2.x.md`) and "Resume here" (`docs/R15-STATUS.md` line 3).
- When the owner says "add" (a bug, a feature, a request): write it into the repo docs and push immediately. Agents
  can run out of limit at any time; nothing may live only in chat.

### Git identity and attribution
- Author and committer are the owner. Before committing:
  ```
  export GIT_AUTHOR_NAME="Asfakul Siam" GIT_AUTHOR_EMAIL="71690568+siamform@users.noreply.github.com"
  export GIT_COMMITTER_NAME="Asfakul Siam" GIT_COMMITTER_EMAIL="71690568+siamform@users.noreply.github.com"
  ```
- Every commit message ends with exactly one trailer line: `Co-authored-by: Perplexity <noreply@perplexity.ai>`
  (owner confirmed 2026-11-21: "oky use co author"; the platform requires it). Author and committer stay the owner.
  Check each commit with `git log -1 --format=fuller` before pushing.
- PR descriptions and GitHub comments end with the "Created with Perplexity" footer line linking the agent's task
  (never in replies to the owner).
- Never force-push. Run `gh` from inside the repo directory.

### CI and devices
- No JDK locally: compile and test only through CI.
- CI: `gh workflow run ci.yml --ref <branch> -f review_apk=true` (about 40 min; `review_apk=false` for fail-before
  branches). ci.yml runs on pull_request and workflow_dispatch only, not on push.
- CI concurrency is per ref with cancel-in-progress: a second dispatch on the same branch cancels the first. Wait.
- Device tests: `gh workflow run device-check.yml --ref <branch> -f test=<comma-separated classes>` (about 20 min,
  emulators api36 and api33-lowram). At most two device workflows at once. Do not stack.
- REPEAT=1 (owner decision). Data is kept between classes when REPEAT=1.
- Wait with `gh run watch <id> --interval 120` in the foreground. No scheduled workflows, no GitHub-API polling
  loops, no secrets in logs.
- Read results: `gh run download <id> -R siamform/yumeiro -D /tmp/gN`, then `rg --no-filename` over the JUnit XML
  (`rg -h` prints help, not "no filename"). `gh run view <id> --log-failed | rg "FAILED$|tests completed"`.
- Cancelled runs go in `docs/evidence/final/CANCELLED-RUNS.md` with the reason.
- Warning: `release.yml` runs on every push to `main` that touches the app. Merging to main = releasing. Only when
  the owner says.

### Secrets
- Never print or commit a secret. gitleaks must stay green.
- Repo secrets (names only): ANILIST_CLIENT_ID, MAL_CLIENT_ID, TRAKT_CLIENT_ID, SIMKL_CLIENT_ID, KITSU_*,
  LASTFM_API_KEY, LASTFM_SHARED_SECRET, LRCLIB_APP_ID, OPENSUBTITLES_API_KEY, TMDB_API_KEY, MT_KEYSTORE_* /
  MT_KEY_* (signing), YUMEIRO_FCM_*, DATA_BRANCH_DEPLOY_KEY, YUMEIRO_TG_API_ID, YUMEIRO_TG_API_HASH,
  CLOUDFLARE_API_TOKEN, FIREBASE_SERVICE_ACCOUNT_JSON (both added 2026-11-21). Not provided yet: TRAKT_CLIENT_SECRET.
- Checked 2026-11-21 from the sandbox (values never printed): Cloudflare token `active`; TMDB, Last.fm, Trakt,
  OpenSubtitles answer 200; Simkl answers 200 with the `simkl-api-key` header (its `/search` needs a user token, the
  app does not call it: Upcoming reads `data.simkl.in`); the Firebase service account gets an OAuth token and an FCM
  `validate_only` send returns 200 (no message sent).
- Build reads keys through `optionalKey(prop, env)` in `app/build.gradle.kts`; blank = feature hidden. Workflows
  pass them in release.yml, review-apk.yml, upgrade-check.yml.
- Rotate the Last.fm shared secret before built-in Last.fm sign-in is enabled for users. Do not set
  LASTFM_SHARED_SECRET on the Worker.
- If the owner pastes a secret in chat, store it only as a repo secret (`gh secret set NAME -R siamform/yumeiro`
  reading from stdin), never in a file in the repo, and say so.

### Talking to the owner
- Short, plain answers. Explain technical choices in everyday words (he asked for this). No release talk unless he
  asks. Ask only what you cannot decide yourself.
- Keep these two shared documents up to date when they change (same names): "Yumeiro Handover for Next Agent" and
  "Yumeiro 2.x Major Bugs and Features".

---

## 4. Owner decisions (do not reopen)

| Decision | Date |
|---|---|
| P2 dropped; P1 only | before 2.0.0 |
| REPEAT=1 for device runs | before 2.0.0 |
| Jackson 2.18.11 phone / 2.13.5 TV | before 2.0.0 |
| NBUG-6: sequential DNS fallback | before 2.0.0 |
| Vega / Nuvio / SkyStream stay one registry | before 2.0.0 |
| `Co-authored-by: Perplexity` trailer, owner as author and committer (re-confirmed by the owner 2026-11-21) | before 2.0.0, 2026-11-21 |
| No 2.0.1; fix everything, then 2.1.0 | 2026-11-20 |
| No release for now; fix, implement and test all first | 2026-11-21 |
| No third-party starter extensions; use the Yumeiro master extension (E1) | 2026-11-21 |
| Discreet launcher icon: dropped, do not build | 2026-11-21 |
| Telegram: full login (TDLib), replaces the old "no login" decision | 2026-11-21 |
| Build our own; reverse engineering is allowed to understand sites, plugins and formats (interoperability, diagnosis), e.g. B-32. Still not allowed: bypassing DRM, logins or paywalls, and copying closed or GPL-incompatible code (legal risk to the app) | 2026-11-21 |

---

## 5. Where things are

### Docs
| File | What |
|---|---|
| `docs/AGENT-START-HERE.md` | This file |
| `docs/R15-STATUS.md` | Line 3 = "Resume here"; full status table |
| `docs/BUGS-AND-FIXES-2.0.0.md` | Ledger: every bug B-1..B-32 with commit, tests, fail-before and pass-after runs |
| `docs/MAJOR-BUGS-AND-FEATURES-2.x.md` | Must-do list: P0, epics E1–E4, features F-01..F-22, Now/Next/Later |
| `docs/HANDOVER-NEXT-AGENT-2026-11-20.md` | Running handover, newest sections at the bottom (methods, measurements) |
| `docs/OWNER-MANUAL-TESTS.md`, `docs/MANUAL-TESTS.md` | Owner's phone test lists |
| `docs/evidence/final/CANCELLED-RUNS.md` | Cancelled CI/device runs and why |
| `docs/ARCHITECTURE.md`, `docs/SYSTEM-DESIGN.md`, `docs/DEVELOPMENT.md` | Architecture and build notes |
| `CHANGELOG.md` | Release notes (write the 2.1.0 section at release time) |

### Code map (app/src/main/java/dev/asfakul/yumeiro/)
| Area | Files |
|---|---|
| App start | `MTApplication.kt` (hooks installed here, no I/O on main), `ui/screens/MTPlayerApp.kt` (first-run effects) |
| Links / streams | `stream/LinkHosts.kt` (routes), `stream/StreamResolver.kt`, `stream/TeraBoxApi.kt` (B-29), `stream/DiskWalaApi.kt` (B-30), `stream/CloudRecapture.kt` (F-03), `stream/StreamModels.kt` |
| Torrent | `LinkHosts.magnetFromHash`, `torrServerUrl`, `TorrentSettings` (user TorrServer only today) |
| Extensions | `online/ExtensionManager.kt`, `online/ExtensionMarket.kt`, `online/OnlineRepository.kt` (home rows; "Nothing came back" path), `ui/screens/online/OnlineHomeScreen.kt`, `ui/screens/online/OnlineGetStarted.kt` |
| Telegram | `online/telegram/TelegramPublic.kt`, `online/telegram/TelegramPlayback.kt`, `ui/screens/sites/TelegramChannelScreen.kt`, `online/web/SavedSites.kt` |
| Startup speed / main thread | `online/StartupSpeed.kt`, `text/RegexWarmup.kt` |
| Updates | `update/GitHubReleaseClient.kt` (ABI names), `notify/WhatsNew.kt` (`shouldPost` must accept 2.1.x at release) |
| Settings | `ui/screens/settings/SettingsCatalog.kt` (+ SettingsDiscoverabilityTest) |

### Traps that already bit agents
- RegexGuardTest: every class holding a `Regex(...)` must be listed in `RegexWarmup.CLASSES`. Hoist patterns to
  `private val` and add the class, or CI fails (happened twice on 2026-11-21).
- Compose-rule device tests measure the test harness (`AbstractMainTestClock.advanceTimeByFrame`), not the user.
  The real stall gate is `GateOnePrepareDeviceTest` then `MainThreadStallRealClockDeviceTest` (limit 700 ms).
- `git add` with a deleted path fails the whole add silently in a chain; check `git show --stat HEAD` after commit.
- Main-thread disk or network work freezes 2 GB phones (api33-lowram). Move it to `Dispatchers.IO`.

---

## 6. State on 2026-11-21

- Owner top priority 2026-11-21 19:00 +06: docs/FEED-ENGINE-2.1.md (feed engine for 1000+ extensions, priority loading,
  instant cached feed, 10 min feed after install/update, reinstall-after-update check). Read it before feed/search work.
- Owner rule 2026-11-21 19:15 +06: after every bug or feature, mark its status in the docs as verified (run ID) or not.
- Live state, pending runs and the next step: docs/HANDOVER-LIVE.md (owner rule 2026-11-21: rewrite it with every
  push; every listed bug and feature must be done and verified; mandatory focus: feed, extension loading, load
  speed, search speed).

- Branch `r15-stability`, green CI 44827815442 on 1e92bc33 (all jobs including the signed review APK).
- Fixed since v2.0.0 (ledger rows B-10..B-30): freezes FREEZE-1/2/4, PROF-1, T-1, F-18, F-19, PIN-1/2, GATE-1,
  RegexGuard, B-25 search-only, B-27 recorder leak, B-28 YouTube Music on main, B-26 starters removed,
  B-29 TeraBox full video (share API + the user's TeraBox sign-in; never the 30 s preview; AV1 → H.264 transcode on
  phones without an AV1 decoder), B-30 DiskWala hand-off to the DiskWala app.
- Telegram credentials: repo secrets YUMEIRO_TG_API_ID / YUMEIRO_TG_API_HASH, BuildConfig TG_API_ID / TG_API_HASH.
- 2026-11-21 (new agent): F-02 Telegram full login started, design ADR-052 in SYSTEM-DESIGN.md.
- Blocked: B-29 signed-in path on a real TeraBox account (needs an owner test account).

---

## 7. What is next, in order

1. **F-02 Telegram full login** (TDLib). Pick a maintained TDLib Android build (check licence, ABIs, size);
   phone-number + code + 2FA password sign-in; chats/channels list, search inside a chat, play videos in Yumeiro's
   player (streamed by TDLib file download with priority). Hidden when BuildConfig TG_API_ID is blank. Pairs with
   E3 ABI splits for size. Tests: unit tests for the auth state machine with a fake client; device test of the
   screens without a real account; the owner checks a real sign-in.
2. **B-31 built-in torrent engine**: e.g. libtorrent4j; magnet/hash → metadata → choose the largest video file →
   sequential pieces → local HTTP server with Range → Yumeiro player. TorrServer stays optional. Test hash
   Owner add 2026-11-21: always ready (one engine + one loopback server for the app's life, no per-link setup), any
   torrent link plays, and faster download / start / seek where possible (priority pieces, DHT/PEX/LSD, extra trackers,
   resume data). F-02 owner add: Telegram videos AND audio, private and public channels and groups, play and download.
   `BF2063A5E9A69F14716288FCA7C2D6078BDA7369` (5.87 GB HEVC 10-bit + DD+5.1 MKV; FFmpeg audio already ships).
3. **B-32** extensions that load nothing ("HindiSubAnime didn't load: Nothing came back from this extension"):
   investigation order is in the ledger row.
4. **E1 Yumeiro master extension** (replaces the removed starters): own repo and update manifest; check each source
   is legal; a scheduled validation workflow needs the owner's yes.
5. **F-21** rich extension metadata rows and **F-22** Upcoming filters + date-grouped sections (specs in
   MAJOR-BUGS-AND-FEATURES-2.x.md).
6. Remaining MAJOR list items (E3 ABI splits, E4 engine, F-items "Built, not tested" need device checks), then
   the owner's manual test list. Release 2.1.0 only when the owner says: version.properties 2.1.0 / 19,
   CHANGELOG, WhatsNew.shouldPost 2.1.x + SettingsDiscoverabilityTest, then merge to main.

---

## 8. Skills and tools that help (Perplexity Computer agents)

- `business-playbooks`: roadmaps and specs (guide `pm/roadmap-management/GUIDE.md`).
- `office`: Word/PDF/PowerPoint/Excel files if the owner asks for one.
- `media`: social images for a release. Only for features that work as shown.
- `website-building`: the Yumeiro website (`docs/website`).
- `visualize`: inline charts or diagrams for explanations.
- `browser-use`: only when a site must be driven by hand (e.g. checking a share page); use `curl` first.
- How the 2026-11-21 link checks were done (reuse): plain `curl` or Python `requests` from the sandbox against the
  real share pages and APIs, saving the answers, then trimming them into test fixtures. Compare competitors by
  reading their open source (e.g. Hikari `codegeasse1/hikari`, `app/src/main/java/com/hikari/app/data/NetworkStream.kt`)
  with `gh api repos/<owner>/<repo>/contents/<path> --jq .content | base64 -d`.
