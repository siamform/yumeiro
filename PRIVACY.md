# Privacy Policy

Last updated: 9 November 2026

Yumeiro is a free, open-source media player for Android, published at
[github.com/siamform/yumeiro](https://github.com/siamform/yumeiro). This policy explains what the app does with
your data. Short version: **we collect nothing.**

## What we collect

Nothing personal. Yumeiro has no accounts, no analytics, no advertising and no crash reporting. We cannot see what
you play, search or install. The one server of our own is optional push alerts, described below.

## What stays on your device

Your library, playlists, watch and listening history, favourites, settings and installed extensions are stored only
on your phone. You can delete them at any time by clearing the app's data or uninstalling it. Sign-in tokens for
optional services (below) are encrypted with the Android Keystore and never written in plain text.

## Services the app talks to

The app connects directly from your device to these services only when you use the matching feature. Each one has
its own privacy policy.

| Service | When | What is sent |
|---|---|---|
| GitHub (github.com, raw.githubusercontent.com) | Update checks, playlist and extension catalogs, in-app news | Standard web requests; no personal data |
| YouTube and YouTube Music | When you browse, search or play YouTube content | Your searches and the videos you open |
| AniList | Only if you sign in to AniList | An access token you approve, and the progress you choose to sync |
| MyAnimeList | Only if you sign in to MyAnimeList | An access token you approve, and the progress you choose to sync |
| OpenSubtitles | Only when you search subtitles online | The title you search for |
| AniSkip | When intro and outro skip is used for anime | The anime and episode number |
| Playlist and stream hosts | When you play a stream or IPTV playlist | Standard media requests to that host |
| Third-party extensions | Only extensions you install | Whatever that extension requests; extensions are written by third parties |

Yumeiro does not sell, share or transfer any data, because it has none to share.

## Push alerts (Yumeiro Cloud)

Only if you turn on push notifications, the app registers with our small server at `api.yumeiro.click` (Cloudflare
Workers, source in `server/` of the repository). It stores your push app's delivery address and the AniList ids of
the shows you follow with notifications on, nothing else: no titles, no history, no account, no IP address. When one
of those shows airs, the server sends an empty "check now" message and the app looks for new episodes itself.
Turning push off stops it; the record is deleted automatically when the address stops working, or after 45 days
without contact.

## Online watch-together rooms (Yumeiro Cloud)

Optional, no account. What's playing, the position and room chat are encrypted on your device with AES-256-GCM. The
room key is in the invite link after the `#`, which is never sent to any server, so `api.yumeiro.click` can only relay
sealed messages it can't read. It stores nothing per message: only the room id, a hash of the host's token and the
creation time, deleted when the host ends the room, when it has been empty for a few minutes, or after 24 hours.

Like any website, Cloudflare processes your IP address to deliver requests and for short-lived rate limiting; Yumeiro
doesn't log or store it.

## AniList and MyAnimeList

Signing in is optional. Yumeiro uses the official OAuth login of each service. The token stays on your device and is
used only to read your list and update your progress. You can sign out in Settings or revoke access at any time
from your AniList or MyAnimeList account settings.

## Children

Yumeiro is not directed at children under 13 and does not knowingly collect any data from anyone.

## Changes

If this policy changes, the new version will be published at this address with a new date.

## Contact

Open an issue at [github.com/siamform/yumeiro/issues](https://github.com/siamform/yumeiro/issues).
