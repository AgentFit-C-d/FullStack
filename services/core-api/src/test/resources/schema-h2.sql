CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

-- Phase 2 UUID fixture. JSON/array payloads are represented as CLOB/VARCHAR in H2;
-- PostgreSQL types and full checks remain authoritative in V5 migration.
CREATE TABLE IF NOT EXISTS "USER" (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    github_user_id BIGINT,
    github_login VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS project (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES "USER"(id),
    name VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL,
    current_step VARCHAR(30) NOT NULL,
    analysis_generation BIGINT NOT NULL,
    review_generation BIGINT NOT NULL,
    current_review_id UUID,
    developer_role VARCHAR(30) NOT NULL DEFAULT 'BACKEND',
    current_input_revision BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS project_profile (id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), kind VARCHAR(20) NOT NULL, version BIGINT NOT NULL, field_metadata CLOB NOT NULL);
CREATE TABLE IF NOT EXISTS project_profile_item (id UUID PRIMARY KEY, project_profile_id UUID NOT NULL REFERENCES project_profile(id), field_key VARCHAR(40) NOT NULL, ordinal INT NOT NULL, evidence CLOB NOT NULL);
CREATE TABLE IF NOT EXISTS project_profile_gap (id UUID PRIMARY KEY, project_profile_id UUID NOT NULL REFERENCES project_profile(id), target_draft_version BIGINT NOT NULL, type VARCHAR(30) NOT NULL, message CLOB NOT NULL);
CREATE TABLE IF NOT EXISTS document_summary (id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), kind VARCHAR(30) NOT NULL, byte_size BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS analysis_attempt (id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), request_id VARCHAR(100) NOT NULL, contract_version VARCHAR(50) NOT NULL, generation BIGINT NOT NULL, status VARCHAR(30) NOT NULL);
CREATE TABLE IF NOT EXISTS developer_profile (id UUID PRIMARY KEY, project_id UUID NOT NULL UNIQUE REFERENCES project(id), version BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS environment_profile (id UUID PRIMARY KEY, project_id UUID NOT NULL UNIQUE REFERENCES project(id), version BIGINT NOT NULL, source VARCHAR(30) NOT NULL);
CREATE TABLE IF NOT EXISTS environment_runtime (id UUID PRIMARY KEY, environment_profile_id UUID NOT NULL REFERENCES environment_profile(id), name VARCHAR(200) NOT NULL);
CREATE TABLE IF NOT EXISTS environment_auth_readiness (id UUID PRIMARY KEY, environment_profile_id UUID NOT NULL REFERENCES environment_profile(id), service_id VARCHAR(200) NOT NULL, state VARCHAR(30) NOT NULL);
CREATE TABLE IF NOT EXISTS profile_confirmation_event (id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), confirmed_profile_id UUID NOT NULL REFERENCES project_profile(id), actor_id UUID NOT NULL REFERENCES "USER"(id));
CREATE TABLE IF NOT EXISTS profile_review (id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), target_profile_id UUID NOT NULL REFERENCES project_profile(id), review_version BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS profile_review_item (id UUID PRIMARY KEY, review_id UUID NOT NULL REFERENCES profile_review(id), candidate_id VARCHAR(100) NOT NULL, model_judgement VARCHAR(50) NOT NULL);
CREATE TABLE IF NOT EXISTS analysis_failure_diagnostic (id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), attempt_id UUID NOT NULL REFERENCES analysis_attempt(id), ciphertext BINARY NOT NULL);
CREATE TABLE IF NOT EXISTS environment_installed_component (id UUID PRIMARY KEY, environment_profile_id UUID NOT NULL REFERENCES environment_profile(id), ordinal INT NOT NULL, name VARCHAR(200) NOT NULL);
ALTER TABLE project ADD CONSTRAINT IF NOT EXISTS project_current_review_fk FOREIGN KEY (current_review_id) REFERENCES profile_review(id);
CREATE TABLE IF NOT EXISTS recommendation (id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), confirmed_profile_id UUID NOT NULL REFERENCES project_profile(id), confirmation_event_id UUID NOT NULL REFERENCES profile_confirmation_event(id), status VARCHAR(30) NOT NULL);
CREATE TABLE IF NOT EXISTS recommendation_capability (recommendation_id UUID NOT NULL REFERENCES recommendation(id), capability_key VARCHAR(100) NOT NULL, necessity VARCHAR(20) NOT NULL, PRIMARY KEY (recommendation_id, capability_key));
CREATE TABLE IF NOT EXISTS recommendation_item (id UUID PRIMARY KEY, recommendation_id UUID NOT NULL REFERENCES recommendation(id), tool_key VARCHAR(100) NOT NULL);
CREATE TABLE IF NOT EXISTS recommendation_notice (id UUID PRIMARY KEY, recommendation_id UUID NOT NULL REFERENCES recommendation(id), type VARCHAR(20) NOT NULL, code VARCHAR(100) NOT NULL);
CREATE TABLE IF NOT EXISTS config_preview (id UUID PRIMARY KEY, recommendation_id UUID NOT NULL REFERENCES recommendation(id), fingerprint CHAR(64) NOT NULL);
CREATE TABLE IF NOT EXISTS config_selection (id UUID PRIMARY KEY, preview_id UUID NOT NULL REFERENCES config_preview(id), recommendation_item_id UUID NOT NULL REFERENCES recommendation_item(id));
CREATE TABLE IF NOT EXISTS config_permission_selection (selection_id UUID NOT NULL REFERENCES config_selection(id), mapping_key VARCHAR(100) NOT NULL, policy VARCHAR(30) NOT NULL, PRIMARY KEY (selection_id, mapping_key));
CREATE TABLE IF NOT EXISTS config_preview_file (id UUID PRIMARY KEY, preview_id UUID NOT NULL REFERENCES config_preview(id), relative_path VARCHAR(200) NOT NULL);
CREATE TABLE IF NOT EXISTS config_export (id UUID PRIMARY KEY, preview_id UUID NOT NULL REFERENCES config_preview(id), fingerprint CHAR(64) NOT NULL);
CREATE TABLE IF NOT EXISTS audit_event (id UUID PRIMARY KEY, project_id UUID REFERENCES project(id), actor_id UUID REFERENCES "USER"(id), event VARCHAR(100) NOT NULL, outcome VARCHAR(30) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL);

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES VARBINARY(10000) NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
