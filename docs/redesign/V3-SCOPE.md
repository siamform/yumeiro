# Round 3 scope: screen-by-screen audit

Every area named in the round 3 brief (§8) was checked against the code. Only areas that exist in
the app are listed. "v3" means changed in round 3; "v2"/"v1" means the current design comes from an
earlier round and was audited but not reworked again in round 3. Nothing was removed anywhere.

| Area | Exists? | Round 3 status | What changed in round 3 / notes |
|---|---|---|---|
| Navigation (bottom bar, 4 tabs) | Yes | **v3** (motion) | Tab changes slide by a third and fade, using M3 emphasized easing (300ms); fade only with reduced motion. Tabs are tour targets. Look from v2 |
| Header (top bar) | Yes | **v3** | On Music and Videos the title is the source selector (chevron rotates, title animates on change). The "◆ 夢色 YUMEIRO" overline now appears **only on Home** |
| Home | Yes | **v3** | Context line from real data, New episodes carousel + empty state, three foldable spaces holding all 12 shortcuts, foldable rows with "See all", "Take the tour" on the setup card. Details in ROUND-3 §A |
| Music: On device | Yes | **v3** (selector) | Source moved from permanent mode cards to the title menu. Tracks/Albums/Artists/Folders/Favourites tabs, sort, selection and the permission card unchanged |
| Music: Online (YouTube Music) | Yes | **v3** (states) | Offline notice at the top when there is no connection |
| Music: Mix | Yes | v2 | Reached from the menu |
| Videos: Yumix (extensions) | Yes | **v3** (selector, tour, states) | Offline notice; market/extension buttons are a tour target. "Get started" (no extensions) and "All extensions are turned off" states were already there and are kept |
| Videos: YouTube | Yes | **v3** (states) | Offline notice; "Watch again" history stays visible |
| Videos: Device | Yes | v2 | Never shows the offline notice (works offline) |
| Videos: Live TV / IPTV (`StreamScreen`) | Yes | **v3** (states) | Offline notice. Screen itself from v1/v2 |
| Now Playing, mini player | Yes | v1 | Audited; unchanged in round 3 |
| Video player | Yes | v1 (kit) | Audited; unchanged |
| Search (library search, online search) | Yes | v1 | Audited; unchanged |
| Extensions, Extension market, Sources hub | Yes | v1/v2 | Audited; unchanged. Their own empty/guide states exist (`GuideCard`, `online_empty`) |
| Local media permission | Yes | existing | `PermissionPromptCard` on Music; setup checklist step "Play your music and videos". Unchanged |
| Playlists, Library (Music/Videos/Reading) | Yes | v1 | Audited; unchanged. Library is a tour stop |
| Downloads | Yes | v1 | Audited; unchanged. Offered as an offline exit |
| Manga (home, title, reader) | Yes | **v3** (states) | Offline notice when sources exist but the phone is offline; "Nothing here." replaced by a proper empty state with guidance. "No manga sources yet" state kept |
| Anime / series episodes | Yes | **v3** on Home | The carousel shows the user's own My List series. The title page (`OnlineDetailScreen`) is unchanged |
| Settings home + 20 categories | Yes | v1 (+ v3 copy) | The Setup guide category now says "Explore Yumeiro again, or open the quick-setup checklist" and finds "explore". Other categories unchanged |
| Equalizer | Yes | v1 | Unchanged |
| Watch together, tracking, backup/restore, source health | Yes | v1 | Audited; unchanged |
| First launch | Yes | **v3** | New welcome with Explore / Skip, Explore tour, checklist kept as a second page. Choice remembered |
| Dialogs, sheets, menus | Yes | v1 + **v3** | New `SourceMenu` (in-window panel with scrim, staggered rows, check mark, Back closes it). Other dialogs/sheets unchanged |
| Inbox / notifications | Yes | v1 | Tour stop ("Alerts and settings"). Unchanged |

## States coverage (brief §7)

| Screen | Loading | Empty | Offline | Missing configuration | Permission | Error | Success |
|---|---|---|---|---|---|---|---|
| Home | — (rows appear when data arrives) | **v3** episodes empty state; empty rows hidden | **v3** context line + Moods offline hint | **v3** "Add a Yumix source first" | via setup card | Moods "retry" (existing) | — |
| Music on device | existing | existing | works offline | — | existing card | existing | — |
| Music online | existing | — | **v3** notice | — | — | existing | — |
| Yumix | existing skeleton | existing | **v3** notice | existing Get started | — | existing provider error | — |
| YouTube | existing | — | **v3** notice | — | — | existing | — |
| Live TV | existing | existing ("no playlists") | **v3** notice | existing market link | — | existing | — |
| Manga | existing spinner | **v3** "Nothing in this list" | **v3** notice | existing "No manga sources yet" | — | existing retry | — |
| Explore tour / welcome | — | — | works offline | — | — | — | "You're ready" stop |

Success feedback for actions (downloads, installs, saves) uses the existing `AppMessageHost`
messages; round 3 did not change them.
