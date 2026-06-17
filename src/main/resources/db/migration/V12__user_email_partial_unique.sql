-- 계정 soft delete 후 동일 이메일로 재가입을 허용하기 위한 변경.
-- 기존 전역 UNIQUE(users.email) 제약은 soft delete된 행까지 포함해 충돌을 일으키므로,
-- 살아있는(deleted_at IS NULL) 행에만 적용되는 부분 유니크 인덱스로 대체한다.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;
