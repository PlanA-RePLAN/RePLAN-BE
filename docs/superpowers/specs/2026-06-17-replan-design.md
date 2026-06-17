# 리플랜(RePlan) 기능 설계

> 작성일: 2026-06-17
> 대상 도메인: `domain/replan` (+ `todo`, `routine`, `monthlyreport` 연동)

## 1. 한 줄 정의

오늘 못 한 할 일(투두)을 두고 **왜 못 했는지** 물어본 뒤, AI가 그 이유에 맞춰
**기존 투두를 고치거나 새 투두를 제안**해주는 기능. "자책 말고 다시 계획 짜자(RePlan)".

## 2. 전체 흐름

```text
실패한 투두에서 "리플랜" 시작
  → 1단계: 큰 분류 선택 (심리적 저항 / 컨디션 난조 / 목표 개선 필요 / 예상치 못한 방해 / 직접 입력)
  → 2단계: 세부 이유 선택
  → 3단계: (일부 이유만) 더 세부 선택        ← 트리는 프론트에 하드코딩
  → (AI가 필요하면) 추가 질문                 ← POST /replans/questions
  → "이렇게 정리했어요" (AI가 입력을 요약)     ← /recommend 응답의 summary
  → "다음과 같은 투두리스트 제안" (diff 파란색) ← /recommend 응답의 operations[]
  → [투두 추가하기] or [추가 없이 끝내기]       ← POST /replans
```

## 3. 공통 시스템 룰 (스펙 1번)

1. **정보 부족 시 AI가 임의 생성하지 않고 추가 질의** → `/questions` 단계.
2. **결과는 기존 투두 형식 유지** — 제목 / 마감기한 / 태그 / 반복여부. 완전히 새로운 형태 금지.
3. **억지스러운 멘탈케어성 내용은 투두로 만들지 않음** → `tipNote`(줄글 가이드)로 안내.
4. **상반된 안은 동시 노출 금지** — A안(디폴트) 먼저, 새로고침 시 다른 안.
5. **바뀐 부분은 파란색 강조** → 응답의 `changedFields`(원래값→새값)로 표현.

## 4. API 설계 — 상태 없는 3개

기존 `GoalAiService`의 refine/recommend 패턴과 동일하게, 백엔드는 상태를 들지 않고
프론트가 매 단계 컨텍스트를 다시 넘긴다.

### 4.1 `POST /api/replans/questions` — 추가 질문 받기

실패사유(+직접입력)를 받아, AI가 추가로 물어볼 질문을 반환한다. 없으면 빈 배열.

요청:
```json
{
  "anchorTodoId": 42,
  "reasonCodes": ["GOAL_NEEDS_IMPROVEMENT", "GOAL_NO_PRIORITY"],
  "directInput": null
}
```

응답:
```json
{
  "questions": [
    { "key": "priority_targets", "type": "TODO_SELECT",
      "title": "우선순위 설정 추천 받고 싶은 투두 선택" },
    { "key": "priority_hint", "type": "CHIP",
      "title": "우선순위 설정을 위한 참고 사항",
      "chips": ["마감기한순", "오래 걸리는 것부터", "과제 제출이 1순위"] },
    { "key": "priority_free", "type": "TEXT", "title": "답변을 입력해주세요" }
  ]
}
```

질문 타입 3종:
- `TEXT` — 자유 텍스트 (예: "공부 관련 정보 입력")
- `TODO_SELECT` — 내 투두리스트에서 다중 선택 (기존 투두 목록 API `filter=all` 재사용)
- `CHIP` — 제시된 칩 중 선택

### 4.2 `POST /api/replans/recommend` — 추천 받기

실패사유 + 추가질문 답변을 받아, AI 요약(`summary`)과 추천 작업 목록(`operations`)을 반환.
**새로고침**은 이 API를 그대로 재호출(매번 다른 안). 호출 횟수 제한(3회)은 프론트가 관리.

요청:
```json
{
  "anchorTodoId": 42,
  "reasonCodes": ["GOAL_NEEDS_IMPROVEMENT", "GOAL_NO_PRIORITY"],
  "answers": [
    { "key": "priority_targets", "selectedTodoIds": [11, 12, 13] },
    { "key": "priority_hint", "selectedChips": ["과제 제출이 1순위"] },
    { "key": "priority_free", "text": "..." }
  ]
}
```

응답:
```json
{
  "summary": "강의 챕터 구성: 2챕터\n강의 분량: 총 4강",
  "tipNote": "과부하 + 컨디션 저하가 주된 패턴이에요. 무리하지 않는 구조로 ...",
  "operations": [
    {
      "action": "MODIFY_TODO",
      "targetTodoId": 42,
      "title": "데이터 분석 1~2강 수강",
      "dueDate": "2026-06-08T23:59:00",
      "tagId": 5,
      "routineType": null,
      "routineDate": null,
      "changedFields": [
        { "field": "title", "before": "데이터 분석 공부하기", "after": "데이터 분석 1~2강 수강" },
        { "field": "dueTime", "before": "2026-06-07T10:00:00", "after": "2026-06-08T23:59:00" }
      ]
    },
    {
      "action": "ADD",
      "targetTodoId": null,
      "title": "데이터 분석 3~4강 수강",
      "dueDate": "2026-06-09T23:59:00",
      "changedFields": [ { "field": "title", "before": null, "after": "데이터 분석 3~4강 수강" } ]
    }
  ]
}
```

