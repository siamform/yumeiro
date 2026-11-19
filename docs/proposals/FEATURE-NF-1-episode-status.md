# NF-1 Episode status: Watched, minutes left, Fresh

Status: **Built, not tested** (6ee4283a test, 66c52247 feat). Fail-before CI 44489413319. Pass-after pending.

- **Ask:** each episode row shows Watched, In progress (with position) or Fresh (new since the last visit).
- **Competitors:** CloudStream EpisodeAdapter (docs/research/2026-11-19-workstreams-A-B-F.md).
- **Design:** `online/EpisodeStatus` from the BUG-1 resume store: Watched, InProgress (minutes left, rounded up),
  Fresh = episode number (season-aware) above the highest seen on the last visit, kept per title + version in
  prefs `episode_seen` (only grows, 400 titles max), read and written on IO. Rows show text; grid tiles a dot
  with TalkBack text (never colour alone).
- **Performance:** one small prefs read per detail page; no network. **Flag:** `nf1_episode_status`.
- **Unhappy paths:** first visit = nothing Fresh; incognito does not store the seen mark; a source that renumbers
  episodes can show one extra Fresh until the next visit. **Tests:** NF1EpisodeStatusTest.
