CREATE TABLE device_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    token       TEXT   NOT NULL,
    platform    VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP
);

-- 같은 FCM 토큰은 한 번만 저장한다.
CREATE UNIQUE INDEX ux_device_token_token ON device_token (token);
CREATE INDEX ix_device_token_user ON device_token (user_id);
