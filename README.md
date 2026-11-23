<div align="center">

<img src="docs/logo.png" width="132" alt="Yumeiro logo" />

# Yumeiro

### 夢色 · One dream-coloured app for every song, video, anime and live channel you love.

**The all-in-one entertainment hub for Android. Made in Bangladesh.**

No account needed. No ads. No tracking.<br/>
Local music and video, YouTube, YouTube Music, live TV / IPTV, CloudStream, Aniyomi, Mihon, Stremio and JavaScript
sources, plus your own Jellyfin, Plex, DLNA, WebDAV and SMB servers — unified in one fast, beautifully designed player.

<p>
  <a href="https://yumeiro.click/download"><img alt="Download" src="https://img.shields.io/github/v/release/siamform/yumeiro?style=for-the-badge&label=Download&color=F5C46B&labelColor=16161A" /></a>
  <a href="https://github.com/siamform/yumeiro/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/siamform/yumeiro/total?style=for-the-badge&color=F5C46B&labelColor=16161A" /></a>
  <a href="https://github.com/siamform/yumeiro/stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/siamform/yumeiro?style=for-the-badge&color=F5C46B&labelColor=16161A" /></a>
</p>
<p>
  <img alt="Android 9+ (TV 7+)" src="https://img.shields.io/badge/Android-9%2B%20%C2%B7%20TV%207%2B-F5C46B?style=flat-square&logo=android&logoColor=white&labelColor=16161A" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-F5C46B?style=flat-square&logo=kotlin&logoColor=white&labelColor=16161A" />
  <a href="LICENSE"><img alt="License GPL-3.0" src="https://img.shields.io/badge/License-GPL--3.0-F5C46B?style=flat-square&labelColor=16161A" /></a>
  <a href="https://github.com/siamform/yumeiro/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/siamform/yumeiro/ci.yml?style=flat-square&label=CI&labelColor=16161A" /></a>
</p>

