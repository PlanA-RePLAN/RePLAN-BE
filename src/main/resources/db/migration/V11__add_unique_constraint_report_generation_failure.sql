CREATE UNIQUE INDEX idx_report_gen_failure_unique_active
    ON report_generation_failure (user_id, target_month)
    WHERE deleted_at IS NULL;
