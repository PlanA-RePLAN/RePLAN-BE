-- 애플 로그인은 네이티브 재로그인 시 이메일을 주지 않고 고유 식별번호(sub)만 준다.
-- 그래서 이메일 대신 sub로 사용자를 식별하기 위해 users에 apple_sub 칸을 추가한다.
-- 애플 회원만 값이 있고(카카오·구글·네이버는 NULL), 값이 있는 경우 서로 중복되면 안 된다.
-- PostgreSQL의 UNIQUE 인덱스는 NULL을 서로 다른 값으로 취급하므로, NULL은 여러 개 허용되고
-- 실제 sub 값만 유일하게 유지된다.
ALTER TABLE users ADD COLUMN apple_sub VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_apple_sub ON users (apple_sub);