- `summary` — "이렇게 정리했어요" 화면용. AI가 사용자 자유입력을 구조화해 되보여줌.
- `tipNote` — 줄글 가이드(멘탈케어/조율 방향).
- `changedFields.field` ∈ `{ title(내용), dueTime(시간), tag(태그), routineType(반복) }`. 프론트는 이걸 파란색으로 렌더.

### 4.3 `POST /api/replans` — 수락 저장

사용자가 추천 화면에서 체크한 operation을 실제 DB에 반영한다. 체크 안 해도(추가 없이 끝내기)
**실패사유는 항상 기록**한다.

요청:
```json
{
  "anchorTodoId": 42,
  "reasonCodes": ["GOAL_NEEDS_IMPROVEMENT", "GOAL_NO_PRIORITY"],
  "acceptedOperations": [
    { "action": "MODIFY_TODO", "targetTodoId": 42, "title": "...", "dueDate": "...", "tagId": 5 },
    { "action": "ADD", "title": "...", "dueDate": "..." }
  ]
}
```

처리:
1. `Replan` 행 생성 (anchor todo + `failureReason1~3`). **acceptedOperations가 비어도 생성.**
2. 각 operation 반영 (아래 5장 규칙).
3. 새로 만든/수정한 todo·routine에 `replan_id` 연결(`linkReplan`).

## 5. 수정 액션 규칙

`action` ∈ `{ ADD, MODIFY_TODO, MODIFY_ROUTINE, CREATE_ROUTINE }`.

### 5.1 앵커(실패한 투두) 처리 — 종류·기한에 따라 갈림

**기본(일반) 투두** — 실패 투두의 `dueDate` 기준:
- 마감 **안 지남** (= 아직 실패 아님) → `MODIFY_TODO` (그 투두 in-place 수정)
- 마감 **지남** (= 실패함) → `ADD` (새 투두 생성, **원본은 손대지 않음**)

**루틴 투두** — 루틴의 종료기한 `Routine.dueDate` 기준:
- 종료기한 **안 지남** → `MODIFY_ROUTINE`
  1. 루틴 규칙(`routineType`/`routineDate`/`routineTime`/`title`/`tag`) 수정
  2. 이번 회차로 생성된 Todo가 **미완료** → 새 규칙에 맞게 그 Todo도 수정
  3. 그 Todo가 **완료** → 그대로 둠
- 종료기한 **지남** → `CREATE_ROUTINE` (기존 루틴 그대로, 새 루틴 생성)

> 루틴은 "이번 회차만 수정"이 없다. 항상 **루틴(규칙) 기준**. "회차 동기화"는 수락 시점에
> 백엔드가 처리하며, 응답에는 화면용 diff(반복: 위클리→먼슬리 등)만 담는다.

### 5.2 범위 — 앵커 1개 수정 + N개 추가

- 한 리플랜의 **수정(MODIFY)은 항상 앵커 1개**. **추가(ADD)는 여러 개** 가능, 사용자가 체크한 것만 반영.
- **예외 — 우선순위(3-3, `GOAL_NO_PRIORITY`)**: 선택한 기존 투두 여러 개를 모두 `MODIFY_TODO`로
  제목 앞에 `[1] [2] [3]` 등수 부여 (유일한 다중 수정 케이스).

## 6. 데이터·통계 정합성 (중요)

### 6.1 실패 흔적은 `Replan` 테이블이 보존

월간 통계의 실패 관련 지표(실패사유 분포·리플랜 횟수·실패 패턴)는 전부 `Replan` 행에서 계산된다
(`MonthlyReportCalculator`). 따라서 투두를 어떻게 수정하든 **실패사유 기록은 안전**하다.

### 6.2 완료율(achievement) 통계는 `dueDate`로 버킷팅 — 그래서 원본을 보존

`findMonthlyTodos`는 투두의 `dueDate`가 그 달에 걸리는지로 집계한다. 그래서:
- **마감 지난(실패한) 기본투두를 in-place로 수정하면 완료율이 오염**된다
  (마감을 미루면 그 달에서 실패가 사라지거나 다음 달로 이동).
- → 그래서 5.1처럼 **마감 지난 건 MODIFY 금지, ADD로 새 투두를 만들고 원본은 손대지 않는다.**
  원본은 그 달 통계에 "실패 1건"으로 그대로 남는다.

### 6.3 `is_active` — 목록에서만 숨기고 통계엔 남김

