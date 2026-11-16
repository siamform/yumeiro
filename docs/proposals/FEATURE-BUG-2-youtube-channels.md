# BUG-2: YouTube channels cannot be opened (Step A.5 note)

Status: investigation note, written before any fix. Ledger row: BUG-2. Date: 2026-11-16.

## 1. Implementations read

| Project | Version read | Files |
|---|---|---|
| NewPipe (TeamNewPipe/NewPipe) | dev @ `1e61c9adacabe9ea59c06eee581d81350c7dcf8e` | `util/NavigationHelper.java` (`openChannelFragment`, `openCommentAuthorIfPresent`, `getIntentByLink`, `getChannelIntent`), `fragments/detail/VideoDetailFragment.java` (uploader / sub-channel click → `openChannel`) |
| Hikari (codegeasse1/hikari) | main @ `9694add539570bd78cb279175e0b45417ede0051` | `ui/ExternalLinks.kt` (`openYouTubeVideo`), `music/MusicApi.kt` (`kindOf`: `MUSIC_PAGE_TYPE_ARTIST` / `USER_CHANNEL` → artist page by `browseId`, `detail(browseId)`) |
| CloudStream (recloudstream/cloudstream) | master @ `5d66bbe` | Searched the repo: it has no built-in YouTube channel browser; YouTube appears only through third-party extensions. Nothing to compare for routing. |
| NewPipeExtractor | `v0.26.5` (in use), `v0.27.0`, `v0.27.1` release notes | No channel fix listed in v0.27.0 or v0.27.1 (v0.27.x raises the Java target to 17 and adds podcast/course tabs) |
| Yumeiro | `r15-stability` @ b89d490 | `youtube/YouTube.kt`, `youtube/YtHistory.kt`, `ui/screens/youtube/YouTubeScreens.kt`, `ui/screens/youtube/YtChannelPage.kt`, `ui/screens/watch/WatchScreens.kt` |

Licences: NewPipe GPL-3.0, Hikari (repo licence checked before reuse; ideas only), Yumeiro GPL-3.0.

## 2. What each does, and its weakness

NewPipe
- Every place that shows an uploader opens the channel: the video page's uploader and sub-channel
  rows, comment authors (only when `uploaderUrl` is present, with an error snackbar on failure),
  and list items.
- Links: `service.getLinkTypeByUrl(url)` sorts any pasted or shared URL into STREAM, CHANNEL or
  PLAYLIST and opens the matching screen. `/@handle`, `/channel/UC…`, `/c/…` and `/user/…` all count
  as CHANNEL.
- Search returns channels and playlists as well as videos.
- Weakness: if the extractor breaks on a channel page, the whole fragment shows an error. There is
  no partial header (name and avatar from the item that was tapped).

Hikari
- YouTube videos are handed to the YouTube app or the browser (`vnd.youtube:` intent, then the watch
  URL); there is no channel screen for YouTube videos. On YouTube Music, an artist or user channel
  (`MUSIC_PAGE_TYPE_ARTIST`, `USER_CHANNEL`) opens an artist page through `browseId`.
- Weakness: no in-app YouTube channel at all; it depends on another app being installed.

## 3. Yumeiro today (traced)

The channel page exists (`YtChannelPage` → `YouTube.channel(url)` → `ChannelInfo.getInfo`, then tabs
through `ChannelTabInfo`). It is only reachable from a video card's channel tap on the YouTube home,
search and list screens (`YtVideoCard(onChannel = openChannel)`).

Defects found by reading the code (each needs a failing test before it counts as a cause):
- E1: the watch page's channel row (avatar, name, subscribers; `WatchScreens.kt` item "channel") has
  no click. This is the place where people expect to open a channel (NewPipe, YouTube).
- E2: `YouTube.toYt` keeps only `StreamInfoItem`. Search results never contain channels, so searching
  a channel's name never offers the channel.
- E3: `YtHistory.encode` does not store `channelUrl`. Cards rebuilt from history (Home, Library, the
  "For you" plan that reads history) have `channelUrl = null`, and `openChannel` then sets
  `channelUrl = null`: the tap silently does nothing.
- E4: `YouTube.isYouTubeUrl` only asks the stream link factory. A shared or pasted channel link
  (`youtube.com/@name`, `/channel/UC…`) is not recognised as YouTube.
- E5 (to check on device with network): whether `ChannelInfo.getInfo` itself fails on v0.26.5 for the
  current YouTube responses. A CI device probe (`YtChannelProbeDeviceTest`) will open `@YouTube` and a
  `/channel/UC…` URL and list the Videos tab, and log the exception if any.

## 4. Target (better than both)

- Every uploader shown opens the channel: the watch-page row, cards, and history cards (E1, E3). When
  `channelUrl` is unknown (old history), resolve it from the video (`StreamInfo.uploaderUrl`, already
  loaded for the watch page) instead of ignoring the tap.
- Search returns channels as their own row type (E2). Channel links are routed by link type (E4), as
  in NewPipe.
- The channel page opens at once with the name and avatar of the item that was tapped, then fills in.
  An extractor failure shows a retry card over that header, not an empty screen. This is better than
  NewPipe's all-or-nothing fragment.
- Migration: old history entries without `channelUrl` still load (the field is optional).

## 5. Tests

- Unit: `YtHistoryTest`: `channelUrl` round-trips, and old JSON without it still decodes (E3).
- Unit: link routing: `/@x`, `/channel/UC…`, `/c/x`, `/user/x` are channels; `watch?v=` is a stream (E4).
- Unit: search mapping keeps `ChannelInfoItem` as a channel row (E2).
- Device (network): `YtChannelProbeDeviceTest`. The channel page opens from the watch page row and
  lists videos (E1, E5).
