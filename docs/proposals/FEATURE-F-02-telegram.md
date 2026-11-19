# F-02 Telegram: public channels and post links, no login (ADR-042)

Status: **Built, not tested** (batch A of PROGRAM-2026-11-19). Flag `telegram_public` (kill switch).
The owner decided on 2026-11-19 at 22:23 +06: "go with no login system like other but with a better
experience". So there's no TDLib and no Telegram account; option B below is closed by that decision. It
isn't silently dropped.

## 1. Current code (read 2026-11-19, head cf40512b)

- Before this item, Telegram showed up only as community links (`notify/Reminders.kt`). A pasted `t.me`
  post went to the generic page probe, a private `t.me/c/…` link was probed too, and a saved `t.me`
  site opened the page browser.
- Player recovery path that gets reused: `RecoveryRouter` returns `RefreshLink` on a refused stream or a
  network error when a `StreamRefreshers` refresher owns the request, at most twice. Then
  `VideoPlayerModal` restarts the fresh request at the saved position.

## 2. Competitors (ideas and contracts only; no code copied)

| Project | Commit / tag | Files read | What it does |
|---|---|---|---|
| Hikari [codegeasse1/hikari](https://github.com/codegeasse1/hikari) (GPL-3.0) | b186ab9b02531c287373d67637f31795671fb0bc (first read at 8aa1652b) | `app/src/main/java/com/hikari/app/telegram/{TelegramWeb.kt, TelegramLinks.kt, TelegramChannels.kt, TelegramError.kt, Td.kt, TdStream.kt}`, `ui/screens/TelegramScreen.kt`, `jniLibs/README.md` | Two layers. Web preview (`t.me/s/<name>`, `?before=` paging, `?q=` search, saved post links incl. `tg://resolve`, `?single`/`?comment`; document file names as titles; `data-src`/poster fallbacks; counts "unpublished" videos). Optional TDLib login with the user's own api_id/api_hash; `TdFileDataSource` streams by byte offset and repairs file references; prebuilt `libtdjni.so` from Telegram X 0.29.0.1813 (arm64 20,060,432 B, armv7 14,264,480 B, plus libsslx/libcryptox/libc++). |
| FreshRSS-xExtension-TelegramChannels [shcheglovnd](https://github.com/shcheglovnd/FreshRSS-xExtension-TelegramChannels) | 6ebe781e | `lib/TelegramChannelsPage.php` | Reads the same `t.me/s/` preview (message wrap, `data-post`, date) to make a feed. |
| TMPlayer [dracu-lah/TMPlayer](https://github.com/dracu-lah/TMPlayer) | e674f784 | repository tree, README | Account-based player (TDLib/MTProto); no public-preview mode. |
| HardPlay [hariomgupta70427/HardPlay](https://github.com/hariomgupta70427/HardPlay) | 1581661728c4 | README, tree | Telegram-bot / account based streaming. |
| telegramedia [kernoeb/telegramedia](https://github.com/kernoeb/telegramedia) | 4666a08f111c | README, tree | Server that needs an account session. |

What Hikari's web layer doesn't do (read in `TelegramWeb.kt` / `TelegramScreen.kt`): there's no refresh
when the preview's temporary file address expires, no page cache, no channel header (photo,
subscribers, description), no "new since the last visit" marks for public channels (its unread counts
come from TDLib), and nothing to do with an app-only post.

## 3. Options and owner decision

| Option | Gives | Cost / risk | Decision |
|---|---|---|---|
| A. Public preview (no login) | Public channels, post links, search, paging | Parser can break when Telegram changes the page; public only | **Build** |
| B. TDLib login | Private chats, big files, seeking | +14–25 MB per ABI; 30–80 MB native memory while running; full account session in the app; api_id needed ([Telegram API terms](https://core.telegram.org/api/terms)); [unofficial-client accounts are observed](https://core.telegram.org/api/obtaining_api_id) | **Not built: owner decision 2026-11-19 22:23 +06** |
| C. Post link only | One pasted post plays | Smallest | Part of A |

## 4. Design (built)

- `online/telegram/TelegramPublic.kt`:
  - `parse()` handles `@name`, t.me / telegram.me / telegram.dog, `/s/`, `tg://resolve|privatepost|join`, and the reserved paths.
  - `parsePage()` reads posts, video, document title, thumb/poster, date, `data-before`, and the header: photo, subscribers, description.
  - `fetchChannel()` has a 15 s limit and a 10-minute LRU cache of 8 pages. `fetchPost()` is never cached.
- `stream/LinkHosts.kt` has new routes `TelegramPost` / `TelegramPrivate`. They apply only to a full
  address and only when the flag is on. `StreamResolver.resolveTelegram` handles a post, and a private
  link fails with a clear message and makes no request.
- `online/telegram/TelegramPlayback.kt` is a `StreamRefreshers` refresher. An expired file address
  (403/410) re-reads the post, and playback resumes at the same position.
- `online/telegram/TelegramSeen.kt` stores one Int per channel in `yu_sites` (the newest post seen).
- `ui/screens/sites/TelegramChannelScreen.kt` has:
  - the channel header and a search box (Telegram's own `?q=`; Back clears the search);
  - older pages that load on their own at the end of the list (at most 5 per opening, and they stop after a failure), with the "Older posts" button kept for remotes and screen readers;
  - "New" marks since the last visit;
  - a card with "Open in Telegram" for app-only posts;
  - honest errors with Retry.
- Saving `@name` or a t.me channel link in Web sites saves `https://t.me/s/<name>` named "@name".

Better than the competitors read: expired links refresh with resume; the header, new marks and
app-only hand-off are added; pages are cached; private links never trigger a request.

## 5. Feature profile

| Field | Value |
|---|---|
| Purpose | Watch videos from public Telegram channels and pasted post links, without an account |
| Entry points | Web sites → add `@name` / t.me link → channel screen; any pasted t.me post link (Stream, share intent) |
| Storage / migration | Saved channel = an existing `SavedSites.Site` (no schema change); `tg_seen_<channel>` Int in `yu_sites`; no migration |
| Permissions | None new (INTERNET already) |
| Settings | Feature flag `telegram_public` (default on, kill switch) |
| Backup / restore | `yu_sites` is a Library file in `PrefSections` → saved channels and seen marks go with Library backup/restore |
| Reset | Library reset clears `yu_sites`; removing a saved channel forgets its seen mark |
| Errors / offline | Offline → "Couldn't reach Telegram…" + Retry; unknown/private channel → "no public posts…"; app-only post → explanation + Open in Telegram; layout change → empty list + message, no crash |
| Accessibility | Rows are buttons with click labels ("Play" / "Why it can't play"); text contrast from the palette; the Older posts button stays alongside the automatic loading |
| TV | Rows, search, Older posts and Open in Telegram are focusable buttons; checked on both TV images in pass-after |
| Low-end | No work at startup (refresher registration only); one HTTP GET per page; page cache of 8 entries; thumbnails through the existing Coil cache |
| Performance budget | Channel page ≤ 15 s hard limit (normally one GET of about 100–200 KB of HTML); parse off the main thread; 0 ms added to cold start |
| Inventory | INVENTORY rows: F-02 channel screen, F-02 post link, F-02 expired-link refresh |

## 6. Side effects

- Stability: the new code runs only when a Telegram link or channel is opened. Every failure path returns
  a message, never throws (unit-tested).
- Battery: no background work, polling or service.
- Startup: one object registration in `MTApplication` (no I/O).
- Memory: at most 8 cached parsed pages (each a few KB of strings).
- Existing features: other links keep their routes (the full `LinkHostsTest` runs in CI). With the flag
  off, t.me links take the old path (`flagOffKeepsTheOldPath`).

## 7. Service terms

The app reads only the public web preview that Telegram shows to any browser: no login, no API, and
no access to private chats. A post that Telegram limits to its apps stays limited, and the user is sent
to Telegram. DRM, authentication and access controls aren't touched.

## 8. Tests

- Unit tests (fail-before = compile failure, the classes are missing):
  - `TelegramPublicTest`: links, page parse, header, new marks, cache, errors, search/paging.
  - `TelegramAndCloudRoutesTest`.
- Device tests:
  - `TelegramPublicDeviceTest`: saved channel → list, header, new marks, app-only card, search, play.
  - `TelegramPlaybackDeviceTest`: a post link plays, and an expired 403 token is refreshed to a first frame.
  - These run on both phones and both TVs (network + player).
- Owner/community test: a real public channel (OMT, Yumeiro users section).
