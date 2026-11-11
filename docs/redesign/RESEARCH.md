# Yumeiro redesign — research & reference board

Research is inspiration, not permission to imitate. Each entry records what a product does well,
why it works, what Yumeiro takes from it, what we deliberately do not copy, and the original
Yumeiro direction ("Tsuki no Michi", see `DESIGN-SYSTEM.md`). Colours, fonts and the Japanese
night vibe stay exactly as they were.

## Principles (cross-cutting)

| Source | Finding | Applied in Yumeiro |
|---|---|---|
| [Android accessibility — touch target size](https://support.google.com/accessibility/android/answer/7101858?hl=en), [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) | 48×48dp minimum targets | `Yume.touch = 48.dp`; header actions, "See all", mini player play, Home resume buttons and Now Playing tools moved to ≥48dp |
| [NN/g — recognition vs recall](https://www.nngroup.com/articles/recognition-and-recall/) | Visible options beat remembered ones | Labels always shown in the dock and the Now Playing tool rail; every Home destination stays visible as a kana key |
| [NN/g — progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/) | Show the core, defer the advanced | One primary action per screen; secondary actions stay in sheets (song actions, video options) |
| [Hidden navigation patterns](https://www.fernandoux.com/en/wiki/techniques/navigation-patterns/) | Hamburger/hidden nav lowers discovery | No hidden menus added; dock keeps 5 labelled destinations |
| [M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive), [usability](https://m3.material.io/foundations/usability/applying-m-3-expressive), [motion](https://m3.material.io/styles/motion/overview/how-it-works), [shape](https://m3.material.io/styles/shape/overview-principles), [Google research](https://design.google/library/expressive-material-design-google-research) | Emphasis via size/shape/motion helps people find key actions faster; spring motion | Spring "settle" motion tokens; the moon-path indicator glides with a spring; a reduced, intentional shape scale |

## Per-area reference board

### Navigation / shell
- **Apple Music (iOS 26)** — floating tab bar, mini player sits above it, swipe on the mini player changes tracks ([Apple newsroom](https://www.apple.com/newsroom/2025/06/apple-elevates-the-iphone-experience-with-ios-26/), [9to5Mac](https://9to5mac.com/2025/11/04/ios-26-1-gave-apple-music-convenient-new-trick), [Apple support](https://support.apple.com/guide/iphone/use-the-music-player-controls-iph676daac9b/ios)).
  Works because playback is always one gesture away. Learn: swipe-to-skip on the mini player. Don't copy: Liquid Glass translucency. Yumeiro: ink dock with a gold "moon path" stroke and top-edge progress.
- **Plex 2024 redesign** — moved to bottom tabs; users complained about extra taps, missing info and removed features ([TechCrunch](https://techcrunch.com/2024/11/22/plex-redesigns-its-app-to-look-more-like-a-streaming-service/), [Reddit r/PleX](https://www.reddit.com/r/PleX/comments/1j4zl9z/how_we_feeling_about_the_new_ui/), [How-To Geek](https://www.howtogeek.com/plex-roku-app-preview-channel-announcement/), [9to5Mac](https://9to5mac.com/2024/11/22/plex-new-iphone-app-design/)).
  Lesson: never remove or bury power features in a redesign. Yumeiro keeps every route, tag and entry point.

### Home
- **Spotify** — filter shortcuts at the top, feed below ([The Verge](https://www.theverge.com/2022/8/9/23298679/spotify-app-redesign-podcasts-music), [Spotify newsroom](https://newsroom.spotify.com/2023-03-08/new-home-page-scroll-clips-previews/)).
- **Netflix** — Continue watching and My Netflix hub ([Netflix help](https://help.netflix.com/en/node/575087423404644), [Android Authority](https://www.androidauthority.com/netflix-app-new-tab-3348085/), [The Verge](https://www.theverge.com/2024/6/6/24172351/netflix-redesign-homepage-experience-test)).
  Learn: resume first, content sooner. Don't copy: autoplay previews, poster carousels as the whole page. Yumeiro: a "Tonight" panel (つづき) with music and video resume rows, then ten kana keys ("Spaces") instead of a ~600dp bento wall, then the endless For-you feed.

### Music / Now Playing
- **YouTube Music 2025 Now Playing** — controls moved up, compact actions, removing Related broke muscle memory ([9to5Google](https://9to5google.com/2025/09/11/youtube-now-playing-2025-redesign), [9to5Google](https://9to5google.com/2025/12/20/youtube-music-2025-now-playing-redesign/), [Android Headlines](https://www.androidheadlines.com/2025/09/youtube-music-just-gave-its-now-playing-screen-a-big-redesign.html)).
  Learn: put every tool in one predictable place. Don't copy: the carousel or dual-pane queue. Yumeiro: a labelled five-tool rail (Lyrics · Up next · Info · Sleep · EQ). Sleep and EQ moved down from unlabeled header icons. Nothing removed.
- **Spotify Your Library pins** ([Android Authority](https://www.androidauthority.com/spotify-your-library-pins-shelf-test-3732365/)) — pinned shelf idea noted for Library (future).

### Extensions & sources
- **Mihon** — Sources/Extensions tabs, installed first, untrusted section and malware warning ([Mihon FAQ](https://mihon.app/docs/faq/browse/extensions), [getting started](https://mihon.app/docs/guides/getting-started)).
- **Stremio** — add-ons supply everything; manifest URL install ([Stremio add-ons docs](https://docs.stremio-addons.net/addons/install), [Stremio help](https://stremio.zendesk.com/hc/en-us/articles/360021348391-How-to-install-uninstall-Add-ons)).
- **CloudStream** — repo vs extension two-step confuses; bottom provider picker gets mis-tapped ([docs](https://recloudstream.github.io/csdocs/), [issue #3230](https://github.com/recloudstream/cloudstream/issues/3230), [wiki](https://cloudstream.miraheze.org/wiki/List_of_extensions)).
  Learn: trust state must be explicit; keep the source picker at the top. Yumeiro already has SourcesHub tabs and ExtensionReviewHost; the redesign keeps them and applies shared kit styling.

### Live TV
- **TiviMate** — EPG grid, favourites, groups, catch-up; built for TV remotes ([Play Store](https://play.google.com/store/apps/details?id=ar.tvplayer.tv&hl=en_US), [overview](https://iptvrepair.com/tivimate/)).
  Learn: groups + favourites. Don't copy: the TV-first grid on a phone.

### Not yet studied in depth
Jellyfin/Findroid, VLC/MX Player gestures, Disney+, Aniyomi. These are listed as a gap in `HANDOVER-REDESIGN.md`.
