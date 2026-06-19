# 목표 추가 프로세스 3단계(탐색·정제·추천) 개편 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 목표 추가를 "탐색 → 정제 → 추천" 3단계로 바꾼다. AI가 목표에 맞는 질문을 동적으로 만들고(탐색), 답변을 구조화해 정리하고(정제), 그걸로 투두를 추천한다(추천).

**Architecture:** AI 호출 로직은 기존 `GoalAiService` 한 곳에 모은다. 탐색용 메서드/프롬프트/파서를 새로 추가하고, 정제·추천의 입출력 DTO와 프롬프트를 동적 질문 구조에 맞게 갈아끼운다. 컨트롤러는 `GoalController`에 엔드포인트를 하나(`/ai/explore`) 추가하고, Swagger는 `GoalControllerDocs`에 문서를 채운다.

**Tech Stack:** Java 17, Spring Boot, Spring Web `RestClient`(Gemini 호출), Jackson(`ObjectMapper`) JSON 파싱, JUnit5 + AssertJ, Gradle.

## Global Constraints

- 설명/주석/문서/커밋은 **비개발자도 이해할 수 있는 쉬운 말**로 쓴다. `P1`/`유니크 인덱스` 같은 약어·기술용어 금지. (CLAUDE.md)
- 커밋은 **커밋 단위로 작업 후 즉시** 한다. 몰아서 하지 않는다.
- AI가 작성했다는 흔적(`Co-Authored-By`, `Generated with` 등) 절대 남기지 않는다.
- Swagger 문서는 `.claude/rules/swagger.md` 규칙을 그대로 따른다: Docs 인터페이스/구현 분리, JSON 타입 표기, 필수(✅)·선택(❌) 이모지, 발생 가능한 모든 에러 `@ApiResponse` 예시, optional 필드 있는 POST는 RequestBody 예시 2개(전체/필수만). **이 규칙은 `swagger-docs` 스킬을 호출해서 적용한다.**
- 서비스에 `throw new CustomException(...)`을 추가하면 해당 엔드포인트 `*ControllerDocs`의 `@ApiResponse` 에러 예시도 같이 업데이트한다. (CLAUDE.md 짝꿍 규칙)
- JPQL/DB 함수는 이 작업 범위 밖(AI 호출만 다룸).
- Gemini 모델·호출 방식은 기존 그대로 유지한다: `gemini-3.1-flash-lite:generateContent`, `callGemini(prompt)` 재사용.
- 테스트는 기존 `GoalAiServiceTest` 패턴을 따른다: `new GoalAiService(null)`로 만들어 프롬프트 빌더·파서·검증 같은 순수 로직만 단위 테스트(실제 Gemini 호출 없음). 따라서 새 프롬프트 빌더·파서 메서드는 **package-private**로 둬서 테스트에서 직접 호출한다.

## 설계 결정 (확정)

1. **탐색 검증 실패는 에러가 아니라 정상 응답(200) + 플래그.** 목표가 아닌 이상한 입력이면 `valid=false` + 안내 메시지를 200으로 돌려준다. 같은 화면에서 다시 입력받는 흐름이라 400으로 던지지 않는다. → 새 에러코드 추가 없음.
2. **정제 결과는 사용자가 받은 질문대로 동적 출력.** 프론트가 탐색에서 받은 질문과 사용자 답변을 정제 API로 다시 보내고, 정제 결과도 그 질문들에 맞춰(질문별 솔루션) 나온다. 고정 3필드(현재수준/투자가능시간/특이사항) 폐기.
3. **질문의 예시 칩도 Gemini가 생성.**
4. **종료일정은 자연어가 아니라 날짜·시간(구조화)로 받는다.** 탐색 화면에서 날짜/시간 피커로 고르므로, 정제 입력의 deadline은 `deadlineDate`(yyyy-MM-dd)·`deadlineTime`(HH:mm)로 받는다. (기존 정제의 자연어 `deadline` 폐기)
5. **정제·추천의 요청/응답은 하위호환을 깨는 변경(breaking change).** 프론트가 3단계 흐름으로 새로 만들어지므로 기존 구조를 정리한다.

## File Structure

**탐색 (신규)**
- Create: `src/main/java/plana/replan/domain/goal/dto/explore/GoalExploreRequest.java` — 탐색 요청(목표 + 종료 날짜/시간)
- Create: `src/main/java/plana/replan/domain/goal/dto/explore/GoalExploreResponse.java` — 탐색 응답(valid, message, questions)
- Create: `src/main/java/plana/replan/domain/goal/dto/explore/ExploreQuestion.java` — 질문 1개(질문 텍스트 + 예시 칩 목록)

