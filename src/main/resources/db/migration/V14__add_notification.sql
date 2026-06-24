CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    category    VARCHAR(16) NOT NULL,
    type        VARCHAR(32) NOT NULL,
    title       VARCHAR(512) NOT NULL,
    body        VARCHAR(255) NOT NULL,
    target_type VARCHAR(16),
    target_id   BIGINT,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP
);

-- 목록(최신순, 카테고리 필터)과 안읽음 카운트에 쓰는 인덱스
CREATE INDEX ix_notification_user_id_desc ON notification (user_id, id DESC);
CREATE INDEX ix_notification_user_category ON notification (user_id, category);
