# Stremio add-on template (static, free hosting)

A Stremio add-on is a few JSON files served over HTTPS. This template needs no server: push it to a
GitHub repo, turn on GitHub Pages, and the add-on URL is
`https://<you>.github.io/<repo>/manifest.json`.

```
manifest.json                       what the add-on offers
catalog/movie/sample-movies.json    a catalog (list of titles)
meta/movie/sample-1.json            one title's details
stream/movie/sample-1.json          playable streams for that title
```

Protocol reference: [Stremio add-on protocol](https://stremio.github.io/stremio-addon-sdk/protocol.html).

## Tips

- IDs: use your own prefix (`yum_`, `mysite_`; avoid `:` in file names on static hosts) or IMDb ids (`tt1234567`) to merge with Cinemeta.
- Streams can be `url` (direct HTTP/HLS), `ytId` (YouTube) or `externalUrl` (opens a browser).
  Yumeiro skips `infoHash` (torrent) streams.
- For search or dynamic results you need code: the official
  [stremio-addon-sdk](https://github.com/Stremio/stremio-addon-sdk) runs on free tiers such as
  Vercel or Cloudflare Workers.
- Only link to media you are allowed to share. The sample uses a public-domain film from the
  Internet Archive.
