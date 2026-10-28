ALTER TABLE recommendation_item
    ADD COLUMN risk_status VARCHAR(20) NOT NULL DEFAULT 'UNASSESSED',
    ADD COLUMN risk_level VARCHAR(20),
    ADD COLUMN external_auth VARCHAR(20),
    ADD COLUMN risk_reason VARCHAR(500),
    ADD COLUMN risk_evidence_url TEXT,
    ADD COLUMN risk_checked_at DATE;

ALTER TABLE recommendation_item
    ADD CONSTRAINT recommendation_item_risk_snapshot_check CHECK (
        (risk_status = 'UNASSESSED' AND risk_level IS NULL AND external_auth IS NULL
            AND risk_reason IS NULL AND risk_evidence_url IS NULL AND risk_checked_at IS NULL)
        OR
        (risk_status = 'REVIEWED' AND risk_level IN ('LOW', 'MEDIUM', 'HIGH')
            AND external_auth IN ('REQUIRED', 'NOT_REQUIRED')
            AND risk_reason IS NOT NULL AND risk_evidence_url IS NOT NULL
            AND risk_checked_at IS NOT NULL)
    );
