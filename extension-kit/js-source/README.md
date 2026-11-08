# JavaScript source template (Mangayomi format)

Yumeiro runs [Mangayomi](https://github.com/kodjodevf/mangayomi)-style JavaScript sources in a
sandboxed QuickJS engine. A source is one `.js` file listed in an `index.json`; host both on GitHub
and add the raw `index.json` URL under Sources → JavaScript.

```
index.json          list of sources in your repo
sample-manga.js     a manga source (popular, latest, search, details, pages)
```

## What a source returns

| Method | Returns |
|---|---|
| `getPopular(page)` / `getLatestUpdates(page)` / `search(query, page, filters)` | `{ list: [{ name, imageUrl, link }], hasNextPage }` |
| `getDetail(url)` | `{ name, imageUrl, description, author, status, genre: [], chapters: [{ name, url, dateUpload }] }` (anime: `episodes` instead of `chapters`) |
| `getPageList(url)` | manga: `["https://…/1.jpg", …]` or `[{ url, headers }]` |
| `getVideoList(url)` | anime: `[{ url, originalUrl, quality, headers, subtitles: [{ file, label }] }]` |

Helpers available inside the sandbox: `new Client().get(url, headers)` → `{ body, statusCode }`,
`new Document(html)` with `select`, `selectFirst`, `attr`, `text`; `unpackJs`, `cryptoHandler`,
`parseDates`, `substringAfter`/`Before`. Network calls go through the app, so cookies and the
Cloudflare helper are shared with the rest of Yumeiro.

## Fields in `index.json`

`itemType`: `0` manga, `1` anime, `2` novel. `sourceCodeLanguage`: `1` (JavaScript). Bump `version`
whenever you change the code; Yumeiro offers an update.
