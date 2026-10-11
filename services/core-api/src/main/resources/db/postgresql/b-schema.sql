-- AgentFit Phase 1: Full Stack B's nine tables (B1-B9).
-- Apply only after A creates project, project_profile, and
-- profile_confirmation_event. This file is not auto-executed yet: A's
-- migration owner must assign its migration version during integration.
-- UUIDs and timestamps are assigned by Spring's authenticated service.

CREATE TABLE recommendation (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id),
    confirmed_profile_id UUID NOT NULL REFERENCES project_profile(id),
    confirmed_profile_version BIGINT NOT NULL CHECK (confirmed_profile_version >= 1),
    confirmation_event_id UUID NOT NULL REFERENCES profile_confirmation_event(id),
    project_version BIGINT NOT NULL CHECK (project_version >= 0),
    review_version BIGINT NOT NULL CHECK (review_version >= 0),
    developer_version BIGINT NOT NULL CHECK (developer_version >= 0),
    environment_version BIGINT NOT NULL CHECK (environment_version >= 0),
    catalog_release VARCHAR(100) NOT NULL CHECK (btrim(catalog_release) <> ''),
    catalog_hash CHAR(64) NOT NULL CHECK (catalog_hash ~ '^[0-9a-f]{64}$'),
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'RECOMMENDED', 'NO_ADDITIONS_NEEDED', 'NEEDS_INFORMATION', 'NO_COMPATIBLE_TOOLS'
    )),
    evidence_level VARCHAR(30) NOT NULL CHECK (evidence_level IN (
        'PLAN_ONLY', 'ENVIRONMENT_DECLARED'
    )),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX recommendation_project_created_idx
    ON recommendation (project_id, created_at DESC, id DESC);

CREATE TABLE recommendation_capability (
    recommendation_id UUID NOT NULL REFERENCES recommendation(id),
    capability_key VARCHAR(100) NOT NULL CHECK (capability_key IN (
        'cap_document_reference', 'cap_repository_navigation', 'cap_code_review',
        'cap_test_authoring', 'cap_test_execution', 'cap_browser_verification',
        'cap_api_verification', 'cap_database_schema_inspection', 'cap_ai_evaluation'
    )),
    necessity VARCHAR(20) NOT NULL CHECK (necessity IN (
        'REQUIRED', 'OPTIONAL', 'UNDETERMINED'
    )),
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    input_refs TEXT[] NOT NULL,
    PRIMARY KEY (recommendation_id, capability_key)
);

CREATE TABLE recommendation_item (
    id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES recommendation(id),
    tool_key VARCHAR(100) NOT NULL CHECK (btrim(tool_key) <> ''),
    tool_version VARCHAR(200) NOT NULL CHECK (btrim(tool_version) <> ''),
    support_key VARCHAR(100) NOT NULL CHECK (btrim(support_key) <> ''),
    required BOOLEAN NOT NULL,
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    explanation_source VARCHAR(20) NOT NULL CHECK (explanation_source IN ('AI', 'TEMPLATE')),
    UNIQUE (recommendation_id, tool_key),
    UNIQUE (id, recommendation_id)
);

CREATE TABLE recommendation_notice (
    id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES recommendation(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('QUESTION', 'REASON')),
    question_owner VARCHAR(20),
    answer_target VARCHAR(200),
    code VARCHAR(100) NOT NULL CHECK (btrim(code) <> ''),
    message TEXT NOT NULL CHECK (btrim(message) <> ''),
    input_refs TEXT[] NOT NULL DEFAULT '{}',
    CONSTRAINT recommendation_notice_question_shape CHECK (
        (type = 'QUESTION' AND question_owner IS NOT NULL
            AND question_owner IN ('AI', 'B')
            AND answer_target IS NOT NULL AND btrim(answer_target) <> '')
        OR (type = 'REASON' AND question_owner IS NULL AND answer_target IS NULL)
    )
);

CREATE INDEX recommendation_notice_parent_idx ON recommendation_notice (recommendation_id);

