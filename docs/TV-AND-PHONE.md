# Yumeiro and Yumeiro TV (two apps, one code base)

Since 1.7.0 the phone and TV apps are built separately as Gradle product flavors
(dimension `device`):

| Flavor | App | `BuildConfig.IS_TV_APP` | Manifest |
|---|---|---|---|
| `mobile` (default) | Yumeiro, phones and tablets | false | `app/src/main/AndroidManifest.xml` (no leanback launcher, no banner) |
| `tv` | Yumeiro TV | true | `app/src/tv/AndroidManifest.xml` merged on top: leanback feature, banner, LEANBACK_LAUNCHER, landscape, no PiP |

Both share `applicationId` (`dev.asfakul.yumeiro`), the signing key and the version code, so
Firebase stays valid and one installs over the other with all data kept.

## What differs (see `Edition.kt`)
- `TvMode.resolve` is always true in the TV app; the Layout chooser is hidden there.
- Coil memory cache 12 % (phone 20 %), no crossfade, RGB565 posters.
- `maxBufferMs` 40 s (phone 50 s) in `StreamMediaFactory`.
- Updater: `GitHubReleaseClient.parseRelease(tv = ...)` picks only `.tv.apk` files in the TV app
  and only phone files in the phone app. The phone app on a device detected as a TV asks for the
  TV file and treats it as an update even at the same version.

## Release file names
Round 13 (1.8.0): one universal APK per edition, no per-processor files. Each carries native
libraries for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` (the release workflow fails if one is missing).
- Phone (Android 9+): `release-Yumeiro-v<V>.apk`.
- TV (Android 7.0+): `Yumeiro-TV-v<V>.tv.apk`.
  The `.tv.apk` ending means phone updaters (1.6.0 and later) never pick it; TV updaters take it as
  the universal TV file. 1.7.0 releases also had `-<abi>` files; updaters still understand both.

## Gradle tasks
```
./gradlew assembleMobileDebug assembleTvDebug
./gradlew testMobileDebugUnitTest                 # all shared tests
./gradlew testTvDebugUnitTest --tests 'dev.asfakul.yumeiro.update.*'
./gradlew assembleMobileRelease assembleTvRelease # release workflow
./gradlew assembleMobilePreview assembleTvPreview # preview builds
```
APKs land in `app/build/outputs/apk/<flavor>/<buildType>/`.
