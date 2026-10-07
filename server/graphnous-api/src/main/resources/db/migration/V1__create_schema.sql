-- The relational part of Graphnous: systems, projects, scans and what
-- belongs to them. The scanned code itself is in Neo4j.
--
-- Rows refer to each other by id without foreign keys, as the entities do:
-- the deleters remove what belongs to a scan, project or system first.

CREATE TABLE systems (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255),
    description     VARCHAR(255),
    organization_id UUID,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_systems_organization ON systems (organization_id);

CREATE TABLE projects (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255),
    description VARCHAR(255),
    git_url     VARCHAR(255),
    path        VARCHAR(255),
    system_id   UUID,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_projects_system ON projects (system_id);

CREATE TABLE scans (
    id           UUID PRIMARY KEY,
    project_id   UUID,
    status       VARCHAR(255),
    git_revision VARCHAR(255),
    git_branch   VARCHAR(255),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    started_at   TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX idx_scans_project_created ON scans (project_id, created_at);
CREATE INDEX idx_scans_status ON scans (status);

CREATE TABLE scan_logs (
    id        UUID PRIMARY KEY,
    scan_id   UUID NOT NULL,
    sequence  BIGINT NOT NULL,
    timestamp TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    level     VARCHAR(255) NOT NULL,
    message   TEXT NOT NULL
);

CREATE INDEX idx_scan_logs_scan_sequence ON scan_logs (scan_id, sequence);

CREATE TABLE scan_steps (
    id          UUID PRIMARY KEY,
    scan_id     UUID NOT NULL,
    type        VARCHAR(255) NOT NULL,
    position    INTEGER NOT NULL,
    status      VARCHAR(255) NOT NULL,
    started_at  TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    error       TEXT
);

CREATE INDEX idx_scan_steps_scan_position ON scan_steps (scan_id, position);

CREATE TABLE scan_stats (
    id                        UUID PRIMARY KEY,
    scan_id                   UUID NOT NULL,
    project_id                UUID NOT NULL,
    number_of_modules_scanned INTEGER NOT NULL,
    -- The files of each language, as JSON: {"JAVA": ["backend/src/..."]}
    languages                 TEXT NOT NULL,
    number_of_classes_parsed  INTEGER NOT NULL,
    number_of_methods_parsed  INTEGER NOT NULL,
    CONSTRAINT uk_scan_stats_scan UNIQUE (scan_id)
);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    system_id  UUID NOT NULL,
    project_id UUID,
    scan_id    UUID,
    title      VARCHAR(255),
    content    TEXT NOT NULL,
    is_read    BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_notifications_system ON notifications (system_id);
CREATE INDEX ix_notifications_project ON notifications (project_id);
CREATE INDEX ix_notifications_scan ON notifications (scan_id);
