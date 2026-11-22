# Release 2.1.0 images

Made by `python3 scripts/release_kit_210.py <shots-dir> docs/assets/release-2.1` (run from the repo root). Captions:
docs/release-2.1/SOCIAL-CAPTIONS.md. Changelog PDF: `python3 scripts/release_pdf_210.py <proof-dir> docs/assets/release-2.1 <out.pdf>`.

Every phone screen is a real app screen. Where each one comes from:

| Shot | Source |
|---|---|
| dl_popup | PopupQualityDeviceTest, run 44929260054 (popq_a_popup.png) |
| home_shortcuts | MoreDestinationsDeviceTest, run 44926513936 (ol9_quick_card_profiles.png) |
| telegram_page | F02SplitDeviceTest, run 44929260054 (f02s_telegram.png) |
| live_country | LiveCountryDeviceTest, run 44929260054 (cty_bd.png, sample playlist) |
| website_panel | StreamWatchDownloadDeviceTest, run 44926513936 (wdl_a_panel.png, local test page) |
| stats | ReleaseFeaturesDeviceTest, run 44912687672 (release_stats.png) |
| extensions_home, video_controls, music_playing, whos_watching, settings_home, telegram_channel | 1.9.0/2.0.0 screens from yumeiro-web/public/shots (same design; these screens did not change in 2.1.0) |

Sizes: hero-readme 1600x800, banner-github 1280x640, og-website 1200x630, x 1600x900, posts 1080x1350,
squares 1080x1080, story 1080x1920, changelog-card 1080x1350.

2026-11-22 (release audit, AUD-CLAIM-1): post-05 and square-05 said "A curated list of working channels per country",
but the curated lists (E2) are not published and not in the app. Only that bullet was repainted, with the script's own
background (`bg()`) and font: "Pick a country, see only its channels". The script text matches; every other pixel is unchanged.
