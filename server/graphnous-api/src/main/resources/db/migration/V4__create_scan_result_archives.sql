-- The results of scans as their scanners produced them, each the gzipped
-- JSON of one target's result, kept to enhance a scan again: the graph
-- holds only part of them.
CREATE TABLE scan_result_archives (
    id       UUID PRIMARY KEY,
    scan_id  UUID NOT NULL,
    position INTEGER NOT NULL,
    content  BYTEA NOT NULL,
    CONSTRAINT uk_scan_result_archives_scan_position UNIQUE (scan_id, position)
);