**정제 (수정)**
- Modify: `src/main/java/plana/replan/domain/goal/dto/refine/GoalRefinementRequest.java` — 동적 질문/답변 입력으로 교체
- Modify: `src/main/java/plana/replan/domain/goal/dto/refine/GoalRefinementResponse.java` — 동적 솔루션 출력으로 교체
- Create: `src/main/java/plana/replan/domain/goal/dto/refine/QuestionAnswer.java` — 질문 + 사용자 답변 한 쌍(요청용)
- Create: `src/main/java/plana/replan/domain/goal/dto/refine/RefinedSolution.java` — 질문별 정제 결과(질문 + 항목 목록 + 근거)
- Reuse: `RefinedField`(goal), `RefinedDeadline`(deadline), `RefinedNoteItem`(솔루션 항목 title/content로 재사용)
- Remove: `RefinedNotes.java` (동적 솔루션으로 대체되어 불필요)

**추천 (수정)**
- Modify: `src/main/java/plana/replan/domain/goal/dto/recommend/TodoRecommendationRequest.java` — 솔루션 목록 입력으로 교체
- Create: `src/main/java/plana/replan/domain/goal/dto/recommend/SolutionInput.java` — 정제 후 최종 솔루션(질문 + 항목 목록)
- Reuse: `RefinedNoteItem`을 항목(title/content)으로 재사용. 응답(`TodoRecommendationResponse`, `RecommendedTodo`)은 변경 없음.

**서비스 / 컨트롤러**
- Modify: `src/main/java/plana/replan/domain/goal/service/GoalAiService.java` — `exploreGoal` 신규, `refineGoal`/`recommendTodos` 프롬프트·파서 수정
- Modify: `src/main/java/plana/replan/domain/goal/controller/GoalController.java` — `POST /api/goals/ai/explore` 추가
- Modify: `src/main/java/plana/replan/domain/goal/controller/GoalControllerDocs.java` — 탐색 문서 추가, 정제·추천 문서 갱신

**테스트**
- Modify: `src/test/java/plana/replan/domain/goal/service/GoalAiServiceTest.java` — 탐색/정제/추천 프롬프트·파서 테스트 추가·수정

---

## Task 1: 목표 탐색 — DTO·서비스·파서

**Files:**
- Create: `src/main/java/plana/replan/domain/goal/dto/explore/GoalExploreRequest.java`
- Create: `src/main/java/plana/replan/domain/goal/dto/explore/GoalExploreResponse.java`
- Create: `src/main/java/plana/replan/domain/goal/dto/explore/ExploreQuestion.java`
- Modify: `src/main/java/plana/replan/domain/goal/service/GoalAiService.java`
- Test: `src/test/java/plana/replan/domain/goal/service/GoalAiServiceTest.java`

**Interfaces:**
- Produces:
  - `record GoalExploreRequest(String goal, String deadlineDate, String deadlineTime)`
  - `record ExploreQuestion(String question, List<String> chips)`
  - `record GoalExploreResponse(boolean valid, String message, List<ExploreQuestion> questions)`
  - `GoalAiService.exploreGoal(GoalExploreRequest) : GoalExploreResponse`
  - package-private `GoalAiService.buildExplorePrompt(GoalExploreRequest, String today) : String`
  - package-private `GoalAiService.parseExploreResponse(String raw) : GoalExploreResponse`

- [ ] **Step 1: DTO 3개 작성**

`GoalExploreRequest.java`:
```java
package plana.replan.domain.goal.dto.explore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 목표 탐색 요청 (목표 + 종료일정만 받아 되물을 질문을 생성)")
public record GoalExploreRequest(
    @Schema(description = "목표 (자연어)", example = "토익 850점 이상 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(description = "종료 날짜 (yyyy-MM-dd 형식). 선택", example = "2026-05-01")
        String deadlineDate,
    @Schema(description = "종료 시간 (HH:mm 형식). 선택", example = "23:59")
        String deadlineTime) {}
```

`ExploreQuestion.java`:
```java
package plana.replan.domain.goal.dto.explore;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI가 생성한 질문 1개 (질문 + 예시 칩)")
public record ExploreQuestion(
    @Schema(description = "질문 텍스트", example = "현재 영어 실력") String question,
    @Schema(description = "사용자가 바로 누를 수 있는 예시 답변 칩 목록")
        List<String> chips) {}
```

`GoalExploreResponse.java`:
```java
package plana.replan.domain.goal.dto.explore;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 목표 탐색 결과")
public record GoalExploreResponse(
    @Schema(description = "달성 가능한 목표인지 여부. false면 questions는 비어있다", example = "true")
        boolean valid,
    @Schema(description = "valid=false일 때 사용자에게 보여줄 안내 메시지. valid=true면 null",
            example = "달성할 수 있는 목표를 입력해주세요.")
        String message,
    @Schema(description = "AI가 생성한 질문 목록 (valid=true일 때 3개)")
        List<ExploreQuestion> questions) {}
```

- [ ] **Step 2: 탐색 프롬프트 빌더 + 파서 + 공개 메서드 작성**

