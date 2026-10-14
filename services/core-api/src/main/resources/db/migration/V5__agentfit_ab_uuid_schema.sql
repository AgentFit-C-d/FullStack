-- AgentFit Phase 2 canonical UUID schema.
-- Development only: V1-V4 development data is intentionally discarded.

DROP TABLE IF EXISTS audit CASCADE;
DROP TABLE IF EXISTS analysis_attempt CASCADE;
DROP TABLE IF EXISTS analysis_admission CASCADE;
DROP TABLE IF EXISTS project_creation_idempotency CASCADE;
DROP TABLE IF EXISTS planning_input CASCADE;
DROP TABLE IF EXISTS project_runtime CASCADE;
DROP TABLE IF EXISTS project CASCADE;
DROP TABLE IF EXISTS "USER" CASCADE;

CREATE TABLE "USER" (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    github_user_id BIGINT UNIQUE,
    github_login VARCHAR(255) UNIQUE
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES "USER"(id),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    current_step VARCHAR(30) NOT NULL CHECK (current_step IN ('PLANNING', 'ROLE_ENVIRONMENT', 'RECOMMENDATION', 'CONFIGURATION', 'COMPLETED')),
    analysis_generation BIGINT NOT NULL DEFAULT 0 CHECK (analysis_generation >= 0),
    review_generation BIGINT NOT NULL DEFAULT 0 CHECK (review_generation >= 0),
    review_version BIGINT NOT NULL DEFAULT 0 CHECK (review_version >= 0),
    current_review_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX project_user_updated_idx ON project (user_id, updated_at DESC, id ASC);

CREATE TABLE project_profile (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('DRAFT', 'CONFIRMED')),
    version BIGINT NOT NULL CHECK (version >= 1),
    project_name VARCHAR(200), project_type VARCHAR(200), domain VARCHAR(200),
    field_metadata JSONB NOT NULL,
    confirmed_by UUID REFERENCES "USER"(id), confirmed_at TIMESTAMPTZ,
    source_draft_id UUID, source_draft_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (project_id, kind),
    CHECK ((kind = 'DRAFT' AND confirmed_by IS NULL AND confirmed_at IS NULL)
        OR (kind = 'CONFIRMED' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))
);

CREATE TABLE project_profile_item (
    id UUID PRIMARY KEY,
    project_profile_id UUID NOT NULL REFERENCES project_profile(id),
    field_key VARCHAR(40) NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    category VARCHAR(30), area VARCHAR(40), name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    version VARCHAR(50), source VARCHAR(20) NOT NULL CHECK (source IN ('DOCUMENT', 'USER')),
    evidence JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (project_profile_id, field_key, ordinal)
);

CREATE TABLE project_profile_gap (
    id UUID PRIMARY KEY,
    project_profile_id UUID NOT NULL REFERENCES project_profile(id),
    target_draft_version BIGINT NOT NULL CHECK (target_draft_version >= 1),
    type VARCHAR(30) NOT NULL CHECK (type IN ('MISSING', 'AMBIGUOUS', 'CONFLICT')),
    target_field_key VARCHAR(40), target_category VARCHAR(30), target_area VARCHAR(40),
    review_item_id UUID, message TEXT NOT NULL, reason TEXT, evidence JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_summary (
    id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id),
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('PDF', 'MARKDOWN', 'TEXT')),
    display_name VARCHAR(200), byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
    character_count INTEGER CHECK (character_count IS NULL OR character_count >= 0),
    page_count INTEGER CHECK (page_count IS NULL OR page_count >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE analysis_attempt (
    id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id),
    document_id UUID REFERENCES document_summary(id), request_id VARCHAR(100) NOT NULL,
    contract_version VARCHAR(50) NOT NULL, generation BIGINT NOT NULL CHECK (generation >= 1),
    draft_id_at_start UUID, draft_version_at_start BIGINT,
    result_draft_id UUID, result_draft_version BIGINT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    error_code VARCHAR(50), started_at TIMESTAMPTZ NOT NULL, deadline_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ, UNIQUE (project_id, generation), UNIQUE (request_id)
);

