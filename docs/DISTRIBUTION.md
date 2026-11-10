# Distribution: website, IzzyOnDroid, F-Droid

Everything here is free.

## Website (now)

`https://yumeiro.click/download` redirects to the newest signed APK (or `?id=<assetId>` for a specific asset).
It asks the GitHub API for a short-lived signed link, so it keeps working when `github.com/.../releases/download/...`
returns 504. Use this link everywhere instead of the raw release URL.

## IzzyOnDroid (recommended first)

IzzyOnDroid takes the APK we already build and sign, so the release workflow does not change.

1. Make sure the repo is public and a release with the APK exists.
2. Open a "request for packaging" issue at <https://gitlab.com/IzzyOnDroid/repo/-/issues> with the repo link.
3. Metadata, screenshots and per-version changelogs are read from `fastlane/metadata/android/en-US/`
   (`title.txt`, `short_description.txt` ≤ 80 chars, `full_description.txt`, `changelogs/<versionCode>.txt` ≤ 500 chars,
   `images/icon.png`, `images/phoneScreenshots/`).
4. Expect a scan for trackers and non-free libraries. Google Cast (`play-services-cast-framework`) will be flagged as
   the "NonFreeDep" anti-feature; it is allowed on IzzyOnDroid.

## F-Droid main repository (later)

F-Droid builds from source and does not accept proprietary dependencies. Before submitting:

- Add a `foss` product flavor without `play-services-cast-framework` (cast button hidden).
- Remove build-time secrets (AniList / MAL / Trakt client ids) from that flavor or read them at runtime only.
- Expect anti-features: `NonFreeNet` (YouTube and extension sites), possibly `NSFW` depending on extensions.
- Submit a merge request to <https://gitlab.com/fdroid/fdroiddata> with `metadata/dev.asfakul.yumeiro.yml`.

## Changelog per release

Add `fastlane/metadata/android/en-US/changelogs/<VERSION_CODE>.txt` in the same PR that changes `version.properties`,
or right after the release workflow bumps it.
