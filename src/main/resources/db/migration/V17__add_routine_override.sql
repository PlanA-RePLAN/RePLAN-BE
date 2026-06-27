ALTER TABLE routine
  ADD COLUMN default_sort_order DOUBLE PRECISION NOT NULL DEFAULT 10000.0;

CREATE TABLE routine_override (
  id             BIGSERIAL    PRIMARY KEY,
  routine_id     BIGINT       NOT NULL REFERENCES routine(id),
  override_date  DATE         NOT NULL,
  title          VARCHAR(255),
  tag_id         BIGINT       REFERENCES tag(id),
  sort_order     DOUBLE PRECISION,
  is_skipped     BOOLEAN      NOT NULL DEFAULT FALSE,
  is_pinned      BOOLEAN,
  is_completed   BOOLEAN,
  completed_time TIMESTAMP,
  created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
  UNIQUE (routine_id, override_date)
);
