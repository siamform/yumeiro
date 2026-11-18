# G6-PG2 — Endless Music Home: offline scrolls ended it for good, and it stopped at about 40 shelves instead of 80

Status: Verified (CI unit). fail-before CI 44235994633 (ab/pg2-before: only YmEndlessCapTest red, 40 seeds not 80, of 1168); fix 2114836; pass-after CI 44238631828 green (1192 tests, YmEndlessCap 1/1, YmEndlessOffline 1/1, SmartDns/DnsWire/DataCacheBounds 4/4 each). Offline half has no fail-before (new afterCall API). Emulator network toggle on Home: in progress. Group L2 G6.
Found by code read, 2026-11-18. This was noted in FEATURE-G6-PG1 as "not changed".

## Problem (code read)

`YtMusicBrowse.moreEndless()` and `YmEndless` (Round 13 endless Home, "Similar to …" shelves):

1. **Offline ends the feed.**
   - `YouTube.related()` swallows errors and returns an empty list.
   - With no connection, each "load more" therefore consumes a seed (`endlessUsed = use(…)`) and counts a miss (`endlessMisses++`).
   - After 6 misses the gate `endlessMisses < 6` closes for the rest of the session, so the feed does not continue when the connection returns.
   - Offline calls fail at once, so the 6 misses come within seconds of reaching the end of the page.
2. **Cap off by two.**
   - `use()` adds two keys per shelf (the seed id and the artist key).
   - `nextSeed` stops at `used.size >= MAX_SHELVES` (80 keys, about 40 shelves).
   - The UI gate allows `MAX_SHELVES * 2`. The documented intent is 80 shelves.

## Competitors (ideas only, exact tags)

| App | Version / file | On a failed "more" |
|---|---|---|
| ArchiveTune | v15.1.0 `viewmodels/HomeViewModel.kt` `loadMoreYouTubeItems` | `YouTube.home(continuation).getOrNull() ?: return` — state untouched, the next scroll retries |
| Metrolist | v14.0.0 `shared/.../viewmodels/HomeViewModel.kt` `loadMoreChipSections` | Sets `loadMoreError = true`; `usedHomeChips` does not advance, so the same page is retried |

## Fix (least risk)

- `YmEndless.use` stores the artist as `a:<key>`. `seedsUsed(used)` counts only seeds, and `nextSeed` stops at `seedsUsed >= MAX_SHELVES`. The UI gate uses the same count.
- `YmEndless.afterCall(before, after, misses, shelf, offline)` decides the outcome of one call:
  - a shelf → keep the seed, misses = 0;
  - nothing while offline → give the seed back, misses unchanged;
  - nothing while online → keep the seed, misses + 1.
- `moreEndless` uses it, with `offline = !Connectivity.online.value`. An offline result waits 3 s before clearing `more`, so a scroll at the end does not loop calls.

## Action → reaction

| Action | Before | After |
|---|---|---|
| End of Home, offline, scroll | 6 seeds burned, feed closed for the session | No seed used, no miss; "load more" retried every ≥3 s |
| Connection back, scroll | Nothing more (closed) | Next "Similar to …" shelf |
| Long session online | Stops at about 40 shelves | Stops at 80 shelves (as documented) |
| Related answers empty while online | Miss counted (6 → stop) | Unchanged |

## Pros / cons

- Pro: the feed survives tunnels and lifts, the same principle as G6-PG1, and network use stays bounded (80 shelves, at most one call per 3 s offline).
- Con: online, a session can now make up to 80 related calls instead of about 40. That is the documented limit.

## Tests

- Unit, fail-before → pass-after: 41 seeds by different artists. Before the fix, `nextSeed` returns null after 40.
- Unit (new API): `afterCall` keeps the seed offline and counts a miss only online.
  - The offline part has no fail-before against the old code: the decision lived inline in a Composable.
  - That behaviour is recorded from the code read above.
- Emulator: the network-toggle part goes in the next G6 device run (endless Home needs YouTube Music; skip if unreachable).
