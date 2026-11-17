# F-02 Telegram option (proposal only; gated; owner decision OMT-25)

Status: **proposal, not built.** Part 8 gate not passed (4.4). Terms and privacy are an owner call (OMT-25).

## 1. Current code (read 2026-11-18, head 52f88de)

- Telegram appears only as community links: `notify/Reminders.kt:27-28` (channel and group `t.me` links).
- No Telegram source, no TDLib, no `t.me` handling in `stream/LinkHosts.kt` (a pasted `t.me` post link
  goes to the generic link finder).

## 2. Competitor (ideas and contracts only; Hikari GPL-3.0, no code copied)

Hikari `codegeasse1/hikari` at 8aa1652b2af151aca85be316b926cbabee742e63, `app/src/main/java/com/hikari/app/telegram/`:

- `TelegramWeb.kt` (256 lines): **no login** path. Reads a public channel's web preview
  `https://t.me/s/<name>` (`?before=` paging, `?q=` search), parses video posts and their CDN file URL;
  counts posts Telegram withholds from browsers; `normalize` accepts `@name`, `t.me/name`, `t.me/s/name`,
  post links.
- `Td.kt` (1,272 lines) + `TdStream.kt` (309): **login** path with TDLib; the user enters their own
  `api_id`/`api_hash` (`Td.kt:208-219`), phone, code, password states; `TdFileDataSource` streams a file to
  Media3 by asking TDLib for byte windows and repairs expired file references.
- `TelegramChannels.kt`, `TelegramLinks.kt`: saved channels and post links (`t.me/c/<id>/<post>` for private).
- Native cost: `jniLibs/arm64-v8a/libtdjni.so` 20.1 MB, `armeabi-v7a` 14.3 MB; `TdApi.java` 5.2 MB source.

Weaknesses: TDLib adds ~14–20 MB per ABI to the APK and a resident native client (memory on 2 GB phones,
TV); the user must create a Telegram developer app; the web-preview path breaks whenever Telegram changes
the page and only covers public channels.

## 3. Options

| Option | What it gives | Cost / risk |
|---|---|---|
| A. Public channel preview (no login) | Watch videos posted in public channels; save channels | HTML parsing can break; public only; fits 200k users |
| B. TDLib bring-your-own account | Private chats/channels, large files, seeking | +14–20 MB APK per ABI, native memory, account security, Telegram API terms; split APK or on-demand module required |
| C. Link only | `t.me/<channel>/<post>` pasted → player via A's parser | Smallest; one link at a time |

Recommendation: **C then A**, behind flag `telegram_public`, off by default. B only as a separate
dynamic-feature module if the owner accepts the size and the terms.

## 4. Design (A/C)

- `LinkHosts.classify`: `t.me/<name>/<post>` → new route `TelegramPost` (pure, unit-tested).
- `TelegramPublic` (online/telegram): fetch the post's `?embed=1` page or `t.me/s/<name>` through OkHttp,
  parse the video URL, 15 s timeout, cache channel pages 10 min.
- Saved channels: list like Web sites (F-04), in SharedPreferences with backup in the SOURCES section.
- Playback: the CDN URL goes to the existing player; download through the existing download path.
- No data leaves the phone except the request to `t.me` (stated in the help text).

## 5. Action–reaction

| Action / event | Reaction |
|---|---|
| Paste a public post link | Plays the video; title from the caption |
| Paste a private (`t.me/c/…`) link | Clear message: needs a Telegram login (not supported) |
| Post has no video / withheld | Message "Telegram does not publish this file to browsers" |
| Page layout changed | Parser returns nothing → message and a crash-free fallback to the link finder |
| Offline / slow | Timeout message, cached page if any |
| Flag off | Telegram links go to the generic link finder as today |

## 6. Pros and cons

- A/C: + no login, no native library, small; − public only, fragile parser.
- B: + full Telegram; − APK size, memory, account risk, terms.

## 7. Tests (when approved)

Unit (pure): `t.me` link classification and HTML parsing from saved fixtures (fail-before: classify returns
generic). Device: fixture-served page on api33-lowram and tv-api34; Back/rotate/kill during load.

## 8. Owner decision (OMT-25)

Plan A/C? Is a user login (B, TDLib, own api_id) ever acceptable?
