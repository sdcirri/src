CREATE INDEX idx_messages_chat_timestamp
    ON src_messages (chat_id, timestamp DESC, id DESC);

CREATE INDEX idx_chats_user1 ON src_chats (user1_id);

CREATE INDEX idx_chats_user2 ON src_chats (user2_id);
