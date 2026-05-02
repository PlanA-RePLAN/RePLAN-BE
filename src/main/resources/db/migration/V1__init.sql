CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password      VARCHAR(255),
    nickname      VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    login_type    VARCHAR(20)  NOT NULL,
    profile_image TEXT,
    updated_at    TIMESTAMP,
    deleted_at    TIMESTAMP
);

CREATE TABLE goal
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    due_date   TIMESTAMP,
    reference  TEXT,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE tag
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    color      VARCHAR(20),
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE routine
(
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    due_date     TIMESTAMP,
    routine_type VARCHAR(20),
    routine_date INTEGER,
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    goal_id      BIGINT REFERENCES goal (id),
    updated_at   TIMESTAMP,
    deleted_at   TIMESTAMP
);

CREATE TABLE todo
(
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(255)     NOT NULL,
    due_date       TIMESTAMP,
    is_completed   BOOLEAN          NOT NULL DEFAULT false,
    completed_time TIMESTAMP,
    sort_order     DOUBLE PRECISION NOT NULL DEFAULT 10000.0,
    is_pinned      BOOLEAN,
    user_id        BIGINT           NOT NULL REFERENCES users (id),
    tag_id         BIGINT REFERENCES tag (id),
    goal_id        BIGINT REFERENCES goal (id),
    routine_id     BIGINT REFERENCES routine (id),
    updated_at     TIMESTAMP,
    deleted_at     TIMESTAMP
);

CREATE TABLE failure_reason
(
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT,
    todo_id    BIGINT NOT NULL REFERENCES todo (id),
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);
