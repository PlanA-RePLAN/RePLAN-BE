-- 프론트엔드 기본 태그(Study/Project/Health/Other)를 회원가입 시 자동 생성하도록 변경하면서,
-- 이미 가입된 유저들에게도 동일한 기본 태그를 1회성으로 백필한다.
INSERT INTO tag (title, color, user_id, created_at)
SELECT v.title, v.color, u.id, CURRENT_TIMESTAMP
FROM users u
CROSS JOIN (VALUES
    ('Study', '#FFEBE7'),
    ('Project', '#F9ECF8'),
    ('Health', '#E4F5EE'),
    ('Other', '#E5EDFF')
) AS v(title, color)
WHERE u.deleted_at IS NULL;
