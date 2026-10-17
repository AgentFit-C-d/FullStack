-- V5 establishes UUID as the canonical identity model.  This migration restores
-- only the Core A execution data V5 deliberately replaced, using UUID foreign keys.

ALTER TABLE project
    ADD COLUMN developer_role VARCHAR(30) NOT NULL DEFAULT 'BACKEND',
    ADD COLUMN current_input_revision BIGINT;

CREATE TABLE project_runtime (
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 1),
    value VARCHAR(200) NOT NULL,
    normalized_value VARCHAR(200) NOT NULL,
    PRIMARY KEY (project_id, ordinal),
    UNIQUE (project_id, normalized_value)
);

CREATE TABLE planning_input (
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    input_type VARCHAR(16) NOT NULL,
    file_name VARCHAR(255),
    media_type VARCHAR(100),
    byte_size BIGINT,
    character_count INTEGER,
    page_count INTEGER,
    raw_content BYTEA,
    extracted_text TEXT,
    citations TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (project_id, revision)
);

CREATE TABLE project_creation_idempotency (
    owner_id UUID NOT NULL REFERENCES "USER"(id),
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    project_id UUID REFERENCES project(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (owner_id, operation, idempotency_key)
);

CREATE TABLE analysis_admission (
    id SMALLINT PRIMARY KEY,
    active_count SMALLINT NOT NULL CHECK (active_count BETWEEN 0 AND 2),
    updated_at TIMESTAMPTZ NOT NULL
);
INSERT INTO analysis_admission(id, active_count, updated_at)
VALUES (1, 0, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE analysis_attempt
    ADD COLUMN owner_id UUID REFERENCES "USER"(id),
    ADD COLUMN input_revision BIGINT,
    ADD COLUMN expected_project_version BIGINT,
    ADD COLUMN expected_basis VARCHAR(100),
    ADD COLUMN execution_token UUID,
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN lease_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX analysis_attempt_project_processing_uq
    ON analysis_attempt(project_id) WHERE status = 'PROCESSING';
CREATE UNIQUE INDEX analysis_attempt_owner_processing_uq
    ON analysis_attempt(owner_id) WHERE status = 'PROCESSING';
CREATE INDEX analysis_attempt_claim_idx
    ON analysis_attempt(status, lease_expires_at, started_at);
