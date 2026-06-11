CREATE TABLE monthly_report
(
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT        NOT NULL REFERENCES users (id),
    report_month              DATE          NOT NULL,
    total_todos               INT,
    completed_todos           INT,
    achievement_rate          NUMERIC(5, 2),
    prev_month_diff           NUMERIC(5, 2),
    replan_count              INT,
    replan_achievement_effect NUMERIC(5, 2),
    analysis_data             JSONB,
    ai_insight                JSONB,
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP,
    deleted_at                TIMESTAMP,
    UNIQUE (user_id, report_month)
);
