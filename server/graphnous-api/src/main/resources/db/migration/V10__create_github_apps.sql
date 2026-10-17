-- The GitHub App setup registered for this server, one at most. The private
-- key and webhook secret are encrypted with the server's secrets key
-- (graphnous.secrets.key), each as base64 of its nonce and ciphertext.

CREATE TABLE github_apps (
    id             SMALLINT PRIMARY KEY CHECK (id = 1),
    app_id         VARCHAR(64) NOT NULL,
    slug           VARCHAR(255) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    html_url       VARCHAR(1024) NOT NULL,
    private_key    TEXT NOT NULL,
    webhook_secret TEXT NOT NULL,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