`GoalAiService.java`에 import 추가(`GoalExploreRequest`, `GoalExploreResponse`, `ExploreQuestion`) 후, 클래스 안에 추가:
```java
public GoalExploreResponse exploreGoal(GoalExploreRequest request) {
  String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  String prompt = buildExplorePrompt(request, today);
  String raw = callGemini(prompt);
  return parseExploreResponse(raw);
}

String buildExplorePrompt(GoalExploreRequest req, String today) {
  return """
      당신은 목표 달성 플래닝 전문가입니다.
      사용자가 입력한 목표를 보고, 투두 리스트를 만들기 위해 추가로 물어볼 질문을 생성하세요.

      입력:
      목표: %s
      종료 날짜: %s
      종료 시간: %s
      오늘 날짜: %s

      [1단계 — 목표 유효성 판단]
      입력이 '달성할 수 있는 실제 목표'인지 판단한다.
      - 목표가 아니거나(예: 무의미한 문자열, 욕설, 목표와 무관한 잡담), 너무 모호해 어떤 계획도 세울 수 없으면
        valid를 false로, message에 "달성할 수 있는 목표를 입력해주세요."를 넣고 questions는 빈 배열로 둔다.
      - 정상 목표면 valid를 true, message는 null로 둔다.

      [2단계 — 질문 생성 (valid=true일 때만)]
      1. 목표 달성 계획에 꼭 필요한 질문을 정확히 3개 생성한다.
      2. 질문은 목표에 맞게 동적으로 만든다(고정 문구 금지). 예: 어학 목표면 현재 실력/성적/수준을 묻는 질문,
         운동 목표면 현재 체력·운동 경험을 묻는 질문 등 목표에 맞춰 워딩을 바꾼다.
      3. question은 짧은 라벨 형태로 쓴다(예: "현재 영어 실력", "투자 가능 시간", "특이사항").
      4. 각 질문마다 사용자가 바로 누를 수 있는 예시 답변(chips)을 2~3개 생성한다(짧은 단어·구).
      5. 모든 텍스트는 서술형/명사형으로 쓰고 "~하세요" 같은 명령형은 쓰지 않는다.

      반드시 아래 JSON만 출력하세요 (다른 설명 없이):
      {"valid":true,"message":null,"questions":[{"question":"","chips":["",""]}]}
      """
      .formatted(
          req.goal(),
          req.deadlineDate() != null ? req.deadlineDate() : "미입력",
          req.deadlineTime() != null ? req.deadlineTime() : "미입력",
          today);
}

GoalExploreResponse parseExploreResponse(String raw) {
  try {
    String json = extractJson(raw);
    JsonNode root = objectMapper.readTree(json);
    boolean valid = root.path("valid").asBoolean(false);
    String message = root.path("message").isNull() ? null : root.path("message").asText(null);

    List<ExploreQuestion> questions = new ArrayList<>();
    for (JsonNode q : root.path("questions")) {
      List<String> chips = new ArrayList<>();
      for (JsonNode c : q.path("chips")) {
        chips.add(c.asText());
      }
      questions.add(new ExploreQuestion(q.path("question").asText(), chips));
    }
    return new GoalExploreResponse(valid, message, questions);
  } catch (Exception e) {
    log.error("Gemini explore 응답 파싱 실패: {}", raw, e);
    throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
  }
}
```

- [ ] **Step 3: 실패 테스트 작성**

