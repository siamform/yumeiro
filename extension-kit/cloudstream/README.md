# CloudStream plugins in Yumeiro

Yumeiro runs CloudStream `.cs3` plugins through a compatibility layer, so the normal CloudStream
workflow applies unchanged:

1. Start from the official template:
   [recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins).
2. Build with GitHub Actions (the template ships a workflow) or locally with `./gradlew make`.
3. Publish a `repo.json` that points at your `plugins.json`; see
   [Create your own JSON repository](https://recloudstream.github.io/csdocs/devs/create-your-own-json-repository/).
4. In Yumeiro: Videos → Online → Extensions → add the `repo.json` URL.

To be listed in Yumeiro's Extension Market, open a pull request that adds your repo URL to
[`.github/extensions/sources.json`](../../.github/extensions/sources.json). The market workflow checks
the manifest, class name, file size and SHA-256 before mirroring it.

Things Yumeiro adds on top: sub/dub choice, seasons, a crash guard (one broken plugin can't crash
the app), and automatic header/referer retries when a stream is refused.
