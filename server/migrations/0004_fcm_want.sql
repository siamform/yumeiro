-- Yumeiro Cloud NOTIFY-1 (ADR-039): what each phone's switches ask for, so a broadcast also reaches a
-- phone whose topic subscription failed. Run once:
-- npx wrangler d1 execute yumeiro --remote --file=migrations/0004_fcm_want.sql
ALTER TABLE fcm_devices ADD COLUMN want TEXT;
