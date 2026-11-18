# Development workflow

For the current maintainer and whoever picks this up next.

## Rules

- `main` is protected. Work on a branch (`feat/…`, `fix/…`), open one PR per update, squash-merge.
- Conventional commits (`feat:`, `fix:`, `docs:` …). The release workflow derives the next version from them unless
  `version.properties` was bumped by hand (a hand bump wins).
- Every merge to `main` that touches `app/**` builds a signed release and publishes it. Do not merge half-done work.
- Keep GitHub Actions usage low: run tests locally, push once, avoid re-running workflows for nothing.
- Free only. No paid backend, no paid store account. If a feature needs a server, make it optional and self-hostable,
  or wait.
- Every new Kotlin file starts with the GPL header (see any file in `dev/asfakul/yumeiro`). Vendored Apache-2.0 files
  keep their origin and get a line in `NOTICE`.

## Group batch CI (owner rule §13a, 2026-11-18; updated by the owner's handover prompt, 2026-11-18)

CI is slow, so work is done one whole group at a time, not one feature at a time. This applies from G8
onward (G6-QP finished as a single item before the rule).

Group lifecycle, in strict order:

1. **Read the entire group line by line.** Every file of every feature in the group, completely. Record the
   files and line ranges reviewed in the group doc.
2. **Audit it.** For every feature: side effects, advantages and disadvantages, the action-reaction table
   (unhappy paths included: cancel, Back, rotate, kill and restore, no or slow network, low memory, denied
   permission, double taps, two events at once), competitor and open-source research (at least two
   implementations per item, exact tag and files), optimization opportunities, architecture and system design.
   Hunt bugs: threading, coroutines, lifecycle, Compose, memory, network, data, errors, security, battery,
   accessibility, TV, code quality.
3. **Write the findings register and ADRs.** Each finding: ID, severity, file and line, root cause, fix plan.
   Redesign only with evidence, an ADR (options, advantages and disadvantages, decision, migration, rollback)
   and behaviour-parity tests.
4. **Write every regression test for the group in one commit**, one test per finding; each must fail on the old code.
5. **Run fail-before once** for the whole group (one CI run; one device run for device findings). Every test must fail.
   - A test that passes on the old code means its finding is not proven: take it out of the batch and put it in
     `docs/UNVERIFIED-QUEUE.md` with the reason.
   - If every test for a layer passes on the old code, the read was wrong: re-read that layer and rewrite its tests.
6. **Commit the fixes**, one small logical commit per finding, in test order. No squash.
7. **Run pass-after once** for the whole group. Every test must pass.
8. **If anything fails:** audit the failures, find the root cause, fix all of them. Only the failing tests may be
   run first to prove each fix, but then **rerun the group CI**. Repeat until the whole group passes. Never weaken a
   test, drop an item, or call a partial pass a pass.
9. **Group regression** on the close commit: the full inventory regression for the group on both phone emulators,
   and on both TV emulators where the group touches TV.
10. **Close the group** in one push: the group status table (every feature and function, status, how verified,
    pass/fail, evidence run ID and test name), ledger rows, queue entries and "Resume here".

Evidence per item is preserved:
- The fail-before log names every failing test; the pass-after log names every passing test.
- Each ledger row cites the group run ID plus the test name. A batch is N findings proven in two runs, not one merged finding.
- Every result is labelled by what proved it: `unit`, `emulator`, `CI`, `TV emulator`, `real hardware`.

Size cap and other limits:
- **Size cap:** more than 30 findings → at most two batches, split by layer.
- **Never cross groups:** G8 batches are G8-only, G9 batches are G9-only.
- **The release gate is never batched:** one unbatched full regression on the release commit, both phones
  and both TV images.
- **CI budget:** check Actions first, never duplicate a running equivalent, ≤ ~10 test classes per run, ≤ 2 device
  workflows at once, no stacked large runs, no scheduled workflows. Throwaway fail-before branches are deleted after.
- **Handover safety:** commit and push after every step; update "Resume here" (docs/R15-STATUS.md) before any long
  wait; a multi-file doc update starts with `DOCS UPDATE IN PROGRESS: <files>` in "Resume here", removed by the push
  that completes it.
- **Owner tests:** only what needs real hardware, a personal account, a home network or human judgment goes to
  `docs/OWNER-MANUAL-TESTS.md` (steps, expected result, pass/fail rule, what to record, failed agent attempts).

## Local setup

```bash
export JAVA_HOME=/path/to/jdk-21 ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testMobileDebugUnitTest      # JVM + Robolectric tests, includes Compose screenshot tests
./gradlew :app:assembleMobileDebug          # installable debug APK (dev.asfakul.yumeiro.debug)
./gradlew :app:assembleRelease        # R8 check; needs MT_KEYSTORE_* env vars to sign
```

Screenshot tests write PNGs to `app/build/screenshots/`. Look at them after UI changes.

## Adding a new source ecosystem

1. Create `sources/<name>/` with a manager object (install/remove/persist) and a `MainAPI` (video) or
   `MangaProvider` (manga) adapter.
2. Publish with `SourceHub.publishVideo("<name>", Ecosystem.X, apis)`; call `SourceHub.remove("<name>")` when empty.
3. Start it from `Bridges.start`.
4. Add a tab or card in `SourcesHubScreen`, a ProGuard rule if it loads classes by name, unit tests for the pure
   parsing parts, and a row in the README "What's different" table.

## Release checklist

1. `testMobileDebugUnitTest` green, `assembleRelease` builds.
2. `version.properties` bumped (or rely on auto-bump) and `fastlane/metadata/android/en-US/changelogs/<code>.txt` added.
3. `docs/HANDOVER.md` section for the update, README if user-visible.
4. Merge PR → release workflow → check the release page, `https://yumeiro.click/download` and `https://yumeiro.click/get?abi=arm64-v8a`.

## Next developer: where to start

- Read `docs/HANDOVER.md` (history and root causes), then `docs/ARCHITECTURE.md`.
- Open issues labelled `good first issue`; the Source status screen is the quickest way to reproduce source bugs.
- Planned work is in `docs/ROADMAP.md`.
