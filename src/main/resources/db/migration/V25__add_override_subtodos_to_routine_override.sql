-- 미래 회차에 예약해 둔 하위 투두 제목 목록.
-- 행(Todo)이 아직 없는 날짜의 하위 투두는 여기에 제목 배열로 저장하고,
-- 배치가 그날 행을 만들 때 실제 하위 투두로 실체화한 뒤 비운다.
ALTER TABLE routine_override ADD COLUMN override_subtodos jsonb;
