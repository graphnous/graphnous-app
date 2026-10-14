-- The patterns of the files to leave out of scans, such as tests, each a
-- JSON array such as ["*.spec.ts", "generated/"]: the ones a project gives
-- its scans, and the ones each scan was created with, which may be other
-- ones for that scan. Null for none.

ALTER TABLE projects ADD COLUMN scan_excludes TEXT;

ALTER TABLE scans ADD COLUMN excludes TEXT;
