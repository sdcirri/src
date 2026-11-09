BEGIN;

ALTER TABLE src_messages ADD COLUMN read BOOLEAN;
UPDATE src_messages SET read = false;
ALTER TABLE src_messages ALTER COLUMN read SET NOT NULL;
ALTER TABLE src_messages ALTER COLUMN read SET DEFAULT false;

COMMIT;
