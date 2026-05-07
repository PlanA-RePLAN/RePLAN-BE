# CLAUDE.md

## 절대 규칙

### 빌드
매 작업 완료 후 반드시 실행. 실패 시 작업 미완료.
```bash
./gradlew build
```

### TDD
테스트 먼저 작성 → 실패 확인 → 구현 → 통과 확인.
→ 상세 규칙: `.claude/rules/tdd.md`

### Swagger
모든 API에 필수. `XxxControllerDocs` 인터페이스 분리 패턴 사용.
→ 상세 규칙: `.claude/rules/swagger.md`

### Git (이슈/브랜치/커밋/PR)
PR·커밋에 AI 흔적 절대 금지 (`Co-Authored-By`, `🤖` 등).
→ 상세 규칙: `.claude/rules/git.md`
