# F-16 Yumix sections and banner tags (absorbs F-01)

Status: **Built, not tested** (commits 6ee4283a test, 2d3c4ef6 feat; F-01 tap: 79532b29, 961fee20). Fail-before
CI 44489413319 (unresolved `heroTags` / new categories). Pass-after: pending CI on r15-stability.

- **Ask:** sections Movies, Series, Web series, Dual Audio, Sub, Dub; quality and audio tags (HD, 4K, Sub, Dub, Dual)
  on banners and posters; a tap on a tag opens the title with that version (F-01).
- **Competitors:** docs/research/2026-11-19-workstreams-A-B-F.md (CloudStream Sub/Dub marks).
- **Design:** `YumixFeed.Category.tagged` reads the poster `ReleaseTags` already parsed and cached for posters
  (Dual/Multi audio, Sub, Dub, "Dubbed" in the name, "Web series"); chips after Movies and Series; the banner line
  uses `YumixFeed.heroTags`. F-01: phones get a clickable version chip (`poster_version_tag`), the detail page
  preselects the version (`VersionHint`).
- **Performance:** no new network, no new store; one pass over the items already in memory.
- **Side effects:** none outside Yumix; TV keeps the plain chips (no extra focus stops).
- **Flags:** `f16_yumix_tag_sections` (and F-01 inside it). **Tests:** F16SectionsTest, F01TagPreselectTest.
