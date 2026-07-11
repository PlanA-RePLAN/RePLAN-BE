-- 알림 설정을 알림 종류별(마감 임박/실패 리플랜/리포트) 3개에서
-- 카테고리별(투두/통계/공지/광고)로 개편하고, 마케팅 정보 수신 동의를 저장한다.

-- 1. 투두 알림: 두 토글을 하나로 합친다 (둘 중 하나라도 켜져 있으면 켜짐)
ALTER TABLE users ADD COLUMN notify_todo BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE users SET notify_todo = (notify_todo_due OR notify_todo_failed);
ALTER TABLE users DROP COLUMN notify_todo_due;
ALTER TABLE users DROP COLUMN notify_todo_failed;

-- 2. 리포트 알림 → 통계 카테고리로 이름 변경
ALTER TABLE users RENAME COLUMN notify_report TO notify_stats;

-- 3. 공지 알림 토글 (공지 발송 기능은 추후 작업, 설정 자리만 준비)
ALTER TABLE users ADD COLUMN notify_notice BOOLEAN NOT NULL DEFAULT TRUE;

-- 4. 마케팅 정보 수신 동의: 여부 + 마지막으로 동의/철회한 시각.
--    기존 회원은 동의받은 적이 없으므로 전부 false(시각 없음)로 시작한다.
ALTER TABLE users ADD COLUMN marketing_agreed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN marketing_agreed_at TIMESTAMP;
