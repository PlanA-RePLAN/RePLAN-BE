-- 통계 탭 세 번째 페이지 "팁노트".
-- 작성 팁 텍스트는 월별 1개라서 (유저+월당 1행인) monthly_report에 컬럼으로 두고,
-- 추천 카드는 월별 여러 개(2~4개)라서 별도 테이블에 행으로 둔다.
ALTER TABLE monthly_report
    ADD COLUMN tip_note_text TEXT;

CREATE TABLE tip_note_item
(
    id                BIGSERIAL PRIMARY KEY,
    monthly_report_id BIGINT       NOT NULL REFERENCES monthly_report (id),
    -- ADD_TODO(새 일반 투두) / ADD_ROUTINE(새 루틴) / MODIFY_ROUTINE(기존 루틴 수정)
    action            VARCHAR(32)  NOT NULL,
    -- MODIFY_ROUTINE의 수정 대상 루틴. 나머지 action은 NULL
    target_routine_id BIGINT REFERENCES routine (id),
    title             VARCHAR(255) NOT NULL,
    tag_id            BIGINT REFERENCES tag (id),
    -- ADD_TODO 전용: 일반 투두의 마감일시
    todo_due_at       TIMESTAMP,
    -- 루틴 카드 전용: 반복 종료일(NULL이면 무기한) / 반복 시각 / 반복 유형 / 반복 날짜 배열
    routine_end_at    TIMESTAMP,
    routine_time      TIME,
    routine_type      VARCHAR(16),
    -- WEEKLY: 요일 인덱스 배열(월=0…일=6), MONTHLY: 일자 배열(1~31), DAILY: NULL
    routine_days      JSONB,
    -- MODIFY_ROUTINE 전용: 화면 diff용 before→after 목록 (서버가 DB 기준으로 계산해 저장)
    changed_fields    JSONB,
    -- PENDING(대기) / APPLIED(반영됨) / DISMISSED(반영 없이 접음)
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    sort_order        INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP
);

-- 조회는 항상 "그 달 리포트의 카드들"로 들어오므로 리포트 기준 인덱스만 둔다.
CREATE INDEX idx_tip_note_item_report
    ON tip_note_item (monthly_report_id)
    WHERE deleted_at IS NULL;
