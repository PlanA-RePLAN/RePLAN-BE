-- 1. routine_time 컬럼 추가
ALTER TABLE routine
    ADD COLUMN routine_time TIME;

-- 2. 하위 루틴 constraint 재정의 (routine_time IS NULL 조건 추가)
ALTER TABLE routine
    DROP CONSTRAINT ck_routine_child_has_no_schedule;

ALTER TABLE routine
    ADD CONSTRAINT ck_routine_child_has_no_schedule
        CHECK (
            parent_id IS NULL
                OR (
                       routine_type IS NULL
                           AND routine_date IS NULL
                           AND due_date IS NULL
                           AND tag_id IS NULL
                           AND goal_id IS NULL
                           AND routine_time IS NULL
                   )
            );

-- 3. 기존 tag.color enum 값 NULL 처리 (hex 코드 방식으로 전환)
UPDATE tag
SET color = NULL
WHERE color IS NOT NULL;
