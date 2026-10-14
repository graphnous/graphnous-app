-- The languages to scan, each a JSON array such as ["JAVA"]: the ones a
-- project scans, and the ones each scan was created with, which may be
-- other ones for that scan. Null for every language a scan finds.

ALTER TABLE projects ADD COLUMN scan_languages TEXT;

ALTER TABLE scans ADD COLUMN languages TEXT;
