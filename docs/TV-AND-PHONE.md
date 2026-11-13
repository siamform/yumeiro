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
- Phone: `release-Yumeiro-v<V>.apk` (universal), `Yumeiro-v<V>-arm64-v8a.apk`, `Yumeiro-v<V>-armeabi-v7a.apk`.
- TV: `Yumeiro-TV-v<V>-universal.tv.apk`, `Yumeiro-TV-v<V>-arm64-v8a.tv.apk`, `Yumeiro-TV-v<V>-armeabi-v7a.tv.apk`.
  The `.tv.apk` ending means 1.6.0 updaters (which match `-<abi>.apk` or `release-`) never pick them.

## Gradle tasks
```
./gradlew assembleMobileDebug assembleTvDebug
./gradlew testMobileDebugUnitTest                 # all shared tests
./gradlew testTvDebugUnitTest --tests 'dev.asfakul.yumeiro.update.*'
./gradlew assembleMobileRelease assembleTvRelease # release workflow
./gradlew assembleMobilePreview assembleTvPreview # preview builds
```
APKs land in `app/build/outputs/apk/<flavor>/<buildType>/`.
