# Yumeiro extension starter kit

Yumeiro doesn't host or ship any content. Everything you watch or read comes from sources that the
community writes and hosts. This folder helps you build one, for free, with no server of your own.

| You want to add… | Format | Start here | Hosting (free) |
|---|---|---|---|
| Movies, series, anime, sports, live TV | **Stremio add-on** (HTTP + JSON) | [`stremio-addon/`](stremio-addon/) | Static JSON on GitHub Pages, or any free Node host |
| Manga or anime from a website | **JavaScript source** (Mangayomi format) | [`js-source/`](js-source/) | One `.js` file + `index.json` on GitHub |
| A full video provider with extractors | **CloudStream plugin** (`.cs3`) | [`cloudstream/`](cloudstream/) | GitHub repo + `repo.json` |
| Manga/anime you already wrote for Mihon/Aniyomi | **Tachiyomi APK extension** | [Notes below](#aniyomi--mihon-extensions) | Your existing extension repo |

## How each one shows up in Yumeiro

- **Stremio add-on**: Settings → Sources, tracking & backup → Sources → Stremio → paste the
  `manifest.json` URL. Its catalogs appear under Videos → Online like any other source.
- **JavaScript source**: Sources → JavaScript → add your repo's `index.json`, then install.
- **CloudStream**: Videos → Online → Extensions → add the `repo.json` (or a short code).
- **Aniyomi / Mihon**: Sources → Aniyomi or Mihon → add your `index.min.json` repo. Yumeiro shows the
  APK's signing fingerprint and asks the user to trust it before any code runs.

## Rules for listing in the public catalogs

1. No content hosted in this repository, ever. Sources fetch from public websites.
2. Respect the sites you read: identify a user agent, keep requests low, cache where you can.
3. Mark adult sources (`isNsfw: true` / `"adult": true`); Yumeiro hides them unless 18+ is on.
4. No trackers, no ads injection, no crypto mining, no obfuscated code. Reviewers reject these.
5. Pick a licence (Apache-2.0, MIT or GPL-3.0 are common) so others can fix your source later.

## Test before you share

- Run Settings → Sources, tracking & backup → **Source status** on your phone; it loads each
  installed source and shows what works, what's slow and what fails, with a shareable report.
- Stremio: open `https://<your-addon>/manifest.json` in a browser; it must be valid JSON with CORS
  (`Access-Control-Allow-Origin: *`).

## Aniyomi / Mihon extensions

Yumeiro loads the same extension APKs (`eu.kanade.tachiyomi.*` API from
[Aniyomi](https://github.com/aniyomiorg/aniyomi) and [Mihon](https://github.com/mihonapp/mihon)).
Nothing changes for authors: keep publishing your `index.min.json` + APKs as you do today. Sources
that need a WebView login or Cloudflare clearance may not work yet; please open an issue with the
Source status report.
