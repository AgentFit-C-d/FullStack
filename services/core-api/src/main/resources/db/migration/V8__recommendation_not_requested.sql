ALTER TABLE recommendation_capability
    DROP CONSTRAINT recommendation_capability_necessity_check;

ALTER TABLE recommendation_capability
    ADD CONSTRAINT recommendation_capability_necessity_check
    CHECK (necessity IN ('REQUIRED', 'OPTIONAL', 'UNDETERMINED', 'NOT_REQUESTED'));
