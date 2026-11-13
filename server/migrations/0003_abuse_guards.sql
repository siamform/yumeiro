-- Yumeiro Cloud R11: abuse guards. Safe to run more than once.
-- npx wrangler d1 execute yumeiro --remote --file=migrations/0003_abuse_guards.sql
-- A proposed change to a confirmed airing time, waiting for a second device to agree.
CREATE TABLE IF NOT EXISTS airing_pending (
  media_id  INTEGER PRIMARY KEY,
  at        INTEGER NOT NULL,
  device_id TEXT NOT NULL,
  reported  INTEGER NOT NULL
);
-- When each device last replaced its follow list (cooldown against write-budget burning).
CREATE TABLE IF NOT EXISTS follow_changes (
  device_id TEXT PRIMARY KEY,
  at        INTEGER NOT NULL
);
