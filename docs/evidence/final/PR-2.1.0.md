# PR text for release-2.2.0 -> main (ships as 2.1.0)

Title: `Yumeiro 2.1.0`

---

Yumeiro 2.1.0: stability, one downloader, more sources, Yumeiro Hub, Yumeiro Live, per-processor APKs and the updater ARM check. (The branch is named release-2.2.0; the owner set the version back to 2.1.0 on 2026-11-23.)

- What ships: CHANGELOG.md (2.1.0 section). Full list with commits and run IDs: docs/release-2.1/CHANGELOG-FULL.md.
- Plan and status: docs/MASTER-PLAN-2.2.0.md. Open items: docs/OPEN-ITEMS-2.1.0.md. Audit: docs/RELEASE-AUDIT-2.1.0.md.
- Version 2.1.0 (20) is already in version.properties, so the release job does not bump again.
- Owner list OWN-1..12: see the 2.1.0 items in the changelog (all fixed).
- Owner list OWN-13..19: one built-in "Yumeiro" source (OWN-13), close motion matches open (OWN-14), locked profile
  no longer force-closes or sticks on the logo after a cold start (OWN-15), Upcoming search, filter icon and
  data-built filters (OWN-16), open-items cross-check (OWN-17), size / quality / sub-dub on every download popup
  (OWN-18), full-screen player with only the main buttons (OWN-19).
- E1 Yumeiro Hub: catalog shelves from Yumeiro Cloud (/v1/hub/*, already deployed); play goes through the user's
  installed sources. E1-C recipes are not in 2.1.0.
- E2-ALL Yumeiro Live: daily checked all-country Live TV list on the streams branch, second default playlist.
- Per-processor APKs are on (repo variable YUMEIRO_ABI_SPLITS=true): universal + arm64-v8a + armeabi-v7a (+ x86).
- Off in this release (not announced as new): built-in torrent engine (seek 7.8 s / 13 s vs 5 s budget, run
  45047541362), curated per-country Live TV lists.
- Not checked (reason): NF1 and DramaPlaybackDeviceTest on device (onshort.net unreachable from CI, run 45049508477);
  Firebase Test Lab (403, APIs not enabled); real-phone tests in docs/release-2.1/MANUAL-TESTS-2.1.md.
- Owner tests: docs/release-2.1/MANUAL-TESTS-2.1.md.

Merge with a merge commit (one push to main). That push runs release.yml (tag v2.1.0, signed APKs) and
stream-sync.yml. The Worker is already deployed; no website deploy.

<sub>Created with [Perplexity](https://www.perplexity.ai/computer/tasks/c2ba2617-0069-43e8-ac57-06af6f0c8f37)</sub>
