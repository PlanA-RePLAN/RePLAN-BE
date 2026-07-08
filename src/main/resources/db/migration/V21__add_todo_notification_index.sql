-- 알림 스케줄러의 마감 임박/실패 투두 조회(TodoRepository.findPinnedDueBetween,
-- findFailedBetween)를 위한 부분 인덱스.
-- 두 쿼리는 공통으로 parent_id IS NULL AND is_completed = false AND is_active = true 조건에서
-- due_date 범위를 조회한다. 인덱스가 없으면 todo 테이블 전체를 순차 스캔(Seq Scan)하므로,
-- 조회 조건에 맞춘 부분 인덱스로 인덱스 스캔이 되도록 한다.
CREATE INDEX IF NOT EXISTS idx_todo_due_active
    ON todo (due_date)
    WHERE parent_id IS NULL AND is_completed = false AND is_active = true;
