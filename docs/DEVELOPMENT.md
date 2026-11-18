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

## Group batch CI (owner rule §13a, 2026-11-18)

CI is the bottleneck, so CI work is done one group at a time, not one item at a time. This applies from
G8 onward. G6-QP was already in progress when the rule arrived and finishes as a single item.

Group lifecycle, in strict order:

1. **Read** the code for every item in the group.
2. **Research** competitors for every item (master prompt §8: at least two implementations per item, exact tag, files).
3. **Write every regression test for the group in one commit**, one test per finding. Each test must fail on the old code.
4. **Run fail-before once** for the whole group (one CI run; device findings: one device run).
   - Every test must fail.
   - A test that passes on the old code means its finding is not proven. Remove it from the batch and put
     it in `docs/UNVERIFIED-QUEUE.md` with the reason. Do not fix it.
5. **Commit the fixes**, one small logical commit per item, in the order the tests were written. No squash.
6. **Run pass-after once** for the whole group. Every test must pass.
7. **If one item fails pass-after:**
   - isolate it and revert or correct only its commit;
   - run only that failing test, then continue;
   - do not rerun the whole group, weaken the test, or drop the item without a queue reason.
8. **Group regression** on the close commit: full inventory regression on both phones, and on both TV images where relevant.
9. **Update ledger, queue and "Resume here" in one push.** Close the group.

Evidence per item is preserved:
- The fail-before log names every failing test; the pass-after log names every passing test.
- Each ledger row cites the group run ID plus the test name. A batch is N findings proven in two runs, not one merged finding.

Size cap and other limits:
- **Size cap:** more than 30 findings → at most two batches, split by layer (for example G8-a: queue, ranges,
  resume, cancellation; G8-b: HLS/DASH, torrent, forced downloads, MediaStore, DB).
- **Never cross groups:** G8 batches are G8-only, G9 batches are G9-only.
- **The release gate is never batched:** one unbatched full regression on the release commit, both phones
  and both TV images.
- **Broken read:** if every test for a layer passes on the old code, the read was wrong. Stop, re-read that
  layer, rewrite its tests, and rerun that layer only.

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