CREATE TABLE developer_profile (
    id UUID PRIMARY KEY, project_id UUID NOT NULL UNIQUE REFERENCES project(id),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1), role VARCHAR(30),
    responsibilities JSONB, experience_level VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE environment_profile (
    id UUID PRIMARY KEY, project_id UUID NOT NULL UNIQUE REFERENCES project(id),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1), source VARCHAR(30) NOT NULL,
    os_family VARCHAR(30), os_version VARCHAR(200), client_id VARCHAR(200), client_version VARCHAR(200),
    configuration_knowledge VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    runtime_knowledge VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    auth_readiness_knowledge VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    installed_components_knowledge VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE environment_runtime (
    id UUID PRIMARY KEY, environment_profile_id UUID NOT NULL REFERENCES environment_profile(id),
    name VARCHAR(200) NOT NULL, version VARCHAR(200), created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (environment_profile_id, name)
);

CREATE TABLE environment_auth_readiness (
    id UUID PRIMARY KEY, environment_profile_id UUID NOT NULL REFERENCES environment_profile(id),
    service_id VARCHAR(200) NOT NULL, state VARCHAR(30) NOT NULL CHECK (state IN ('READY', 'NOT_READY', 'UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL, UNIQUE (environment_profile_id, service_id)
);

CREATE TABLE profile_confirmation_event (
    id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id),
    confirmed_profile_id UUID NOT NULL REFERENCES project_profile(id),
    confirmed_profile_version BIGINT NOT NULL CHECK (confirmed_profile_version >= 1),
    project_version BIGINT NOT NULL CHECK (project_version >= 0),
    source_draft_id UUID, source_draft_version BIGINT,
    actor_id UUID NOT NULL REFERENCES "USER"(id), confirmed_at TIMESTAMPTZ NOT NULL,
    request_id VARCHAR(100) NOT NULL, profile_snapshot JSONB NOT NULL,
    candidate_actions JSONB NOT NULL DEFAULT '[]', UNIQUE (project_id, project_version)
);

CREATE TABLE profile_review (
    id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id),
    target_profile_id UUID NOT NULL REFERENCES project_profile(id), target_profile_version BIGINT NOT NULL,
    review_version BIGINT NOT NULL CHECK (review_version >= 0), request_id VARCHAR(100) NOT NULL,
    contract_version VARCHAR(50) NOT NULL, item_count INTEGER NOT NULL CHECK (item_count >= 0),
    created_at TIMESTAMPTZ NOT NULL, UNIQUE (project_id, review_version)
);

CREATE TABLE profile_review_item (
    id UUID PRIMARY KEY, review_id UUID NOT NULL REFERENCES profile_review(id),
    candidate_id VARCHAR(100) NOT NULL, target_field_key VARCHAR(40), proposed_value JSONB,
    model_judgement VARCHAR(50) NOT NULL, initial_server_disposition VARCHAR(30) NOT NULL,
    server_disposition VARCHAR(30) NOT NULL, disposition_history JSONB NOT NULL DEFAULT '[]',
    supporting_evidence JSONB NOT NULL DEFAULT '[]', conflicting_evidence JSONB NOT NULL DEFAULT '[]',
    question TEXT, reason TEXT, UNIQUE (review_id, candidate_id)
);

CREATE TABLE analysis_failure_diagnostic (
    id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id),
    attempt_id UUID NOT NULL UNIQUE REFERENCES analysis_attempt(id), received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL, ciphertext BYTEA NOT NULL, key_id VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size >= 0), truncated BOOLEAN NOT NULL, redacted BOOLEAN NOT NULL,
    redaction_policy_version VARCHAR(100), truncation_policy_version VARCHAR(100), created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE environment_installed_component (
    id UUID PRIMARY KEY, environment_profile_id UUID NOT NULL REFERENCES environment_profile(id),
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0), name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL, version VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL, UNIQUE (environment_profile_id, ordinal),
    UNIQUE (environment_profile_id, normalized_name)
);

ALTER TABLE project ADD CONSTRAINT project_current_review_fk
    FOREIGN KEY (current_review_id) REFERENCES profile_review(id);

