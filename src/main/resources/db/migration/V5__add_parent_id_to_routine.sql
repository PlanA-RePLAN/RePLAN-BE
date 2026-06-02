ALTER TABLE routine
    ADD COLUMN parent_id BIGINT REFERENCES routine (id);

CREATE INDEX idx_routine_parent_id ON routine (parent_id);

ALTER TABLE routine
    ADD CONSTRAINT ck_routine_child_has_no_schedule
        CHECK (
            parent_id IS NULL
                OR (
                       routine_type IS NULL
                           AND routine_date IS NULL
                           AND due_date IS NULL
                           AND tag_id IS NULL
                           AND goal_id IS NULL
                   )
            );
