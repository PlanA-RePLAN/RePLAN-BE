-- 매월(MONTHLY) 반복 루틴을 '일자 하나(1~31)'에서 '일자 비트마스크'로 바꾼다.
-- 이제 여러 날짜를 한 루틴에 담을 수 있고, 규칙은 WEEKLY(요일 비트마스크)와 동일하다.
-- 기존에 저장된 값은 '일자 d'였으므로 비트 (d-1)만 켠 값 (1 << (d-1)) 으로 1회 변환한다.
-- 예) 15일 -> 1 << 14 = 16384,  3일 -> 1 << 2 = 4.
-- 기존 값은 항상 1~31 범위였으므로 최대 1 << 30 = 1073741824 로 int 범위 안에 들어간다.
UPDATE routine
SET routine_date = (1 << (routine_date - 1))
WHERE routine_type = 'MONTHLY'
  AND routine_date BETWEEN 1 AND 31;
