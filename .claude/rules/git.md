# Git 규칙

## 이슈 생성

`.github/ISSUE_TEMPLATE/이슈-템플릿.md` 형식을 반드시 사용한다.

```
## Issue?
(무엇을 구현/수정하는지 한 문장 요약)

## Details
- [ ] 세부 작업 항목 1
- [ ] 세부 작업 항목 2

## References(Optional)
관련 링크 또는 참고 자료
```

## 브랜치명

형식: `prefix/이슈번호-키워드`

| prefix | 사용 시점 |
|--------|-----------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `chore` | 설정/의존성 변경 |
| `refactor` | 리팩토링 |
| `docs` | 문서 작업 |

예시: `feat/75-goal-api`, `fix/60-cors-config`, `docs/77-process-docs`

## 커밋 메시지

- 한국어, Conventional Commits 형식
- AI 흔적 **절대 금지**: `Co-Authored-By: Claude`, `🤖 Generated with` 등

```
feat: 목표(Goal) CRUD API 구현 (#75)
fix: CORS 설정 수정 (#60)
chore: Swagger 작성 규칙 추가
```

## PR

- **base 브랜치**: `develop`
- 생성 전 `gh pr list --head 브랜치명`으로 중복 확인
- 제목: `prefix: 설명 (#이슈번호)`
- 본문 구성:
  ```
  ## Summary
  - 변경 사항 bullet points

  ## Test plan
  - [ ] 테스트 항목

  Closes #이슈번호
  ```
- PR 본문에도 AI 흔적 금지