마감 지난 원본을 ADD로 대체할 때, 원본이 목록에 계속 떠서 지저분하다. 해결:
- 원본 `is_active = false`로 설정.
- **목록 조회 쿼리에만 `AND is_active = true` 추가** → 화면에서 사라짐.
- **통계 쿼리에는 넣지 않음** → 실패로 계속 카운트.

| 종류 | 쿼리 | `is_active` 조건 |
|------|------|-----------------|
| 목록(화면) | `findActiveTodosForUser` (all) | 추가 ✅ |
| 목록(화면) | `findActiveTodosByDueDateRange` (day) | 추가 ✅ |
| 목록(화면) | `findCompletedTodosByCompletedTimeRange` (day 완료분) | 추가 ✅ |
| 목록(화면) | `findAllTodosByDueDateRange` (week/month) | 추가 ✅ |
| 목록(화면) | `findPinnedActiveTodosForUser` (고정) | 추가 ✅ |
| 통계 | `findMonthlyTodos` | **넣지 않음** ❌ |
| 통계 | `findReplanDerivedMonthlyTodos` | **넣지 않음** ❌ |

> 현재 `is_active`는 전부 `true`라, 조건을 넣어도 기존 동작은 바뀌지 않는다(리플랜이 false를
> 만들기 시작할 때부터 효과 발생). `is_active`/`deactivate()`는 V6에서 미리 추가됐으나 현재 미사용.

### 6.4 `replan_id` 연결

ADD/MODIFY로 생긴 파생 투두·루틴에 `replan_id`를 연결해야 `replanAchievementEffect`
(리플랜 효과 = 완료된 파생투두 ÷ 리플랜수)가 측정된다.

### 6.5 알려진 한계 (이번 범위 밖)

완료율이 `dueDate` 기준이라, 리플랜이 마감을 **다른 달로** 미루면 실패는 이번 달(Replan.createdAt)에
기록되지만 완료 집계는 다음 달로 넘어가 **리플랜 효과가 달 경계에서 과소집계**될 수 있다.
이는 기존 통계 설계의 특성이며, 이번 리플랜 범위에서는 고치지 않고 한계로 남긴다.

## 7. 재사용성 — 통계 "팁노트" 추천과 엔진 공유

통계(월간리포트) 도메인의 "팁노트" 탭은 현재 텍스트 팁(`AiInsight.writingTip`)만 구현돼 있고,
**추천 투두 리스트는 미구현**이다. 이 추천 리스트는 리플랜 추천을 "단일 실패투두" 대신
"지난달 pastData 전체"로 돌리는 것과 같다.

→ 리플랜 추천 로직을 **입력 어댑터로 분리**하여 공용 엔진으로 둔다. 입력만 두 갈래:
- 단일 실패투두 컨텍스트 (리플랜)
- 지난달 실패패턴 묶음(pastData) (통계 팁노트)

출력(`summary`+`operations`)은 동일. 통계 팁노트 추천은 이 엔진을 재사용해 구현한다.

## 8. 실패 카테고리별 AI 로직 (스펙 2~5번 요약)

프롬프트에 반영할 로직. 결과는 모두 `operations`(+`tipNote`)로 표현된다.

- **심리적 저항**: 마일스톤 쪼개기 / 타임박싱(15분) / 초안 쓰기 / 딴짓 유형별 환경통제(추가질문)
- **컨디션 난조**: 예열 태스크 전환(집중 시간 질의) / 수면 최우선 / 통증 유형별 분기(질의)
- **목표 개선**: 로드맵화(분량·마감 질의) / 하루 과부하 분산 / 마감 +1시간 / 우선순위 `[n]` / 마감 +30분 / 30분 시도 후 중단 룰
- **예상치 못한 방해**: 마감 +2시간 or 다음날 / 환경확보 선행 / 디지털 단절 / 직접 미룰 마감 지정 / 연쇄지연(반복=시작 15분↑, 단일=간격 벌리기)
- **엣지케이스**: 매핑 안 되는 복합 사유는 억지 매핑 없이 맥락 기반 맞춤 생성(포맷 제약은 유지)

## 9. 구현 영향 요약

- **신규**: `ReplanController` / `ReplanControllerDocs` / `ReplanService` / 추천 AI 서비스(공용 엔진) / DTO 일습.
- **수정**: 목록 조회 쿼리 5종에 `is_active = true` 추가, `TodoService`/`RoutineService`에 리플랜 반영 헬퍼.
- **기존 활용**: `Replan` 엔티티, `FailureReasonCode` enum, `Todo.linkReplan/deactivate`,
  `Routine.linkReplan/deactivate`, `GoalAiService`의 Gemini 호출 패턴.
- **짝꿍 주의**: 프론트의 실패사유 코드 ↔ 백엔드 `FailureReasonCode` enum 이름 동기화(통계 묶음용),
  서비스 throw ↔ `*ControllerDocs` 에러 예시 동기화.
