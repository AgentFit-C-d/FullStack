CREATE TABLE analysis_input_lease (
    attempt_id UUID NOT NULL REFERENCES analysis_attempt(id) ON DELETE CASCADE,
    upload_id VARCHAR(128) NOT NULL,
    lease_id VARCHAR(128) NOT NULL,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    input_revision BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT analysis_input_lease_pk PRIMARY KEY (attempt_id),
    CONSTRAINT analysis_input_lease_upload_uq UNIQUE (upload_id),
    CONSTRAINT analysis_input_lease_id_uq UNIQUE (lease_id)
);

CREATE INDEX analysis_input_lease_project_ix
    ON analysis_input_lease(project_id, input_revision);

CREATE TABLE analysis_result (
    attempt_id UUID NOT NULL REFERENCES analysis_attempt(id) ON DELETE CASCADE,
    request_id VARCHAR(128) NOT NULL,
    generation BIGINT NOT NULL,
    contract_version VARCHAR(64) NOT NULL,
    result_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT analysis_result_pk PRIMARY KEY (attempt_id),
    CONSTRAINT analysis_result_request_uq UNIQUE (request_id)
);
