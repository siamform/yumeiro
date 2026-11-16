# BUG-4: Playback quality list differs from the download list (Step A.5 note)

Status: investigation note plus the first two root-cause fixes (F1, F2). Ledger row: BUG-4. Date: 2026-11-16.
Owner report: the same Yumix / YouTube video sometimes shows only one playback quality, while
downloading it reveals several. Every available resolution must be detected, displayed,
selectable and really applied; the upscaling path must be proven by frame capture.

## 1. Implementations read

| Project | Version read | Files |
|---|---|---|
| CloudStream (recloudstream/cloudstream) | master @ `5d66bbeaf3b35c0c18ad2e0b9ce6b0aea4f5241c` | `ui/player/GeneratorPlayer.kt` (`showMirrorsDialogue`, `showTracksDialogue`, `sortLinks`, `currentLinks` observer), `ui/player/CS3IPlayer.kt` (`getVideoTracks`, `setMaxVideoSize`), `ui/download/DownloadButtonSetup.kt` |
| Hikari (codegeasse1/hikari) | main @ `9694add539570bd78cb279175e0b45417ede0051` | `download/DownloadEngine.kt` (`hlsQualities`, `pickVariant`), `download/BatchServerPlanner.kt` (`heightOf`, `filter`), `data/TitleQuality.kt`, `player/StreamsLive.kt`, `player/ServerStreamsPage.kt` |
| Yumeiro | `r15-stability` @ f4133da | `player/QualityEngine.kt`, `ui/screens/video/VideoPlayerModal.kt` (alternatives effect, quality dialogs), `ui/screens/video/VideoPlayerDialogs.kt` (`qualityOptions`), `youtube/YouTubeStreams.kt` (`videoRequest`, `manifestFile`), `youtube/YouTubePlayback.kt`, `stream/IncomingMedia.kt`, `stream/StreamRepository.kt`, `online/download/YtDownloads.kt`, `ui/screens/online/DownloadSheets.kt` |

Licences: CloudStream GPL-3.0, Hikari (ideas only). No code copied.

## 2. What each does, and its weakness

CloudStream
- Two separate lists. "Sources" (`showMirrorsDialogue`): every extractor link, sorted by a
  quality profile, re-sorted live from the `currentLinks` observer as links arrive. "Tracks"
  (`showTracksDialogue`): the ExoPlayer video tracks of the current link only, hidden when there
  is one. Downloads use the same link list (link picker in `DownloadButtonSetup`).
- Weakness: the user must understand source vs track; a track pick is `setMaxVideoSize`, a cap,
  not an override, so ABR may still play a lower rendition than the one chosen.

Hikari
- Download side reads the HLS master itself (`hlsQualities`: every variant's height/bandwidth)
  and picks with `pickVariant` (exact height, else nearest lower, else nearest higher). Server
  resolution comes from the server name (`heightOf`, 0 when unknown) so unknown servers still
  appear, just unlabelled.
- Playback uses the player's own track list. So playback and download read the same master
  through different code, which is exactly the class of divergence the owner reports.

## 3. Yumeiro, found by reading the code

Playback list = `qualityOptions(tracks)` (the stream's own ladder) plus `QualityEngine.alternatives`
(YouTube: the download sheet's `YtDownloads.options`; Yumix: other sources grouped by height;
otherwise engine levels "Scaled/Upscaled by Yumeiro"). Download list = `YtDownloads.optionsFor`
(YouTube, every resolution) or Settings quality "(up to)" (Yumix).

- F1 (root cause, YouTube, always): YouTube normally plays from a local single-representation
  DASH manifest (`YouTubeStreams.manifestFile` → `file://…/cache/yt/<id>_<itag>.mpd`). The player
  only built the alternatives request when `isNetworkUri(video.uri)` was true, so YouTube got
  `request = null` → no YouTube alternatives → engine levels only: one real quality while the
  download sheet listed 144p…4K. Fix: `QualityEngine.requestForList(registered, fallback, networkUri)`:
  the request registered for the player URI (it carries the YouTube page URL) wins; the bare
  fallback is used only for real network URIs, so local files still never borrow a stale online
  session.
- F2 (root cause, Yumix, intermittent): the alternatives effect was keyed on `links.size / 5`.
  When the first frame (and `playingHeight`) arrived with one source known, sources 2–4 never
  rebuilt the list (`byHeight.size < 2` → engine levels). That explains "sometimes". Fix: key on
  the link count, the session phase and the playing link URL.
- F3 (open, Yumix): sources whose quality is unknown (0, typical for HLS masters) are dropped from
  the list (`quality in 144..4320`). Hikari probes the master for variants; candidate fix is to
  read the master's `RESOLUTION` like `HlsDownloader` already does for downloads. Not changed yet:
  needs measurement on a real Yumix source list first.
- F4 (open): `qualityOptions` drops tracks the device can't decode (`isTrackSupported`) without
  saying so; the YouTube list labels these ("Plays as … here"), the HLS ladder does not.

## 4. Proof plan

1. Unit (CI): `r15/Bug4QualityListTest` (F1 rule; YouTube manifest request keeps its id; local
   file gets no request; network fallback). Existing `QualityParityTest`, `QualityRound2Test`.
2. Emulator (device-check): open a YouTube video, open the quality dialog, assert ≥3 real heights
   (not "Scaled by Yumeiro"), select each, and read the applied `Format.height` from the player;
   frame-capture an engine upscale level (Anime4K path, `Anime4kGlDeviceTest` covers the GL path).
3. Yumix on CI only where a public test source exists; otherwise OWNER-MANUAL-TESTS after own
   attempts are recorded.
