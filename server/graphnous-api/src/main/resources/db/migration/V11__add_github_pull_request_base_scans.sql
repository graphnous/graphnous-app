-- The scan of the commit a pull request's branch started from, which its
-- report compares the head with; null when the report compares with the
-- newest completed scan of the base branch instead.

ALTER TABLE github_pull_request_scans ADD COLUMN base_scan_id UUID;

CREATE INDEX idx_github_pull_request_scans_base_scan ON github_pull_request_scans (base_scan_id);