`GoalAiServiceTest.java`에 추가:
```java
@Test
void 탐색_프롬프트에_목표와_종료일정이_들어간다() {
  GoalExploreRequest req = new GoalExploreRequest("토익 850점 이상 달성", "2026-05-01", "23:59");
  String prompt = service.buildExplorePrompt(req, "2026-06-20");
  assertThat(prompt).contains("토익 850점 이상 달성");
  assertThat(prompt).contains("2026-05-01");
  assertThat(prompt).contains("23:59");
}

@Test
void 탐색_종료일정이_없으면_미입력으로_들어간다() {
  GoalExploreRequest req = new GoalExploreRequest("토익 850점", null, null);
  String prompt = service.buildExplorePrompt(req, "2026-06-20");
  assertThat(prompt).contains("미입력");
}

@Test
void 탐색_유효한_응답을_파싱한다() {
  String raw =
      "{\"valid\":true,\"message\":null,\"questions\":"
          + "[{\"question\":\"현재 영어 실력\",\"chips\":[\"토익 600점대\",\"RC 파트 취약\"]}]}";
  GoalExploreResponse res = service.parseExploreResponse(raw);
  assertThat(res.valid()).isTrue();
  assertThat(res.message()).isNull();
  assertThat(res.questions()).hasSize(1);
  assertThat(res.questions().get(0).question()).isEqualTo("현재 영어 실력");
  assertThat(res.questions().get(0).chips()).containsExactly("토익 600점대", "RC 파트 취약");
}

@Test
void 탐색_목표가_아니면_valid_false와_안내메시지를_파싱한다() {
  String raw =
      "{\"valid\":false,\"message\":\"달성할 수 있는 목표를 입력해주세요.\",\"questions\":[]}";
  GoalExploreResponse res = service.parseExploreResponse(raw);
  assertThat(res.valid()).isFalse();
  assertThat(res.message()).isEqualTo("달성할 수 있는 목표를 입력해주세요.");
  assertThat(res.questions()).isEmpty();
}
```
import 추가: `plana.replan.domain.goal.dto.explore.GoalExploreRequest`, `GoalExploreResponse`.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "plana.replan.domain.goal.service.GoalAiServiceTest"`
Expected: 탐색 관련 4개 테스트 PASS (기존 추천 테스트도 그대로 PASS).

- [ ] **Step 5: 커밋**
```bash
git add src/main/java/plana/replan/domain/goal/dto/explore src/main/java/plana/replan/domain/goal/service/GoalAiService.java src/test/java/plana/replan/domain/goal/service/GoalAiServiceTest.java
git commit -m "Feat: 목표 탐색 단계 추가 (목표에 맞는 질문·예시 칩 생성과 목표 유효성 판단)"
```

---

## Task 2: 목표 탐색 — 컨트롤러 + Swagger 문서

**Files:**
- Modify: `src/main/java/plana/replan/domain/goal/controller/GoalController.java`
- Modify: `src/main/java/plana/replan/domain/goal/controller/GoalControllerDocs.java`

**Interfaces:**
- Consumes: `GoalAiService.exploreGoal(GoalExploreRequest)` (Task 1)
- Produces: `POST /api/goals/ai/explore` → `ApiResult<GoalExploreResponse>`

- [ ] **Step 1: 컨트롤러 메서드 추가**

`GoalController.java`에 import(`GoalExploreRequest`, `GoalExploreResponse`) 추가 후:
```java
@Override
@PostMapping("/ai/explore")
public ResponseEntity<ApiResult<GoalExploreResponse>> exploreGoal(
    @AuthenticationPrincipal Long userId, @Valid @RequestBody GoalExploreRequest request) {
  return ResponseEntity.ok(ApiResult.ok(goalAiService.exploreGoal(request)));
}
```

- [ ] **Step 2: Swagger 문서 작성 (swagger-docs 스킬 사용)**

`swagger-docs` 스킬을 호출해서 `GoalControllerDocs`에 `exploreGoal` 문서를 추가한다. 규칙 준수 포인트:
- `@Operation` summary/description + 무한스크롤 아님
- Request Headers 표, Request Body 표(goal ✅ / deadlineDate ❌ / deadlineTime ❌, 선택 필드 주석 포함)
- optional 필드가 있으므로 `@io.swagger.v3.oas.annotations.parameters.RequestBody` 예시 2개(전체 / 필수만)
- `@ApiResponses`: 200(valid=true 예시 + valid=false 예시 둘 다), 400(목표 누락 `@NotBlank`), 401(EMPTY_TOKEN·EXPIRED_TOKEN 2케이스), 502(GEMINI_API_ERROR·GEMINI_PARSE_ERROR)
- 검증 실패(valid=false)는 200 정상 응답 예시로 보여주고 에러 응답으로 넣지 않는다.

- [ ] **Step 3: 컴파일·전체 테스트 확인**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**
```bash
git add src/main/java/plana/replan/domain/goal/controller/GoalController.java src/main/java/plana/replan/domain/goal/controller/GoalControllerDocs.java
git commit -m "Feat: 목표 탐색 API 엔드포인트와 Swagger 문서 추가"
```

---

## Task 3: 목표 정제 — 동적 질문/솔루션 구조로 재구성

**Files:**
- Create: `src/main/java/plana/replan/domain/goal/dto/refine/QuestionAnswer.java`
- Create: `src/main/java/plana/replan/domain/goal/dto/refine/RefinedSolution.java`
- Modify: `src/main/java/plana/replan/domain/goal/dto/refine/GoalRefinementRequest.java`
- Modify: `src/main/java/plana/replan/domain/goal/dto/refine/GoalRefinementResponse.java`
- Remove: `src/main/java/plana/replan/domain/goal/dto/refine/RefinedNotes.java`
- Modify: `src/main/java/plana/replan/domain/goal/service/GoalAiService.java`
- Test: `src/test/java/plana/replan/domain/goal/service/GoalAiServiceTest.java`

**Interfaces:**
- Consumes: `RefinedField`, `RefinedDeadline`, `RefinedNoteItem` (기존)
- Produces:
  - `record QuestionAnswer(String question, String answer)`
  - `record RefinedSolution(String question, List<RefinedNoteItem> items, String reason)`
  - `record GoalRefinementRequest(String goal, String deadlineDate, String deadlineTime, List<QuestionAnswer> answers)`
  - `record GoalRefinementResponse(RefinedField goal, RefinedDeadline deadline, List<RefinedSolution> solutions)`
  - package-private `buildRefinePrompt(GoalRefinementRequest, String today)` (시그니처 변경)
  - package-private `parseRefineResponse(String raw)` (반환 구조 변경, package-private로 노출)

- [ ] **Step 1: 요청/응답 DTO 재구성**

`QuestionAnswer.java`:
```java
package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "탐색 단계에서 받은 질문과 사용자의 답변 한 쌍")
public record QuestionAnswer(
    @Schema(description = "탐색에서 받은 질문", example = "현재 영어 실력",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "질문은 필수입니다.")
        String question,
    @Schema(description = "사용자 답변. 빈 값일 수 있음", example = "토익 600점대, RC 취약")
        String answer) {}