**[Download the APK](https://yumeiro.click/download)** · **[Website](https://yumeiro.click/)** · **[What's new](docs/WHATS-NEW.md)** · **[Full guide](docs/GUIDE.md)** · **[Source status](https://yumeiro.click/status)** · **[Report a bug](https://github.com/siamform/yumeiro/issues)**

</div>

---

## Latest release: 2.2.0

- **Online at once**: Extension Market, YouTube, Yumix, online music and Live TV work right after an install or update.
- **Live TV keeps its channels**: the saved list shows at once, even on a slow link.
- **One Back**: the Back key, button bar and swipes close a page with the same slide as the on-screen arrow.
- **Solid themes**: glass effects removed from every theme.
- **Clearer extensions**: unsupported ones say what is missing; broken ones can offer an older working copy.

Highlights with images: [docs/WHATS-NEW.md](docs/WHATS-NEW.md) · every change and how it was checked: [CHANGELOG.md](CHANGELOG.md) ·
what is not proven yet: [docs/UNVERIFIED-QUEUE.md](docs/UNVERIFIED-QUEUE.md).

**Known imperfect:** YouTube can block playback with "Sign in to confirm you're not a bot"; Android TV keeps showing
the notification dialog after every "Don't allow". Full list in the [CHANGELOG](CHANGELOG.md).

## Why Yumeiro?

Most people juggle four apps: a music player, a video player, YouTube, and something for anime or live TV.
Yumeiro replaces all of them with **one fast, offline-first app that looks like it belongs in 2026.**

<table>
<tr>
<td width="50%" valign="top">

### Music
- Your local library: songs, albums, artists, folders, playlists
- **Synced lyrics** that scroll with the song (tap a line to seek)
- 10-band **equalizer** and sound FX
- **Sleep timer** — 5 to 90 minutes or end of song, with a gentle fade-out
- **YouTube Music** without an account — quick picks, search, background play

</td>
<td width="50%" valign="top">

### Video
- Grid of your device videos with **continue watching**
- Gestures, **pinch to zoom**, PiP, background play, speed, audio tracks
- **Floating player** like YouTube — press back or swipe the video down to keep watching while you browse
- Slim vertical brightness and volume bars, like your phone's own
- **Subtitles**: embedded, local files, or search OpenSubtitles, SubDL and SubSource at once (free keys, your own)
- **Subtitle style**: size, colour, outline, shadow or box, bold and height, with a live preview
- **YouTube** with no login — trending, anime, AMV, live

</td>
</tr>
<tr>
<td valign="top">

### Anime & online
- Works with **CloudStream extensions** — your existing repos just work
- **Aniyomi** anime, **Mihon** manga (with a reader, extensions-lib up to 1.6 and the new store index — Keiyoushi works), **Stremio** add-ons and **JavaScript** sources
- **Home servers**: Jellyfin, Emby, Plex, DLNA, WebDAV, SMB
- Extension **markets** for every format (CloudStream, Aniyomi, Mihon, Mangayomi, Stremio) with search, ranking and one-tap Add
- Auto-updates, sub/dub, seasons, episodes
- A YouTube-style **For you** feed across every extension

</td>
<td valign="top">

### Live TV & streams
- HLS, DASH, RTSP, MP4/MKV, IPTV **M3U playlists**
- **Playlist market** with hundreds of lists, refreshed every 3 days
- Custom headers, ClearKey / Widevine, smart link detection
- **Auto-unlock for HTTP 403**: refused links are retried with browser, VLC and host-player headers automatically
- Watch page with next / previous channel, groups and sharing

</td>
</tr>
</table>

### Designed, not just built

- **Tsuki** design system — calm ink-black nights or warm paper days, with a single moonlight-gold accent.
- **Ambient colour** — the background glow quietly takes the colour of the artwork that is playing, like Spotify and Apple Music do.
- **One floating dock** — the mini player and the navigation bar are joined into one capsule.
- **Endless home** — *Continue listening & watching*, shortcuts, then a *For you* feed that mixes your own songs and videos with YouTube and online picks forever.
- **User guide in Settings** — illustrated diagrams of every tab, gesture and fix.
- **Every error comes with a suggestion** for what to try next.
- Smooth 60 fps: progress ticks never recompose the page, no blur on lists, baseline profiles included.

## Android TV, Google TV and Fire TV

The TV APK (`Yumeiro-TV-v<version>.tv.apk`, Android 7.0 or newer) switches to TV mode on its own: a left navigation
rail, a visible focus ring on everything, and a player built for the remote. Install it with a file manager, or from a
computer on the same network: `adb connect <tv-ip>:5555`, then `adb install -r Yumeiro-TV-v<version>.tv.apk`.
More in [docs/TV.md](docs/TV.md).

## For extension authors

The [extension kit](extension-kit/README.md) has a working sample for each format Yumeiro reads: a static Stremio add-on you can host
on GitHub Pages for free, a JavaScript source with its `index.json`, and pointers for CloudStream, Aniyomi and Mihon templates.
Repositories listed in [.github/extensions/sources.json](.github/extensions/sources.json) are checked every 3 days and shown on the
[public status page](https://yumeiro.click/status).

## Install

1. Pick your APK on **[the download page](https://yumeiro.click/download)** (or [Releases](https://github.com/siamform/yumeiro/releases/latest)):
   `Yumeiro-vX.Y.Z-arm64-v8a.apk` for most phones (64-bit), `Yumeiro-vX.Y.Z-armeabi-v7a.apk` for older 32-bit phones,
   or the universal `release-Yumeiro-vX.Y.Z.apk` if you are not sure. TVs: `Yumeiro-TV-vX.Y.Z-<processor>.tv.apk` or
   the universal `Yumeiro-TV-vX.Y.Z.tv.apk`. The download page checks your processor for you.
2. Open it and allow "Install unknown apps" for your browser or file manager.
3. Done. Yumeiro checks for new versions and shows you the version and source before installing (Settings → Updates).

Phones and tablets: Android 9 or newer, `release-Yumeiro-v<version>.apk`. Android TV, Google TV and Fire TV: Android 7.0 or newer, `Yumeiro-TV-v<version>.tv.apk`. The universal APK runs on every processor (arm64-v8a, armeabi-v7a, x86, x86_64); the per-processor APKs are smaller. The updater always takes the file that fits your phone (Settings → Updates shows which).

## Verify a release

GitHub Actions builds every release from this repository. The build uses a pinned JDK, pinned action SHAs and no build
cache, and it never makes a debuggable build. The signing key exists only as an encrypted GitHub secret and is shredded
after the build. Each release publishes `SHA256SUMS.txt`, generated in the same CI job.

```sh
# 1. Checksum: download the APK and SHA256SUMS.txt from the same release
sha256sum -c SHA256SUMS.txt --ignore-missing

# 2. Signing certificate (apksigner ships with Android SDK build-tools)
apksigner verify --print-certs release-Yumeiro-vX.Y.Z.apk | grep SHA-256
```

The SHA-256 certificate digest must be:

```
2e:cd:fd:04:f3:b2:d1:28:05:01:33:4a:5c:24:7d:71:29:ba:fa:2b:5a:43:48:47:28:9d:fc:06:9a:41:3f:6b
```

The same value is in [`.github/release-signing-cert.sha256`](.github/release-signing-cert.sha256), and CI checks it on
every release. Do not install an APK with any other certificate; it was not built by this project. The in-app updater
does the same checks automatically. It only downloads from this repository's GitHub releases, compares the APK against
the published SHA-256, and relies on Android's same-signature rule when installing.

## What Yumeiro hosts (and doesn't)

Yumeiro is a player. **It hosts no media.** Playlists, live-TV streams and online-video extensions come from third
parties. The market branches only index links that other people publish. Extensions are third-party code that runs
inside the app, so install only those you trust. See [DISCLAIMER.md](DISCLAIMER.md).

## Security

Please report vulnerabilities privately via
[GitHub security advisories](https://github.com/siamform/yumeiro/security/advisories/new), not in public issues.
Scope, response times and the disclosure policy are in [SECURITY.md](SECURITY.md). Privacy: [PRIVACY.md](PRIVACY.md) · Terms: [TERMS.md](TERMS.md). Audit notes are in
[docs/security](docs/security/).

## More

| | |
|---|---|
| [What's new](docs/WHATS-NEW.md) | Release highlights with images (2.2.0, 2.1.0, 2.0.0, the redesign) |
| [Screenshots](docs/SCREENSHOTS.md) | Every main screen, dark and light, phone and TV |
| [How it compares](docs/COMPARISON.md) | Yumeiro next to CloudStream, Aniyomi, Mihon, Stremio, Kodi and Jellyfin |
| [Full guide](docs/GUIDE.md) | Every feature and where to find it in Settings |
| [TV](docs/TV.md) | Android TV, Google TV and Fire TV |
| [FAQ](docs/FAQ.md) | Play Protect, HTTP 403, accounts, privacy, crash reports |
| [Changelog](CHANGELOG.md) | Every release, with how each change was checked |

## Build from source

```bash
git clone https://github.com/siamform/yumeiro.git
cd yumeiro
./gradlew assembleMobileDebug        # JDK 17+, Android SDK 37
./gradlew testMobileDebugUnitTest    # unit + UI tests
```

Architecture, release pipeline, updater internals and the full feature list are in **[docs/GUIDE.md](docs/GUIDE.md)**
and **[docs/HANDOVER.md](docs/HANDOVER.md)**.

## Contributing

Pull requests are welcome. Branch from `main`, use [Conventional Commits](https://www.conventionalcommits.org/),
and make sure CI passes — merging to `main` ships a release automatically. Security issues: please use
[private reporting](https://github.com/siamform/yumeiro/security/advisories/new).

## Community

Yumeiro is **@officialyumeiro** everywhere:
[Website](https://yumeiro.click) ·
[Telegram channel](https://t.me/yumeiroofficial) ·
[Telegram group](https://t.me/chatyumeiroofficial) ·
[Discord](https://discord.gg/eqkNymUAMJ) ·
[Facebook](https://www.facebook.com/officialyumeiro) ·
[Instagram](https://www.instagram.com/officialyumeiro) ·
[X (Twitter)](https://x.com/officialyumeiro) ·
[TikTok](https://www.tiktok.com/@officialyumeiro) ·
[Reddit](https://www.reddit.com/user/officialyumeiro)

Developer, Asfakul Siam:
[Facebook](https://www.facebook.com/siam1p) ·
[Instagram](https://www.instagram.com/asfakul_siam) ·
[Telegram](https://t.me/siamorwho) ·
[GitHub](https://github.com/siamform)

## Support the project

If Yumeiro replaced a few apps on your phone, **[give it a star](https://github.com/siamform/yumeiro/stargazers)** —
it is the single best way to help more people find it. Share it with a friend who still uses four apps.

<div align="center">

[Website](https://yumeiro.click/) · [GitHub @siamform](https://github.com/siamform)

**Made in Bangladesh**

</div>

## License

Yumeiro is free software under the **[GNU GPL v3](LICENSE)** — the same licence as the CloudStream and NewPipe code it builds on.
You may study, change and share the code; modified versions must stay open source under the same licence.

The **Yumeiro name, logo and icon are not covered by the licence** — forks must use their own name, icon and
`applicationId`. See [TRADEMARKS.md](TRADEMARKS.md) and [NOTICE](NOTICE).

Fonts: Manrope and Unbounded, SIL Open Font License 1.1 ([docs/licenses](docs/licenses/)).
