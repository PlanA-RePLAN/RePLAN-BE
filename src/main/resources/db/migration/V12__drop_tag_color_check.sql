-- hex 색상 전환(V8) 때 남은 옛 색상 검사 제약 제거. 색상 검증은 앱(TagService)에서 수행.
ALTER TABLE tag DROP CONSTRAINT IF EXISTS tag_color_check;