CREATE TABLE config_preview (
    id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES recommendation(id),
    generator_version VARCHAR(100) NOT NULL CHECK (btrim(generator_version) <> ''),
    existing_state VARCHAR(30) NOT NULL CHECK (existing_state IN (
        'UNKNOWN', 'USER_SAYS_EMPTY', 'PROVIDED'
    )),
    selection_digest CHAR(64) NOT NULL CHECK (selection_digest ~ '^[0-9a-f]{64}$'),
    content_digest CHAR(64) NOT NULL CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    approval_id UUID UNIQUE,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    approval_expires_at TIMESTAMPTZ,
    CONSTRAINT config_preview_expiry CHECK (created_at < expires_at),
    CONSTRAINT config_preview_approval_shape CHECK (
        (approval_id IS NULL AND approved_by IS NULL
            AND approved_at IS NULL AND approval_expires_at IS NULL)
        OR (approval_id IS NOT NULL AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL AND approval_expires_at IS NOT NULL
            AND created_at <= approved_at
            AND approved_at < approval_expires_at
            AND approval_expires_at <= expires_at)
    )
);

CREATE INDEX config_preview_recommendation_idx ON config_preview (recommendation_id, created_at DESC);

CREATE TABLE config_selection (
    id UUID PRIMARY KEY,
    preview_id UUID NOT NULL REFERENCES config_preview(id),
    recommendation_item_id UUID NOT NULL REFERENCES recommendation_item(id),
    UNIQUE (preview_id, recommendation_item_id)
);

CREATE INDEX config_selection_item_idx ON config_selection (recommendation_item_id);

CREATE TABLE config_permission_selection (
    selection_id UUID NOT NULL REFERENCES config_selection(id),
    mapping_key VARCHAR(100) NOT NULL CHECK (btrim(mapping_key) <> ''),
    policy VARCHAR(30) NOT NULL CHECK (policy IN (
        'ALWAYS_ALLOW', 'ASK_EACH_TIME', 'DENY'
    )),
    PRIMARY KEY (selection_id, mapping_key)
);

CREATE TABLE config_preview_file (
    id UUID PRIMARY KEY,
    preview_id UUID NOT NULL REFERENCES config_preview(id),
    target_key VARCHAR(200) NOT NULL CHECK (btrim(target_key) <> ''),
    relative_path VARCHAR(200) NOT NULL CHECK (btrim(relative_path) <> ''),
    action VARCHAR(20) NOT NULL CHECK (action IN ('PROPOSAL', 'UPDATE')),
    before_hash CHAR(64) CHECK (before_hash ~ '^[0-9a-f]{64}$'),
    after_hash CHAR(64) NOT NULL CHECK (after_hash ~ '^[0-9a-f]{64}$'),
    UNIQUE (preview_id, relative_path),
    CONSTRAINT config_preview_file_action_shape CHECK (
        (action = 'PROPOSAL' AND before_hash IS NULL)
        OR (action = 'UPDATE' AND before_hash IS NOT NULL)
    )
);

CREATE TABLE config_export (
    id UUID PRIMARY KEY,
    preview_id UUID NOT NULL REFERENCES config_preview(id),
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    generated_at TIMESTAMPTZ NOT NULL,
    latest_report_id UUID UNIQUE,
    reported_state VARCHAR(20),
    reason_code VARCHAR(50),
    reported_by UUID,
    reported_at TIMESTAMPTZ,
    CONSTRAINT config_export_report_shape CHECK (
        (latest_report_id IS NULL AND reported_state IS NULL
            AND reason_code IS NULL AND reported_by IS NULL AND reported_at IS NULL)
        OR (latest_report_id IS NOT NULL AND reported_state IS NOT NULL
            AND reported_by IS NOT NULL
            AND reported_at IS NOT NULL AND (
                (reported_state = 'APPLIED' AND reason_code IS NULL)
                OR (reported_state = 'FAILED' AND reason_code IS NOT NULL
                    AND reason_code IN (
                    'COPY_FAILED', 'AUTH_REQUIRED', 'DEPENDENCY_MISSING', 'UNKNOWN'
                ))
            ))
    )
);

CREATE INDEX config_export_preview_generated_idx
    ON config_export (preview_id, generated_at DESC, id DESC);
