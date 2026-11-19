-- Yumeiro Cloud F-AIR (ADR-043): airing alerts for Firebase phones. Before this, an aired episode only
-- woke UnifiedPush devices. Needs 0003 and 0004 first. Run once:
-- npx wrangler d1 execute yumeiro --remote --file=migrations/0005_fcm_follows.sql
ALTER TABLE fcm_devices ADD COLUMN follows_v TEXT NOT NULL DEFAULT '';
CREATE TABLE IF NOT EXISTS fcm_follows (
  media_id INTEGER NOT NULL,
  fcm_id   TEXT NOT NULL,
  PRIMARY KEY (media_id, fcm_id)
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS fcm_follows_device ON fcm_follows(fcm_id);
