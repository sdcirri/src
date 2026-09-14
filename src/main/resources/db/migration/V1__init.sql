CREATE TABLE src_users (
    id                    uuid PRIMARY KEY,
    username              varchar(255) NOT NULL UNIQUE,
    display_name          varchar(255),
    password_hash         varchar(255) NOT NULL,
    registration_time_utc timestamptz  NOT NULL,
    pro_pic               bytea
);

CREATE TABLE src_users_crypto (
    id              uuid PRIMARY KEY,
    kek_salt        bytea NOT NULL,
    iv_ed25519      bytea NOT NULL,
    private_ed25519 bytea NOT NULL,
    public_ed25519  bytea NOT NULL,
    iv_x25519       bytea NOT NULL,
    private_x25519  bytea NOT NULL,
    public_x25519   bytea NOT NULL,
    CONSTRAINT fk_users_crypto_user FOREIGN KEY (id) REFERENCES src_users (id)
);

CREATE TABLE src_sessions (
    id                    uuid PRIMARY KEY,
    access_token          bytea       NOT NULL UNIQUE,
    access_token_expires  timestamptz NOT NULL,
    refresh_token         bytea       NOT NULL UNIQUE,
    refresh_token_expires timestamptz NOT NULL,
    user_id               uuid        NOT NULL,
    CONSTRAINT fk_session_user_id FOREIGN KEY (user_id) REFERENCES src_users (id),
    CONSTRAINT src_sessions_access_ne_refresh CHECK (access_token != refresh_token)
);

CREATE TABLE src_chats (
    id       uuid PRIMARY KEY,
    user1_id uuid NOT NULL,
    user2_id uuid NOT NULL,
    CONSTRAINT uk_chat_users UNIQUE (user1_id, user2_id),
    CONSTRAINT ck_chat_user_order_and_not_self CHECK (user1_id < user2_id),
    CONSTRAINT fk_chat_user1_id FOREIGN KEY (user1_id) REFERENCES src_users (id),
    CONSTRAINT fk_chat_user2_id FOREIGN KEY (user2_id) REFERENCES src_users (id)
);

CREATE TABLE src_messages (
    id        uuid PRIMARY KEY,
    iv        bytea       NOT NULL,
    data      bytea       NOT NULL,
    timestamp timestamptz NOT NULL,
    chat_id   uuid        NOT NULL,
    sender_id uuid        NOT NULL,
    CONSTRAINT fk_message_chat_id FOREIGN KEY (chat_id) REFERENCES src_chats (id),
    CONSTRAINT fk_message_sender_id FOREIGN KEY (sender_id) REFERENCES src_users (id)
);
