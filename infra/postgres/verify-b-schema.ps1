$ErrorActionPreference = 'Stop'

$docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
if (-not $docker) {
    $docker = 'C:/Program Files/Docker/Docker/resources/bin/docker.exe'
}
if (-not (Test-Path -LiteralPath $docker)) {
    throw 'Docker CLI is not installed.'
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$schemaPath = Join-Path $repositoryRoot 'services/core-api/src/main/resources/db/postgresql/b-schema.sql'
$schema = Get-Content -LiteralPath $schemaPath -Raw

# The real A tables have not been merged. These minimum parent tables live
# only inside this transaction, which is rolled back after every check.
$before = @'
BEGIN;
CREATE SCHEMA b_schema_check;
SET LOCAL search_path TO b_schema_check;
CREATE TABLE project (id VARCHAR(36) PRIMARY KEY);
CREATE TABLE project_profile (id UUID PRIMARY KEY);
CREATE TABLE profile_confirmation_event (id UUID PRIMARY KEY);
'@

$after = @'
DO $$
DECLARE actual INTEGER;
BEGIN
    SELECT count(*) INTO actual FROM information_schema.tables
    WHERE table_schema = 'b_schema_check' AND table_name IN (
        'recommendation', 'recommendation_capability', 'recommendation_item',
        'recommendation_notice', 'config_preview', 'config_selection',
        'config_permission_selection', 'config_preview_file', 'config_export'
    );
    IF actual <> 9 THEN
        RAISE EXCEPTION 'Expected 9 B tables, found %', actual;
    END IF;
END $$;

INSERT INTO project VALUES ('11111111-1111-1111-1111-111111111111');
INSERT INTO project_profile VALUES ('22222222-2222-2222-2222-222222222222');
INSERT INTO profile_confirmation_event VALUES ('33333333-3333-3333-3333-333333333333');

INSERT INTO recommendation (
    id, project_id, confirmed_profile_id, confirmed_profile_version,
    confirmation_event_id, project_version, review_version, developer_version,
    environment_version, catalog_release, catalog_hash, status, evidence_level,
    created_at
) VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222', 1,
    '33333333-3333-3333-3333-333333333333', 1, 0, 0, 0,
    'test-release', repeat('a', 64), 'RECOMMENDED', 'PLAN_ONLY', now()
);
INSERT INTO recommendation_capability VALUES (
    '44444444-4444-4444-4444-444444444444',
    'cap_code_review', 'REQUIRED', 'test reason', ARRAY['developer.role']
);
INSERT INTO recommendation_item VALUES (
    '55555555-5555-5555-5555-555555555555',
    '44444444-4444-4444-4444-444444444444',
    'test-tool', '1.0', 'test-support', true, 'test reason', 'AI'
);
INSERT INTO recommendation_notice VALUES (
    '66666666-6666-6666-6666-666666666666',
    '44444444-4444-4444-4444-444444444444',
    'REASON', NULL, NULL, 'test_reason', 'test message', ARRAY[]::TEXT[]
);
INSERT INTO config_preview (
    id, recommendation_id, generator_version, existing_state,
    selection_digest, content_digest, fingerprint, created_at, expires_at
) VALUES (
    '77777777-7777-7777-7777-777777777777',
    '44444444-4444-4444-4444-444444444444',
    'test-generator', 'UNKNOWN', repeat('a', 64), repeat('b', 64),
    repeat('c', 64), now(), now() + interval '10 minutes'
);
INSERT INTO config_selection VALUES (
    '88888888-8888-8888-8888-888888888888',
    '77777777-7777-7777-7777-777777777777',
    '55555555-5555-5555-5555-555555555555'
);
INSERT INTO config_permission_selection VALUES (
    '88888888-8888-8888-8888-888888888888', 'test-mapping', 'ASK_EACH_TIME'
);
INSERT INTO config_preview_file VALUES (
    '99999999-9999-9999-9999-999999999999',
    '77777777-7777-7777-7777-777777777777',
    'test-target', 'config/test.json', 'PROPOSAL', NULL, repeat('d', 64)
);
INSERT INTO config_export (id, preview_id, fingerprint, generated_at) VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '77777777-7777-7777-7777-777777777777', repeat('c', 64), now()
);

DO $$
BEGIN
    BEGIN
        INSERT INTO recommendation (
            id, project_id, confirmed_profile_id, confirmed_profile_version,
            confirmation_event_id, project_version, review_version,
            developer_version, environment_version, catalog_release,
            catalog_hash, status, evidence_level, created_at
        ) VALUES (
            'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
            '11111111-1111-1111-1111-111111111111',
            '22222222-2222-2222-2222-222222222222', 1,
            '33333333-3333-3333-3333-333333333333', 1, 0, 0, 0,
            'test-release', repeat('a', 64), 'INVALID', 'PLAN_ONLY', now()
        );
        RAISE EXCEPTION 'Invalid recommendation status was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        UPDATE config_preview SET approval_id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'
        WHERE id = '77777777-7777-7777-7777-777777777777';
        RAISE EXCEPTION 'Partial approval was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        UPDATE config_export SET latest_report_id = 'dddddddd-dddd-dddd-dddd-dddddddddddd',
            reported_state = 'FAILED', reported_by = 42,
            reported_at = now() WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
        RAISE EXCEPTION 'Failed report without reason was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        UPDATE config_export SET latest_report_id = 'dddddddd-dddd-dddd-dddd-dddddddddddd',
            reported_state = NULL, reported_by = 42,
            reported_at = now() WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
        RAISE EXCEPTION 'Report without state was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO recommendation_notice VALUES (
            'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
            '44444444-4444-4444-4444-444444444444',
            'QUESTION', NULL, 'environment.os', 'test_question', 'test message', ARRAY[]::TEXT[]
        );
        RAISE EXCEPTION 'Question without owner was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO config_permission_selection VALUES (
            'ffffffff-ffff-ffff-ffff-ffffffffffff', 'test-mapping', 'ASK_EACH_TIME'
        );
        RAISE EXCEPTION 'Missing selection reference was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
END $$;

ROLLBACK;
SELECT 'B schema smoke test passed; no test tables were retained' AS result;
'@

$sql = $before + "`n" + $schema + "`n" + $after
$sql | & $docker exec -i agentfit-postgres-local psql -X -v ON_ERROR_STOP=1 -U agentfit -d agentfit
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL B schema smoke test failed (psql exit $LASTEXITCODE)."
}