```

`RefinedSolution.java`:
```java
package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "질문 1개에 대한 정제 결과 (질문 + 항목 목록 + AI 근거)")
public record RefinedSolution(
    @Schema(description = "어떤 질문에 대한 정제인지", example = "현재 수준") String question,
    @Schema(description = "정제된 항목 목록 (제목 + 내용)") List<RefinedNoteItem> items,
    @Schema(description = "AI 정제 근거", example = "현재 실력과 목표 사이 격차를 영역별로 정리했습니다.")
        String reason) {}
```

`GoalRefinementRequest.java` (전체 교체):
```java
package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "AI 목표 정제 요청 (탐색에서 받은 질문/답변을 함께 전달)")
public record GoalRefinementRequest(
    @Schema(description = "목표 (자연어)", example = "토익 850점 이상 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(description = "종료 날짜 (yyyy-MM-dd 형식). 선택", example = "2026-05-01")
        String deadlineDate,
    @Schema(description = "종료 시간 (HH:mm 형식). 선택", example = "23:59")
        String deadlineTime,
    @Schema(description = "탐색에서 받은 질문과 사용자 답변 목록",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "질문/답변은 최소 1개 이상이어야 합니다.")
        @Valid
        List<QuestionAnswer> answers) {}
```

`GoalRefinementResponse.java` (전체 교체):
```java
package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 목표 정제 결과")
public record GoalRefinementResponse(
    @Schema(description = "정제된 목표") RefinedField goal,
    @Schema(description = "정제된 종료일정") RefinedDeadline deadline,
    @Schema(description = "질문별 정제 솔루션 목록") List<RefinedSolution> solutions) {}
```

`RefinedNotes.java` 삭제:
```bash
git rm src/main/java/plana/replan/domain/goal/dto/refine/RefinedNotes.java
```

- [ ] **Step 2: 서비스 정제 로직 교체**

`GoalAiService.java`에서 `import ...RefinedNotes;` 제거, `import ...QuestionAnswer;`·`import ...RefinedSolution;` 추가. `buildRefinePrompt`/`parseRefineResponse`를 아래로 교체(둘 다 package-private로):
```java
String buildRefinePrompt(GoalRefinementRequest req, String today) {
  StringBuilder qa = new StringBuilder();
  for (QuestionAnswer a : req.answers()) {
    qa.append("- ").append(a.question()).append(": ")
      .append(a.answer() != null && !a.answer().isBlank() ? a.answer() : "미입력").append("\n");
  }
  return """
      당신은 목표 달성 플래닝 전문가입니다.
      사용자의 목표와 질문 답변을 분석하고, 투두 리스트 생성에 최적화되도록 정제하세요.

      입력:
      목표: %s
      종료 날짜: %s
      종료 시간: %s
      질문/답변:
      %s

      정제 규칙:
      1. 사용자가 변경 불가한 제약(특정 요일·교재·장소 등)은 반드시 그대로 유지한다.
      2. goal: 막연한 표현을 제거하고 측정 가능한 수치·기준을 포함해 구체화한다.
         (예: "토익 900점" → "토익 900점 달성 (LC 450·RC 450 이상)")
      3. deadline: 입력으로 받은 종료 날짜(date, yyyy-MM-dd)·종료 시간(time, HH:mm)을 그대로 둔다(임의 변경 금지).
         입력이 없으면 null. reason에는 일정 기준 한 줄 평가를 적는다.
      4. solutions: 입력된 '질문/답변' 각각에 대해 정제 결과를 1개씩 만든다(질문 수와 동일).
         - question: 입력 질문을 그대로 또는 자연스러운 라벨로 정리
         - items: 그 질문에 대한 정제 내용을 {title, content} 항목 1~5개로 구조화.
           유저 답변을 그대로 옮기지 말고, 답변 + 목표 달성에 필요한 보강(영역별 평가·전략·루틴 등)을 포함한다.
           title은 항목 소제목(예: "교재 및 컨텐츠"), content는 투두 생성에 바로 쓸 수 있게 구체적으로 서술.
         - reason: 그 질문 정제에 대한 근거 1~2문장
      5. 목표 달성에 교재·강의가 필요하지만 답변에 없으면 Google Search로 실제 존재가 확인되는 것만 items에 추가한다.
         링크를 확인할 수 없으면 포함하지 않는다.
      6. 모든 텍스트는 "~합니다", "~했습니다" 서술형으로 쓰고 "~하세요" 명령형은 금지한다.

      반드시 아래 JSON만 출력하세요 (다른 설명 없이):
      {"goal":{"value":"","reason":""},"deadline":{"date":null,"time":null,"reason":""},"solutions":[{"question":"","items":[{"title":"","content":""}],"reason":""}]}
      """
      .formatted(
          req.goal(),
          req.deadlineDate() != null ? req.deadlineDate() : "미입력",
          req.deadlineTime() != null ? req.deadlineTime() : "미입력",
          qa.toString());
}

GoalRefinementResponse parseRefineResponse(String raw) {
  try {
    String json = extractJson(raw);
    JsonNode root = objectMapper.readTree(json);

    RefinedField goal =
        new RefinedField(
            root.path("goal").path("value").asText(), root.path("goal").path("reason").asText());

    JsonNode dl = root.path("deadline");
    String dlDate = dl.path("date").isNull() ? null : dl.path("date").asText(null);
    String dlTime = dl.path("time").isNull() ? null : dl.path("time").asText(null);
    RefinedDeadline deadline = new RefinedDeadline(dlDate, dlTime, dl.path("reason").asText());

    List<RefinedSolution> solutions = new ArrayList<>();
    for (JsonNode s : root.path("solutions")) {
      List<RefinedNoteItem> items = new ArrayList<>();
      for (JsonNode item : s.path("items")) {
        items.add(new RefinedNoteItem(item.path("title").asText(), item.path("content").asText()));
      }
      solutions.add(new RefinedSolution(s.path("question").asText(), items, s.path("reason").asText()));
    }
    return new GoalRefinementResponse(goal, deadline, solutions);
  } catch (Exception e) {
    log.error("Gemini refine 응답 파싱 실패: {}", raw, e);
    throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
  }
}
```

- [ ] **Step 3: 정제 테스트 작성**

`GoalAiServiceTest.java`에 추가(import: `GoalRefinementRequest`, `GoalRefinementResponse`, `QuestionAnswer`, `java.util.List`):
```java
private GoalRefinementRequest refineReq() {
  return new GoalRefinementRequest(
      "토익 850점", "2026-05-01", "23:59",
      List.of(new QuestionAnswer("현재 영어 실력", "토익 600점대")));
}

@Test
void 정제_프롬프트에_목표와_질문답변이_들어간다() {
  String prompt = service.buildRefinePrompt(refineReq(), "2026-06-20");
  assertThat(prompt).contains("토익 850점");
  assertThat(prompt).contains("현재 영어 실력: 토익 600점대");
}

@Test
void 정제_답변이_비면_미입력으로_들어간다() {
  GoalRefinementRequest req =
      new GoalRefinementRequest("토익 850점", null, null,
          List.of(new QuestionAnswer("특이사항", "")));
  String prompt = service.buildRefinePrompt(req, "2026-06-20");
  assertThat(prompt).contains("특이사항: 미입력");
}

@Test
void 정제_응답을_질문별_솔루션으로_파싱한다() {
  String raw =
      "{\"goal\":{\"value\":\"토익 850점 달성\",\"reason\":\"구체화함\"},"
          + "\"deadline\":{\"date\":\"2026-05-01\",\"time\":\"23:59\",\"reason\":\"유지\"},"
          + "\"solutions\":[{\"question\":\"현재 수준\",\"items\":"
          + "[{\"title\":\"독해\",\"content\":\"실전풀이 필요\"}],\"reason\":\"격차 정리\"}]}";
  GoalRefinementResponse res = service.parseRefineResponse(raw);
  assertThat(res.goal().value()).isEqualTo("토익 850점 달성");
  assertThat(res.deadline().date()).isEqualTo("2026-05-01");
  assertThat(res.solutions()).hasSize(1);
  assertThat(res.solutions().get(0).question()).isEqualTo("현재 수준");
  assertThat(res.solutions().get(0).items().get(0).title()).isEqualTo("독해");
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "plana.replan.domain.goal.service.GoalAiServiceTest"`
Expected: 정제 3개 + 기존 테스트 PASS.

- [ ] **Step 5: 커밋**
```bash
git add -A src/main/java/plana/replan/domain/goal/dto/refine src/main/java/plana/replan/domain/goal/service/GoalAiService.java src/test/java/plana/replan/domain/goal/service/GoalAiServiceTest.java
git commit -m "Refactor: 목표 정제를 동적 질문/답변 기반 솔루션 구조로 변경"
```

---

## Task 4: 목표 정제 — Swagger 문서 갱신

**Files:**
- Modify: `src/main/java/plana/replan/domain/goal/controller/GoalControllerDocs.java`

**Interfaces:**
- Consumes: 변경된 `GoalRefinementRequest`/`GoalRefinementResponse` (Task 3). 컨트롤러 메서드 시그니처는 그대로(엔드포인트·반환 타입 동일).

- [ ] **Step 1: refineGoal 문서 갱신 (swagger-docs 스킬 사용)**

`swagger-docs` 스킬을 호출해서 `GoalControllerDocs.refineGoal` 문서를 새 입출력에 맞게 고친다:
- Request Body 표를 goal(✅) / deadlineDate(❌) / deadlineTime(❌) / answers(✅, array) 로 교체. answers 항목 구조(question/answer) 설명.
- `@io.swagger.v3.oas.annotations.parameters.RequestBody` 예시를 새 구조로 교체(전체 / 종료일정 생략 2가지).
- 응답 예시(200)를 goal/deadline/solutions 구조로 교체.
- `@ApiResponses` 에러: 400(goal 누락·answers 비어있음), 401(2케이스), 502(GEMINI_API_ERROR·GEMINI_PARSE_ERROR) 유지.

- [ ] **Step 2: 컴파일·전체 테스트 확인**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**
```bash
git add src/main/java/plana/replan/domain/goal/controller/GoalControllerDocs.java
git commit -m "Docs: 목표 정제 API 문서를 동적 질문/솔루션 구조로 갱신"
```

---

## Task 5: 투두 추천 — 솔루션 목록 입력으로 재구성

**Files:**
- Create: `src/main/java/plana/replan/domain/goal/dto/recommend/SolutionInput.java`
- Modify: `src/main/java/plana/replan/domain/goal/dto/recommend/TodoRecommendationRequest.java`
- Modify: `src/main/java/plana/replan/domain/goal/service/GoalAiService.java`
- Test: `src/test/java/plana/replan/domain/goal/service/GoalAiServiceTest.java`

**Interfaces:**
- Consumes: `RefinedNoteItem` (항목 title/content 재사용)
- Produces:
  - `record SolutionInput(String question, List<RefinedNoteItem> items)`
  - `record TodoRecommendationRequest(String goal, String deadlineDate, String deadlineTime, List<SolutionInput> solutions, Integer refreshCount)`
  - `buildRecommendPrompt`는 솔루션을 텍스트로 직렬화해서 프롬프트에 넣음(시그니처 동일, 내부만 변경)

- [ ] **Step 1: SolutionInput + 요청 DTO 재구성**

`SolutionInput.java`:
```java
package plana.replan.domain.goal.dto.recommend;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import plana.replan.domain.goal.dto.refine.RefinedNoteItem;

@Schema(description = "정제·수정이 끝난 최종 솔루션 1개 (질문 + 항목 목록)")
public record SolutionInput(
    @Schema(description = "질문 라벨", example = "현재 수준") String question,
    @Schema(description = "항목 목록 (제목 + 내용)") List<RefinedNoteItem> items) {}
```

`TodoRecommendationRequest.java` (전체 교체):
```java
package plana.replan.domain.goal.dto.recommend;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "AI 투두 추천 요청 (정제된 솔루션을 전달)")
public record TodoRecommendationRequest(
    @Schema(description = "목표", example = "토익 850점 달성 (LC 450·RC 450 이상)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(description = "마감 날짜 (yyyy-MM-dd 형식). 선택", example = "2026-05-01")
        String deadlineDate,
    @Schema(description = "마감 시간 (HH:mm 형식). 선택", example = "23:59")
        String deadlineTime,
    @Schema(description = "정제·수정이 끝난 솔루션 목록. 선택")
        List<SolutionInput> solutions,
    @Schema(description = "새로고침 횟수(0~3). 0 또는 생략은 첫 추천, 1~3은 다시 추천", example = "0")
        Integer refreshCount) {}
```

- [ ] **Step 2: 추천 프롬프트의 입력 직렬화 교체**

`GoalAiService.java`에서 `import ...SolutionInput;`·`import ...RefinedNoteItem;` 추가. `buildRecommendPrompt`의 입력 부분만 교체한다. 기존에 `현재수준/투자가능시간/특이사항` 3줄로 넣던 것을 솔루션 직렬화 한 블록으로 바꾼다. 헬퍼 추가:
```java
private String buildSolutionInfo(List<SolutionInput> solutions) {
  if (solutions == null || solutions.isEmpty()) return "미입력";
  StringBuilder sb = new StringBuilder();
  for (SolutionInput s : solutions) {
    sb.append("[").append(s.question()).append("]\n");
    if (s.items() != null) {
      for (RefinedNoteItem item : s.items()) {
        sb.append("- ").append(item.title()).append(": ").append(item.content()).append("\n");
      }
    }
  }
  return sb.toString();
}
```
그리고 `buildRecommendPrompt` 안에서 프롬프트 입력부의 `현재수준/투자가능시간/특이사항:` 세 줄을 다음 한 줄로 교체:
```java
        솔루션:
        %s
```
`.formatted(...)` 인자도 `req.goal()`, `deadlineInfo`, `buildSolutionInfo(req.solutions())` 순서로 맞춘다(기존 currentLevel/availableTime/notes 인자 3개 → 솔루션 1개). 본문의 "notes에 ..." 문구는 "솔루션에 ..."로 자연스럽게 바꾼다. 나머지 투두 생성 규칙·JSON 포맷·`refreshStyleBlock`은 그대로 둔다.

- [ ] **Step 3: 기존 추천 테스트를 새 요청 구조로 수정**

`GoalAiServiceTest.java`의 `req(...)` 헬퍼와 import를 새 DTO에 맞게 수정:
```java
private TodoRecommendationRequest req(Integer refreshCount) {
  return new TodoRecommendationRequest(
      "토익 900점 달성", "2026-08-25", "08:00",
      List.of(new SolutionInput("현재 수준",
          List.of(new RefinedNoteItem("실력", "토익 600점")))),
      refreshCount);
}
```
import 추가: `plana.replan.domain.goal.dto.recommend.SolutionInput`, `plana.replan.domain.goal.dto.refine.RefinedNoteItem`. 그리고 추천 프롬프트에 솔루션이 들어가는지 확인하는 테스트 추가:
```java
@Test
void 추천_프롬프트에_솔루션이_들어간다() {
  String prompt = service.buildRecommendPrompt(req(0));
  assertThat(prompt).contains("[현재 수준]");
  assertThat(prompt).contains("실력: 토익 600점");
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "plana.replan.domain.goal.service.GoalAiServiceTest"`
Expected: 기존 새로고침 테스트(수정됨) + 신규 솔루션 테스트 PASS.

- [ ] **Step 5: 커밋**
```bash
git add -A src/main/java/plana/replan/domain/goal/dto/recommend src/main/java/plana/replan/domain/goal/service/GoalAiService.java src/test/java/plana/replan/domain/goal/service/GoalAiServiceTest.java
git commit -m "Refactor: 투두 추천 입력을 정제 솔루션 목록 기반으로 변경"
```

---

## Task 6: 투두 추천 — Swagger 문서 갱신

**Files:**
- Modify: `src/main/java/plana/replan/domain/goal/controller/GoalControllerDocs.java`

- [ ] **Step 1: recommendTodos 문서 갱신 (swagger-docs 스킬 사용)**

`swagger-docs` 스킬을 호출해서 `GoalControllerDocs.recommendTodos` 문서를 새 입력에 맞게 고친다:
- Request Body 표를 goal(✅) / deadlineDate(❌) / deadlineTime(❌) / solutions(❌, array) / refreshCount(❌) 로 교체. solutions 항목 구조(question + items[title/content]) 설명.
- RequestBody 예시를 새 구조로 교체(전체 / 솔루션·종료일정 생략).
- 응답·에러(@ApiResponses)는 기존 유지: 200(overallReason/todos), 400(INVALID_REFRESH_COUNT·goal 누락), 401(2케이스), 502(GEMINI_API_ERROR·GEMINI_PARSE_ERROR).

- [ ] **Step 2: 컴파일·전체 테스트 확인**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**
```bash
git add src/main/java/plana/replan/domain/goal/controller/GoalControllerDocs.java
git commit -m "Docs: 투두 추천 API 문서를 솔루션 목록 입력 구조로 갱신"
```

---

## Task 7: 로컬 서버 전수 API 테스트 (CLAUDE.md 프로세스 2단계)

**Files:** (코드 변경 없음 — 실제 서버 띄워 확인)

- [ ] **Step 1: 로컬 환경 기동**
```bash
open -a Docker            # 꺼져 있으면
docker compose up -d      # postgres 5432, redis 6379
./gradlew bootRun --args='--spring.profiles.active=local'
curl http://localhost:8080/actuator/health
```

- [ ] **Step 2: 로그인해서 토큰 발급**
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"claude-test@replan.local","password":"claude-test-1234"}'
# 응답 data.accessToken 을 TOKEN 변수에 저장
```

- [ ] **Step 3: 목표 탐색 — 정상 목표**

`POST /api/goals/ai/explore` 에 `{"goal":"토익 850점 이상 달성","deadlineDate":"2026-05-01","deadlineTime":"23:59"}` 전송.
Expected: `valid=true`, `questions` 3개, 각 질문에 chips 존재.

- [ ] **Step 4: 목표 탐색 — 이상한 입력**

같은 엔드포인트에 `{"goal":"ㅁㄴㅇㄹ 안녕 점심 뭐먹지"}` 전송.
Expected: HTTP 200, `valid=false`, `message`에 안내 문구, `questions` 빈 배열.

- [ ] **Step 5: 목표 정제**

`POST /api/goals/ai/refine` 에 탐색에서 받은 질문 + 임의 답변을 `answers`로, goal·deadlineDate·deadlineTime과 함께 전송.
Expected: `goal`·`deadline`·`solutions`(질문 수만큼) 반환, 각 solution에 items 존재.

- [ ] **Step 6: 투두 추천 (첫 추천 + 새로고침)**

`POST /api/goals/ai/todo-recommendations` 에 정제 결과를 `solutions`로 옮겨 `refreshCount:0`으로 전송 → 투두 목록 확인. 이어서 `refreshCount:2`로 다시 호출 → 스타일이 달라지는지 확인. `refreshCount:4`로 호출 → 400 INVALID_REFRESH_COUNT 확인.

- [ ] **Step 7: 결과 기록**

각 단계 요청/응답을 PR 본문 "테스트 결과"에 쓸 수 있게 정리(성공/실패 케이스 모두).

---

## 이후 절차 (CLAUDE.md 작업 프로세스)

코드·로컬 테스트가 끝나면: **PR 생성**(README PR 템플릿·태그 규칙 준수, `close #178`) → **CodeRabbit 리뷰 반영** → **main 머지** → **배포 환경 전수 테스트**. 버전은 직전 작업의 새 기능이므로 마이너 올림(`/schedule` 아님) 후보 — 배포 PR 때 최신 태그 확인해 결정.
