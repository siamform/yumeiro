# Yumeiro 2.0.0: ready-to-post release text

Copy the parts you need. Images are in docs/assets/social (1080 x 1350, real captures; 00 is the changelog card).

## Short post (Facebook / Telegram / Discord)

Yumeiro 2.0.0 is out. Phone and Android TV.

If you installed 1.9.0: please update now. 1.9.0 closed itself when a video opened (YouTube, Live TV, downloads,
online episodes). 2.0.0 fixes it. Your profiles, history, downloads and settings stay.

What's in it:
- Profiles (up to 8) with "Who's watching?", PIN or fingerprint lock, own history and 18+ switch
- Settings organised by subject: You, Video, Music, Downloads & library, Sources & accounts, System
- Private downloads (hidden from lists and the gallery) and stealth mode
- Your statistics: week, streak, activity map
- Public Telegram channels (no login), TeraBox / DiskWala links
- Hold the video for 2x, volume boost to 200 %, Shorts player
- Faster start, Live TV switches to a backup link, Vega script repos, your own proxy, DNS fallback

Download: https://yumeiro.click/download
Changelog: https://github.com/siamform/yumeiro/blob/main/CHANGELOG.md
Help test it: https://github.com/siamform/yumeiro/blob/main/docs/OWNER-MANUAL-TESTS.md#yumeiro-users-checklist

Made in Bangladesh.

## Suggested image order

1. 00-changelog-card.png (what's new, crash fix first)
2. 02-video.png (a film plays and resumes)
3. tv-video-controls.png (real playback on TV)
4. 04-whos-watching.png / tv-whos-watching.png (profiles)
5. 06-settings.png (Settings by subject)
6. 09-stats.png (your statistics)
7. 08-telegram.png (Telegram channels, no login)
8. 12-downloads.png (Downloads and Private)
9. 03-music.png / tv-music-playing.png (music)
10. 01-extensions.png / tv-extensions-home.png (installed extensions)
11. 10-sources.png, 05-profiles-page.png, 07-search.png, 11-music-settings.png, tv-settings-home.png

## Changelog for the post (full)

### Fixed in 2.0.0
- Opening any video crashed 1.9.0 (YouTube, Live TV, downloads, online episodes). The video player is rebuilt in
  smaller parts, and every release build is now checked so this can't come back unnoticed.
- 1.9.0 showed "Closed to free memory" crash reports when Android closed the unused app in the background. That
  is normal Android behaviour and is no longer reported as a crash.
- Memory-trim events are logged once every few seconds instead of many times a second.

### New since 1.8.1 (first shipped in 1.9.0)
- Profiles and privacy: up to 8 profiles, PIN / fingerprint lock, own history, lists, YouTube history and 18+
  setting per profile; private downloads; stealth mode.
- Settings organised by subject, with search that finds profiles, statistics, Telegram, Vega and script repos. An
  Inbox note after updating says where everything is.
- Your statistics: week, streak and activity map (Settings → You → Your statistics).
- Sources: public Telegram channels without login, open TeraBox / DiskWala links, Vega / Nuvio / SkyStream script
  repos in a sandbox, your own proxy, DNS fallback.
- Player: hold for 2x, volume boost to 200 %, Shorts player, external players, five accent colours, Kasumi theme.
- Faster start: setup, feed, extensions and catalog show at once.
- Live TV switches to a channel's other link when one dies; Now / Next from the playlist guide.
- Manga: saved chapters open offline; on-device translation in the reader.
- TV: search and filter sheets no longer pop up the keyboard; every control reachable with the remote.

### Known limits
- YouTube can block playback with "Sign in to confirm you're not a bot".
- Android TV keeps showing the notification dialog after every "Don't allow".
- Built-in Last.fm sign-in stays off for now; your own Last.fm key works.
