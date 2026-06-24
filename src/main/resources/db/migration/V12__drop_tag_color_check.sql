-- 태그 색상을 enum 이름값(RED/BLUE 등)에서 hex 코드(#3B82F6)로 전환할 때(V8),
-- 운영 DB에 남아 있던 옛 검사 제약조건(tag_color_check)을 함께 지우지 않아
-- hex 색상 저장이 거부되며 색상 있는 태그 생성·수정이 500으로 실패했다.
-- 옛 제약조건을 제거한다. 색상 형식 검증은 애플리케이션(TagService)에서 이미 수행한다.
-- IF EXISTS: 로컬 등 이 제약이 없는 DB에서도 오류 없이 통과시키기 위함.
ALTER TABLE tag DROP CONSTRAINT IF EXISTS tag_color_check;
