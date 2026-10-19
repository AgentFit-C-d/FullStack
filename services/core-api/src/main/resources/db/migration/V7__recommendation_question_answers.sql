CREATE TABLE recommendation_question_answer (
    notice_id UUID PRIMARY KEY REFERENCES recommendation_notice(id) ON DELETE CASCADE,
    recommendation_id UUID NOT NULL REFERENCES recommendation(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    answer TEXT NOT NULL,
    actor_id UUID NOT NULL REFERENCES "USER"(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (length(trim(answer)) BETWEEN 1 AND 4000)
);
CREATE INDEX recommendation_question_answer_recommendation_idx
    ON recommendation_question_answer (recommendation_id);
