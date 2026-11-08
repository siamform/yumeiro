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

## Local setup

```bash
export JAVA_HOME=/path/to/jdk-21 ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest      # JVM + Robolectric tests, includes Compose screenshot tests
./gradlew :app:assembleDebug          # installable debug APK (dev.asfakul.yumeiro.debug)
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

1. `testDebugUnitTest` green, `assembleRelease` builds.
2. `version.properties` bumped (or rely on auto-bump) and `fastlane/metadata/android/en-US/changelogs/<code>.txt` added.
3. `docs/HANDOVER.md` section for the update, README if user-visible.
4. Merge PR → release workflow → check the release page and `https://yumeiro-phi.vercel.app/download`.

## Next developer: where to start

- Read `docs/HANDOVER.md` (history and root causes), then `docs/ARCHITECTURE.md`.
- Open issues labelled `good first issue`; the Source status screen is the quickest way to reproduce source bugs.
- Planned work is in `docs/ROADMAP.md`.
