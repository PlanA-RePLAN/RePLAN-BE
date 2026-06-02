DROP TABLE failure_reason;

CREATE TABLE replan
(
    id                BIGSERIAL PRIMARY KEY,
    todo_id           BIGINT       NOT NULL REFERENCES todo (id),
    failure_reason_1  VARCHAR(128) NOT NULL,
    failure_reason_2  VARCHAR(128),
    failure_reason_3  VARCHAR(128),
    CONSTRAINT chk_reason CHECK (failure_reason_1 IS NOT NULL),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP
);

ALTER TABLE todo
    ADD COLUMN replan_id BIGINT REFERENCES replan (id);
ALTER TABLE todo
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE routine
    ADD COLUMN replan_id BIGINT REFERENCES replan (id);
ALTER TABLE routine
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
