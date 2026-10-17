-- The scans the GitHub App started for pull requests and has not reported
-- on yet: once one is done, its impact report goes to the pull request's
-- check run and comment, and the row is deleted.

CREATE TABLE github_pull_request_scans (
    scan_id         UUID PRIMARY KEY,
    project_id      UUID NOT NULL,
    installation_id BIGINT NOT NULL,
    -- Its full name, such as acme/shop
    repository      VARCHAR(255) NOT NULL,
    pull_request    INTEGER NOT NULL,
    head_sha        VARCHAR(64) NOT NULL,
    base_branch     VARCHAR(255) NOT NULL,
    check_run_id    BIGINT NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_github_pull_request_scans_pull_request
    ON github_pull_request_scans (repository, pull_request, project_id, created_at);
