# Release-day plan and universal hard rules (owner, 2026-11-22 12:15 +06)

Every agent, old or new, follows this file. An agent can run out of limit at any time, and the sandbox can freeze or
be closed at any time. So: write state into the repo and push after every step, never keep it only in your head or
the sandbox. Read this file, then `docs/HANDOVER-LIVE.md`, then `docs/AGENT-START-HERE.md`.

## Universal hard rules (added by the owner 2026-11-22; they apply on top of AGENT-START-HERE.md section 3)

1. Assume you can stop at any second. After every finished step: commit, push, rewrite `docs/HANDOVER-LIVE.md`, tick
   the step in the checklist below (with run IDs / file paths). Never leave verified work only in the sandbox.
2. Partial or fake success is never acceptable. A step is done only when it is done AND verified (run ID, file, link).
3. When the owner says "add", write it into the repo docs and push at once.
4. Skills: the docs name the skills past agents used; install them again in a new sandbox and use them when the
   work needs them (they are sandbox-only, so they are gone after a reset):
   - `ui-ux-pro-max` (UI/UX rules, `references/pro-rules.md`), `kotlin-agent-skills` (Kotlin / Android),
     `rea` (reverse engineering for interoperability), `architecture-designer` from `jeffallan/claude-skills`
     (system design). Past clone path: `/home/user/workspace/agent-skills` (sandbox only).
   - A Photoshop / image-mockup skill may be installed for the mockups (owner allowed it). Built-in `media` skill
     (images), `office` (PDF), `website-building` (yumeiro-web) are also available.
5. The goal is clear: Yumeiro is the top app of its kind in everything (see `docs/GOAL-TOP-OF-CLASS.md`). Set the
   goal, chase it, achieve it. Keep Yumeiro's own vibe and flavour (夢色, the palette and type in `docs/brand/`);
   never copy another app's look or code.
6. No merge to main, release, tag or production deploy until the owner explicitly approves after his soft test.
   The website (`siamform/yumeiro-web`) is changed on a branch / PR only, not deployed, until he approves.

## Today's work, in order (tick each with evidence; next agent continues at the first unticked line)

### A. Finish open fixes and features (ledger `docs/BUGS-AND-FIXES-2.0.0.md`, list `docs/MAJOR-BUGS-AND-FEATURES-2.x.md`)
- [ ] F-05-S SkyStream: r15 CI pass-after (first attempt 44989934475: 45 Robolectric Maven-fetch errors = Blocked,
      plus 2 real test bugs: `File.createTempFile("p", ...)` prefix too short, fixed), device
      SkyStreamProviderDeviceTest, then flip `scripts_skystream` on in its own commit.
- [ ] GATE-1-FEED-LATE (late feed sections join one per frame): fail-before 44990181586 valid (2 of 3 fail), r15 CI,
      then gate trio on api33-lowram.
- [ ] Other open rows in MAJOR (C5, C6, B-8, B-9, F-02, B-29, F-03, F-19, F-20, airing alerts, PIN-1, FREEZE,
      FEED-ENGINE leftovers, Stremio stream-only add-ons). Anything that cannot be proven today is listed as
      "Not run (reason)" or "Blocked (reason)" in the changelog, never claimed.

### B. Website `siamform/yumeiro-web` (https://yumeiro.click)
- [ ] Update the changelog page and any version / feature facts to the current state.
- [ ] Add the social links (same accounts as OL-13: Telegram channel + group, Discord, Facebook, Instagram, X,
      TikTok, Reddit `@officialyumeiro`; developer Facebook siam1p, Instagram asfakul_siam, Telegram @siamorwho).
- [ ] Branch + PR only; deploy only on the owner's go.

### C. Release material (all in `docs/release-2.1/`, committed)
- [ ] `CHANGELOG-FULL.md`: full changelog from 2.0.0 to now, from the ledger rows with commit and run IDs.
- [ ] Root `CHANGELOG.md` updated with the same (user wording).
- [ ] `MANUAL-TESTS-2.1.md`: manual test list for the owner and users (from `docs/OWNER-MANUAL-TESTS.md`, written
      for users: steps + "passes if").
- [ ] README forensic pass: every claim, version, feature, link, screenshot and project fact checked against the code;
      fix anything missing, old or wrong; add the latest mockup images and the full changelog link. Keep the
      "made in Bangladesh" line.
- [ ] Mockup images: social media (square + story), share card, release banner, website hero, README images. Real
      screens only (from device runs / screenshots), in Yumeiro's style.
- [ ] Zip `yumeiro-2.1-release-kit.zip`: changelog PDF with image proof, `CHANGELOG.md`, the mockups, and
      `SOCIAL-CAPTIONS.md` (captions for each network).

### D. Soft-test APK
- [ ] Build the phone APK in CI from `r15-stability` (review APK job, signed, not published), download it, share it in
      the chat. The owner installs it, tests, then decides: approve PR / merge / release, or continue.

## Progress log (newest first; one line per finished step)
- 2026-11-22 12:15 +06: plan written (this file).
