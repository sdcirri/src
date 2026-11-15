BEGIN;

CREATE INDEX idx_messages_chat_timestamp_id ON src_messages (chat_id, timestamp DESC, id DESC);

COMMIT;