CREATE TABLE recommendation (
    id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id),
    confirmed_profile_id UUID NOT NULL REFERENCES project_profile(id), confirmed_profile_version BIGINT NOT NULL,
    confirmation_event_id UUID NOT NULL REFERENCES profile_confirmation_event(id), project_version BIGINT NOT NULL,
    review_version BIGINT NOT NULL, developer_version BIGINT NOT NULL, environment_version BIGINT NOT NULL,
    catalog_release VARCHAR(100) NOT NULL, catalog_hash CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('RECOMMENDED', 'NO_ADDITIONS_NEEDED', 'NEEDS_INFORMATION', 'NO_COMPATIBLE_TOOLS')),
    evidence_level VARCHAR(30) NOT NULL CHECK (evidence_level IN ('PLAN_ONLY', 'ENVIRONMENT_DECLARED')),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX recommendation_project_created_idx ON recommendation (project_id, created_at DESC, id DESC);

CREATE TABLE recommendation_capability (
    recommendation_id UUID NOT NULL REFERENCES recommendation(id), capability_key VARCHAR(100) NOT NULL,
    necessity VARCHAR(20) NOT NULL CHECK (necessity IN ('REQUIRED', 'OPTIONAL', 'UNDETERMINED')),
    reason TEXT NOT NULL, input_refs TEXT[] NOT NULL, PRIMARY KEY (recommendation_id, capability_key)
);
CREATE TABLE recommendation_item (
    id UUID PRIMARY KEY, recommendation_id UUID NOT NULL REFERENCES recommendation(id), tool_key VARCHAR(100) NOT NULL,
    tool_version VARCHAR(200) NOT NULL, support_key VARCHAR(100) NOT NULL, required BOOLEAN NOT NULL,
    reason TEXT NOT NULL, explanation_source VARCHAR(20) NOT NULL CHECK (explanation_source IN ('AI', 'TEMPLATE')),
    UNIQUE (recommendation_id, tool_key)
);
CREATE TABLE recommendation_notice (
    id UUID PRIMARY KEY, recommendation_id UUID NOT NULL REFERENCES recommendation(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('QUESTION', 'REASON')), question_owner VARCHAR(20),
    answer_target VARCHAR(200), code VARCHAR(100) NOT NULL, message TEXT NOT NULL,
    input_refs TEXT[] NOT NULL DEFAULT '{}'
);
CREATE TABLE config_preview (
    id UUID PRIMARY KEY, recommendation_id UUID NOT NULL REFERENCES recommendation(id),
    generator_version VARCHAR(100) NOT NULL, existing_state VARCHAR(30) NOT NULL,
    selection_digest CHAR(64) NOT NULL, content_digest CHAR(64) NOT NULL, fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL, approval_id UUID UNIQUE,
    approved_by UUID REFERENCES "USER"(id), approved_at TIMESTAMPTZ, approval_expires_at TIMESTAMPTZ,
    CHECK (created_at < expires_at)
);
CREATE TABLE config_selection (
    id UUID PRIMARY KEY, preview_id UUID NOT NULL REFERENCES config_preview(id),
    recommendation_item_id UUID NOT NULL REFERENCES recommendation_item(id), UNIQUE (preview_id, recommendation_item_id)
);
CREATE TABLE config_permission_selection (
    selection_id UUID NOT NULL REFERENCES config_selection(id), mapping_key VARCHAR(100) NOT NULL,
    policy VARCHAR(30) NOT NULL CHECK (policy IN ('ALWAYS_ALLOW', 'ASK_EACH_TIME', 'DENY')),
    PRIMARY KEY (selection_id, mapping_key)
);
CREATE TABLE config_preview_file (
    id UUID PRIMARY KEY, preview_id UUID NOT NULL REFERENCES config_preview(id),
    target_key VARCHAR(200) NOT NULL, relative_path VARCHAR(200) NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('PROPOSAL', 'UPDATE')),
    before_hash CHAR(64), after_hash CHAR(64) NOT NULL, UNIQUE (preview_id, relative_path)
);
CREATE TABLE config_export (
    id UUID PRIMARY KEY, preview_id UUID NOT NULL REFERENCES config_preview(id), fingerprint CHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL, latest_report_id UUID UNIQUE, reported_state VARCHAR(20),
    reason_code VARCHAR(50), reported_by UUID REFERENCES "USER"(id), reported_at TIMESTAMPTZ
);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY, project_id UUID REFERENCES project(id), actor_id UUID REFERENCES "USER"(id),
    request_id VARCHAR(100), event VARCHAR(100) NOT NULL, outcome VARCHAR(30) NOT NULL,
    reason_code VARCHAR(100), target_type VARCHAR(50), target_id UUID, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX audit_event_project_time_idx ON audit_event (project_id, occurred_at DESC);
