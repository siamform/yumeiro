# Extension compatibility

Yumeiro aims to load any extension a user brings, from any of the ecosystems it supports, without
the user having to know which app it was built for. This page records how that is checked and
what was fixed.

## Add anything (auto-detect)

One entry point, in Extensions (menu, "Add anything (auto-detect)") and in the Sources hub header
("Add anything"). It also receives links and files opened from other apps (VIEW and SEND intents).
Detection lives in `sources/UniversalAdd.kt` as pure functions (`hint`, `candidates`, `sniff`),
covered by `UniversalAddTest`.

| Paste or open | Detected as | Engine |
|---|---|---|
| `.cs3` file or link (zip with `manifest.json` and `pluginClassName`) | CloudStream plugin | `ExtensionManager` |
| `repo.json` with `pluginLists`, `cloudstreamrepo://`, `https://cs.repo/?`, short code | CloudStream repository | `ExtensionManager` |
| `plugins.json` (array with `internalName` and `url`) | CloudStream plugin list | `ExtensionManager` |
| `index.min.json` or v2 store, `aniyomi://`, `tachiyomi://`, `mihon://` | Aniyomi or Mihon repository (by package names) | `TachiyomiExtensions` |
| Extension `.apk` (feature `tachiyomi.animeextension` or `tachiyomi.extension`) | Aniyomi or Mihon extension | `TachiyomiExtensions` |
| Stremio `manifest.json`, `stremio://` | Stremio add-on | `StremioAddons` |
| Mangayomi `index.json` (`sourceCodeUrl`), `mangayomi://` | JavaScript repository | `JsSources` |
| Jellyfin, Emby or Plex address | Home server (sign-in follows) | `HomeServers` |

When a link has no telling scheme, the app tries the likely URLs (`candidates`) and reads the
content (`sniff`), so a plain GitHub raw link to any of these works.

## CloudStream link audit

`scripts/extension_link_audit.py` checks every `.cs3` statically against the dex files of a built
APK: each class and member the plugin references must exist in the plugin itself, the Android
platform, or the APK. A gap means a `NoClassDefFoundError` or `NoSuchMethodError` at load or run time.

Run on the full market index (`extensions` branch, `index.json`):

- 2,620 entries, 2,552 unique plugins. 2,248 downloaded; the rest are dead links upstream.
- 2,182 linked clean against the shipped APK.
- 66 had gaps, all fixed except one:

| Gap | Extensions | Fix |
|---|---|---|
| Jetpack Compose classes stripped by R8 (plugins with their own settings UI) | 52 | `proguard-rules.pro` keeps the public Compose API (`runtime`, `ui`, `foundation`, `material3`, `material`, `animation`) |
| Extractors removed from CloudStream library 4.8 (`GMPlayer`, `Okrulink`, `VidSrcExtractor`, `VidSrcTo`, `Vidguardto` and `Vidguardto1-3`, `Vidplay` and subclasses) | 12 (StreamPlay, Ultima, Anichi, Animexin, Coflix, Movierulzhd, VidSrc) | Restored GPL copies in `app/src/main/java/com/lagradost/cloudstream3/extractors/`, with attribution headers. Rhino is a runtime dependency of the library already; the app compiles against it with `compileOnly` |
| `SubtitleHelperPlatform_jvmKt.getCurrentLocale` (plugin built against the JVM library) | 1 (PerfecPlay Youtube) | Shim in `com/lagradost/cloudstream3/utils/SubtitleHelperPlatformJvm.kt` |
| CloudStream app internals (`ResultViewModel2`, `PlayerGeneratorViewModel`, media3, coil3) | 1 (doGior StreamCenter) | Not supported: it drives CloudStream's own screens and player, which no other app has |

## Runtime probe

A subset was also loaded and run on the JVM (dex2jar, `-dsn -cf`, 4 GB heap), calling the main
page, search and load paths. CineStream, AniVortex, RaghavAnime (Kitsu), SoraAnime, Warflix and
Internet Archive returned real results.

Known upstream problems, which behave the same in CloudStream:

- PlutoTV: the API no longer sends a field the plugin requires, so parsing fails.
- Wizstream: a private build that needs a key from its author.
- StreamPlay: one method is too large for a JVM class file, so the JVM probe cannot load it.
  ART has no such limit and the static audit is clean.

## Re-running

```sh
./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3g :app:assembleMobilePreview :app:assembleTvPreview
mkdir -p /tmp/apkdex && unzip -o -q app/build/outputs/apk/preview/*universal*.apk 'classes*.dex' -d /tmp/apkdex
python3 scripts/extension_link_audit.py --apk-dex /tmp/apkdex --plugins <folder of .cs3> --out audit.json
```

Run it after every CloudStream library bump: upstream removes extractors from time to time and
older plugins keep referencing them.
