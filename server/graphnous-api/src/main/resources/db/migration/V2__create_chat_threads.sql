-- Conversations with the assistant, each of a user about a system. The
-- messages are the conversation's AG-UI messages, as a JSON array.

CREATE TABLE chat_threads (
    id         UUID PRIMARY KEY,
    system_id  UUID NOT NULL,
    -- Null for the single user of a server without security
    user_id    UUID,
    title      VARCHAR(255),
    messages   TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_chat_threads_system_user ON chat_threads (system_id, user_id, updated_at);
