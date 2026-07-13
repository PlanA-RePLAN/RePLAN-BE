-- 예약 하위 투두 완료 지원: override_subtodos 원소를 제목 문자열에서 {title, isCompleted} 객체로 승격
UPDATE routine_override
SET override_subtodos = (
    SELECT jsonb_agg(jsonb_build_object('title', elem, 'isCompleted', false))
    FROM jsonb_array_elements_text(override_subtodos) AS elem
)
WHERE override_subtodos IS NOT NULL
  AND jsonb_typeof(override_subtodos) = 'array'
  AND jsonb_array_length(override_subtodos) > 0
  AND jsonb_typeof(override_subtodos -> 0) = 'string';
