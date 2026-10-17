-- Why a scan started: MANUAL in the app or through the API, CHAT from the
-- assistant's chat, GITHUB_APP by the GitHub App, UPLOAD of results scanned
-- elsewhere. Null for the scans from before triggers were recorded.

ALTER TABLE scans ADD COLUMN scan_trigger VARCHAR(32);
