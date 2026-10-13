-- The project a conversation is about, for the conversations started from
-- a project; null for those about the system as a whole, as all were until
-- now.

ALTER TABLE chat_threads ADD COLUMN project_id UUID;

CREATE INDEX idx_chat_threads_project_user ON chat_threads (project_id, user_id, updated_at);
