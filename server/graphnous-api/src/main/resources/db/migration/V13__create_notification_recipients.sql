-- Who read a notification: one row for each user who marked it read or
-- unread, without a user (user_id null) where there is none, as without
-- security or for an API key. A notification without a row for someone is
-- unread for them. Before, a notification was read for everyone at once;
-- those read become read without a user.

CREATE TABLE notification_recipients (
    id              UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id         UUID,
    is_read         BOOLEAN NOT NULL,
    read_at         TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uq_notification_recipients UNIQUE NULLS NOT DISTINCT (notification_id, user_id)
);

CREATE INDEX ix_notification_recipients_user ON notification_recipients (user_id);

INSERT INTO notification_recipients (id, notification_id, user_id, is_read, read_at)
SELECT gen_random_uuid(), id, NULL, TRUE, NULL FROM notifications WHERE is_read;

ALTER TABLE notifications DROP COLUMN is_read;
