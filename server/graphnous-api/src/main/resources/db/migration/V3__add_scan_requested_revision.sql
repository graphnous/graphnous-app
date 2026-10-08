-- The revision a scan was asked for, such as a tag or a short commit hash;
-- git_revision becomes the commit it checked out. Until now both were the
-- one asked for.

ALTER TABLE scans ADD COLUMN git_requested_revision VARCHAR(255);

UPDATE scans SET git_requested_revision = git_revision;
