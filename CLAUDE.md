# CLAUDE.md

## 필수 규칙

### 작업 시작 절차
모든 신규 작업은 다음 순서로 시작한다.

1. `git checkout develop && git pull` — develop 브랜치 최신화
2. GitHub 이슈 생성 — 반드시 `.github/ISSUE_TEMPLATE/이슈-템플릿.md` 템플릿 구조(`Issue?`, `Details`, `References`)를 그대로 사용한다.
3. `git checkout -b {type}/{issueNumber}-{slug}` — 이슈 번호를 포함한 브랜치를 develop 기준으로 생성하고 체크아웃
4. 위 절차를 완료한 뒤에야 코드 작업을 시작한다.

### 빌드 & 테스트
**매 작업 완료 후 반드시 빌드(테스트 포함)를 실행해야 한다.**

```bash
./gradlew build
```

빌드가 실패하면 해당 작업은 완료된 것으로 간주하지 않는다.

### PR / 커밋 작성 규칙
- PR 본문, 커밋 메시지에 AI가 작성했다는 흔적을 절대 남기지 않는다.
- `Co-Authored-By: Claude`, `🤖 Generated with Claude Code` 등 일체 금지.
- 사람이 직접 작성한 것처럼 자연스럽게 작성한다.

### 커밋 단위 작업 순서
**커밋을 몰아서 하지 않는다. 반드시 커밋 단위로 작업하고 즉시 커밋한다.**

올바른 순서:
1. 커밋 1에 해당하는 파일 작업 → 빌드 → 커밋 1
2. 커밋 2에 해당하는 파일 작업 → 빌드 → 커밋 2
3. ...

**절대 금지:** 모든 작업을 먼저 끝낸 뒤 `git add`로 파일을 골라 커밋을 나눠서 올리는 방식.

### Swagger API 명세

규칙 전문은 `.claude/rules/swagger.md` 참고.
