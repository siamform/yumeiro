# PR text for r15-stability -> main (prepared, not opened)

Open only after the owner says "go". Title: `Yumeiro 2.1.0`

---

Yumeiro 2.1.0: stability, one downloader, more sources, Live TV by country.

- What ships: CHANGELOG.md (2.1.0 section). Full list with commits and run IDs: docs/release-2.1/CHANGELOG-FULL.md.
- Audit: docs/RELEASE-AUDIT-2.1.0.md (independent; findings, runs, Not checked list, GO / NO-GO).
- Version 2.1.0 (19) is already in version.properties, so the release job does not bump again.
- Owner list OWN-1..12 (all fixed in this branch): smoother screen changes and faster Yumix -> details -> player
  (OWN-1/6), online works after skipping the tour / fresh install / update (OWN-2), Telegram sign-in with your own
  API ID and hash, still being checked on a real account (OWN-3), Vega / SkyStream links in the market add box (OWN-4),
  managed repos show installed state (OWN-5), Cloudflare default network (OWN-7), one subtitle language for batch
  downloads (OWN-8), official built-in sources shown: Yumeiro Classics (public-domain films) + Yumeiro Drama (OWN-9),
  TeraBox share links (OWN-10), YouTube audio-only download (OWN-11), profile PIN recovery question and current PIN
  to remove (OWN-12).
- Off in this release (not announced as new): built-in torrent engine, curated Live TV lists, per-processor APKs.
- Owner tests before and after: docs/release-2.1/MANUAL-TESTS-2.1.md (OMT-83..92).

Merge with a merge commit (one push to main). That push runs release.yml (tag v2.1.0, signed APKs) and
stream-sync.yml (Live TV data refresh). No Worker or website deploy.

<sub>Created with [Perplexity](https://www.perplexity.ai/computer/tasks/c2ba2617-0069-43e8-ac57-06af6f0c8f37)</sub>
