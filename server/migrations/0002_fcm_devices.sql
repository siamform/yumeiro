-- Yumeiro Cloud: add FCM device table (instant notices). Safe to run more than once.
-- npx wrangler d1 execute yumeiro --remote --file=migrations/0002_fcm_devices.sql
CREATE TABLE IF NOT EXISTS fcm_devices (
  id          TEXT PRIMARY KEY,
  token_hash  TEXT NOT NULL,
  fid         TEXT NOT NULL UNIQUE,
  topics      TEXT NOT NULL DEFAULT '',
  app_version INTEGER NOT NULL DEFAULT 0,
  channel     TEXT NOT NULL DEFAULT 'release',
  created     INTEGER NOT NULL,
  seen        INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS fcm_devices_seen ON fcm_devices(seen);
