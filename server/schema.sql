-- Yumeiro Cloud (D1). Only what push needs: a device's push endpoint and the AniList ids it follows.
-- No accounts, no names, no IP addresses, no watch history.
CREATE TABLE IF NOT EXISTS devices (
  id         TEXT PRIMARY KEY,          -- random, given to the app at registration
  token_hash TEXT NOT NULL,             -- SHA-256 of the device's secret token
  endpoint   TEXT NOT NULL UNIQUE,      -- UnifiedPush endpoint (https)
  follows_v  TEXT NOT NULL DEFAULT '',  -- hash of the follow list, so unchanged lists aren't rewritten
  created    INTEGER NOT NULL,
  seen       INTEGER NOT NULL           -- last check-in (unix s); devices silent 45 days are deleted
);
CREATE TABLE IF NOT EXISTS follows (
  media_id  INTEGER NOT NULL,           -- AniList media id
  device_id TEXT NOT NULL,
  PRIMARY KEY (media_id, device_id)
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS follows_device ON follows(device_id);
CREATE TABLE IF NOT EXISTS state (k TEXT PRIMARY KEY, v TEXT NOT NULL);
-- Next airing time per show (shared, not per device). Phones report it from AniList when they check in;
-- the server can't ask AniList itself (AniList blocks Cloudflare Workers). After an episode fires the
-- time moves on a week; `rolls` counts those guesses and stops after 2 without a fresh report.
CREATE TABLE IF NOT EXISTS airing (
  media_id INTEGER PRIMARY KEY,         -- AniList media id
  at       INTEGER NOT NULL,            -- unix s of the next episode
  rolls    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS airing_at ON airing(at);
