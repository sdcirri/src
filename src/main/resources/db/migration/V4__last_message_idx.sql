BEGIN;

CREATE INDEX idx_messages_chat_timestamp_id ON messages (chat_id, timestamp DESC, message_id DESC);

COMMIT;
