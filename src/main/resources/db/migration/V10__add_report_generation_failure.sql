CREATE TABLE report_generation_failure
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT  NOT NULL REFERENCES users (id),
    target_month  DATE    NOT NULL CHECK (EXTRACT(DAY FROM target_month) = 1),
    retry_count   INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at    TIMESTAMP(6),
    updated_at    TIMESTAMP(6),
    deleted_at    TIMESTAMP(6)
);

CREATE INDEX idx_report_gen_failure_retry
    ON report_generation_failure (retry_count)
    WHERE deleted_at IS NULL;
