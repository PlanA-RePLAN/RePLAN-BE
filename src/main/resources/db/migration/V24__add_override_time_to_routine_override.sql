-- 회차 예외(routine_override)에 그 날짜 회차만의 마감시간을 저장한다.
-- NULL이면 루틴 기본 시간(routine.routine_time)을 따른다 (기존 title/tag의 null 의미와 동일).
ALTER TABLE routine_override ADD COLUMN override_time time;
