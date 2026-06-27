-- soft-delete된 todo도 unique index에 포함되어 skip→unskip 후 배치가 실패하는 문제 수정.
-- 전체 unique index → soft-delete 제외 partial unique index로 교체.
DROP INDEX uq_todo_routine_duedate;
CREATE UNIQUE INDEX uq_todo_routine_duedate
    ON todo (routine_id, due_date)
    WHERE deleted_at IS NULL;
