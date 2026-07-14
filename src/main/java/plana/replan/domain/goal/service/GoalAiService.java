package plana.replan.domain.goal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import plana.replan.domain.goal.dto.explore.ExploreQuestion;
import plana.replan.domain.goal.dto.explore.GoalExploreRequest;
import plana.replan.domain.goal.dto.explore.GoalExploreResponse;
import plana.replan.domain.goal.dto.recommend.RecommendedTodo;
import plana.replan.domain.goal.dto.recommend.SolutionInput;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationRequest;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationResponse;
import plana.replan.domain.goal.dto.refine.GoalRefinementRequest;
import plana.replan.domain.goal.dto.refine.GoalRefinementResponse;
import plana.replan.domain.goal.dto.refine.QuestionAnswer;
import plana.replan.domain.goal.dto.refine.RefinedDeadline;
import plana.replan.domain.goal.dto.refine.RefinedField;
import plana.replan.domain.goal.dto.refine.RefinedNoteItem;
import plana.replan.domain.goal.dto.refine.RefinedSolution;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.global.exception.CustomException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalAiService {

  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

  private static final String DEADLINE_PASSED_MESSAGE = "종료 일정이 이미 지났어요. 미래 날짜로 다시 설정해주세요.";

  /** 프롬프트에 넣는 현재 일시 형식(예: 2026-07-14 23:05 (화요일)). 요일까지 줘야 반복 요일 배치에 쓸 수 있다. */
  static final DateTimeFormatter PROMPT_NOW_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)", Locale.KOREAN);

  // 응답 길이 상한. 3세대 모델은 답하기 전 '생각(thinking)'에도 이 예산을 함께 쓴다.
  // 이 값이 부족하면 생각이 예산을 다 먹고 실제 답이 중간에 잘려(MAX_TOKENS) 반쪽 JSON이 온다.
  private static final int GEMINI_MAX_OUTPUT_TOKENS = 8192;

  // 각 API 응답을 항상 완결된 JSON으로 받도록 강제하기 위한 응답 구조(스키마).
  // 검색(google_search)과 함께 써도 3세대 모델에서는 동작한다.
  private static final String EXPLORE_SCHEMA =
      """
      {
        "type": "OBJECT",
        "properties": {
          "valid": { "type": "BOOLEAN" },
          "message": { "type": "STRING", "nullable": true },
          "questions": {
            "type": "ARRAY",
            "items": {
              "type": "OBJECT",
              "properties": {
                "question": { "type": "STRING" },
                "chips": { "type": "ARRAY", "items": { "type": "STRING" } }
              },
              "propertyOrdering": ["question", "chips"]
            }
          }
        },
        "propertyOrdering": ["valid", "message", "questions"]
      }
      """;

  private static final String REFINE_SCHEMA =
      """
      {
        "type": "OBJECT",
        "properties": {
          "goal": {
            "type": "OBJECT",
            "properties": { "value": { "type": "STRING" }, "reason": { "type": "STRING" } },
            "propertyOrdering": ["value", "reason"]
          },
          "deadline": {
            "type": "OBJECT",
            "properties": {
              "date": { "type": "STRING", "nullable": true },
              "time": { "type": "STRING", "nullable": true },
              "reason": { "type": "STRING" }
            },
            "propertyOrdering": ["date", "time", "reason"]
          },
          "solutions": {
            "type": "ARRAY",
            "items": {
              "type": "OBJECT",
              "properties": {
                "question": { "type": "STRING" },
                "items": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "title": { "type": "STRING" },
                      "content": { "type": "STRING" }
                    },
                    "propertyOrdering": ["title", "content"]
                  }
                },
                "reason": { "type": "STRING" }
              },
              "propertyOrdering": ["question", "items", "reason"]
            }
          }
        },
        "propertyOrdering": ["goal", "deadline", "solutions"]
      }
      """;

  private static final String RECOMMEND_SCHEMA =
      """
      {
        "type": "OBJECT",
        "properties": {
          "overallReason": { "type": "STRING" },
          "todos": {
            "type": "ARRAY",
            "items": {
              "type": "OBJECT",
              "properties": {
                "type": { "type": "STRING" },
                "title": { "type": "STRING" },
                "dueDate": { "type": "STRING", "nullable": true },
                "dueTime": { "type": "STRING", "nullable": true },
                "routineType": { "type": "STRING", "nullable": true },
                "routineDays": { "type": "ARRAY", "nullable": true, "items": { "type": "INTEGER" } },
                "routineTime": { "type": "STRING", "nullable": true },
                "tagId": { "type": "INTEGER", "nullable": true }
              },
              "propertyOrdering": ["type", "title", "dueDate", "dueTime", "routineType", "routineDays", "routineTime", "tagId"]
            }
          }
        },
        "propertyOrdering": ["overallReason", "todos"]
      }
      """;

  private final RestClient geminiRestClient;
  private final TagRepository tagRepository;
  private final Clock clock;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${gemini.api-key}")
  private String apiKey;

  public GoalExploreResponse exploreGoal(GoalExploreRequest request) {
    if (isDeadlinePassed(request.deadlineDate(), request.deadlineTime())) {
      return new GoalExploreResponse(false, DEADLINE_PASSED_MESSAGE, List.of());
    }
    String today = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE);
    String prompt = buildExplorePrompt(request, today);
    String raw = callGemini(prompt, schema(EXPLORE_SCHEMA));
    return parseExploreResponse(raw);
  }

  /**
   * 종료 일정이 이미 지났는지 판단한다. 날짜 비교를 AI에 맡기면 결과가 오락가락하므로 반드시 코드로 검사한다. 시간이 없으면 그날 하루가 끝날 때까지를 기한으로 보고,
   * 형식이 잘못된 값은 기존처럼 AI 판단에 맡긴다.
   */
  boolean isDeadlinePassed(String deadlineDate, String deadlineTime) {
    if (deadlineDate == null) {
      return false;
    }
    try {
      LocalDate date = LocalDate.parse(deadlineDate);
      LocalTime time = deadlineTime != null ? LocalTime.parse(deadlineTime) : LocalTime.MAX;
      return LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock));
    } catch (DateTimeParseException e) {
      return false;
    }
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
        입력이 '달성할 수 있는 실제 목표'인지 판단한다. (종료 일정이 지났는지는 서버 코드가 이미 검사했으므로 판단하지 않는다.)
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

  public GoalRefinementResponse refineGoal(GoalRefinementRequest request) {
    String today = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE);
    String prompt = buildRefinePrompt(request, today);
    String raw = callGemini(prompt, schema(REFINE_SCHEMA));
    return parseRefineResponse(raw);
  }

  String buildRefinePrompt(GoalRefinementRequest req, String today) {
    StringBuilder qa = new StringBuilder();
    for (QuestionAnswer a : req.answers()) {
      qa.append("- ")
          .append(a.question())
          .append(": ")
          .append(a.answer() != null && !a.answer().isBlank() ? a.answer() : "미입력")
          .append("\n");
    }
    return """
        당신은 목표 달성 플래닝 전문가입니다.
        사용자의 목표와 질문 답변을 분석하고, 투두 리스트 생성에 최적화되도록 정제하세요.

        입력:
        목표: %s
        종료 날짜: %s
        종료 시간: %s
        오늘 날짜: %s
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
            today,
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
          items.add(
              new RefinedNoteItem(item.path("title").asText(), item.path("content").asText()));
        }
        solutions.add(
            new RefinedSolution(s.path("question").asText(), items, s.path("reason").asText()));
      }
      return new GoalRefinementResponse(goal, deadline, solutions);
    } catch (Exception e) {
      log.error("Gemini refine 응답 파싱 실패: {}", raw, e);
      throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
    }
  }

  public TodoRecommendationResponse recommendTodos(Long userId, TodoRecommendationRequest request) {
    validateRefreshCount(request.refreshCount());
    List<Tag> tags = tagRepository.findAllByUserId(userId);
    String now = LocalDateTime.now(clock).format(PROMPT_NOW_FORMAT);
    String prompt = buildRecommendPrompt(request, buildTagInfo(tags), now);
    String raw = callGemini(prompt, schema(RECOMMEND_SCHEMA));
    return parseRecommendResponse(raw, tags);
  }

  /** 유저 태그 목록을 프롬프트용 문자열로 만든다. 태그가 없으면 "없음". */
  String buildTagInfo(List<Tag> tags) {
    if (tags == null || tags.isEmpty()) {
      return "없음";
    }
    StringBuilder sb = new StringBuilder();
    for (Tag tag : tags) {
      sb.append("- id=").append(tag.getId()).append(", name=").append(tag.getTitle()).append("\n");
    }
    return sb.toString();
  }

  /** 새로고침 횟수를 0~3 범위로 검증한다. null은 허용(첫 추천으로 취급). */
  private void validateRefreshCount(Integer refreshCount) {
    if (refreshCount != null && (refreshCount < 0 || refreshCount > 3)) {
      throw new CustomException(GoalErrorCode.INVALID_REFRESH_COUNT);
    }
  }

  String buildRecommendPrompt(TodoRecommendationRequest req, String tagInfo, String now) {
    String deadlineInfo = buildDeadlineInfo(req.deadlineDate(), req.deadlineTime());
    String prompt =
        """
        당신은 목표 달성 플래닝 전문가입니다.

        목표: %s
        마감기한: %s
        현재 일시: %s
        솔루션:
        %s

        [사용 가능한 태그 목록]
        %s
        각 투두마다 위 목록에서 가장 적절한 태그 하나를 골라 그 태그의 id를 tagId에 넣으세요.
        - tagId는 반드시 위 목록에 있는 id 중 하나여야 합니다. 목록에 없는 id를 지어내지 마세요.
        - 마땅한 태그가 없거나 목록이 "없음"이면 tagId는 null로 두세요.

        [1단계 — 교재·강의 정보 수집, 투두 생성 전 반드시 먼저 수행]
        솔루션에 교재·강의명이 하나라도 있으면:
        - 솔루션에 언급된 교재·강의를 빠짐없이 목록화한다
        - 강의인 경우 플랫폼(인프런, freeCodeCamp, 해커스, 패스트캠퍼스, Udemy 등)을 파악한다. 플랫폼이 명시되지 않았으면 Google Search로 해당 강의가 어느 플랫폼에 있는지 먼저 검색한다
        - 유튜브 강의는 목차 검색 대상에서 제외한다. 유튜브 강의는 강수·목차 없이 주제 단위로 투두를 생성한다
        - 유튜브 외 플랫폼 강의와 교재는 Google Search로 아래 정보를 검색한다:
          · 인터넷 강의(유튜브 제외): 플랫폼명 + 강의명으로 검색하여 총 강수, 각 강의 제목(목차 전체) 확인
          · 책·교재: 총 챕터 수, 총 페이지 수, 챕터별 제목
          · 단어장·VOCA: 총 Day/Unit 수, Day당 단어 수
          · 문제집: 총 파트 수, 총 회차 수
        - 검색으로 목차·분량을 확인한 경우에만 강수·챕터 단위로 세분화한다
        - 검색으로 확인되지 않으면 투두 제목에 "(목차 미확인)"을 명시하고 주제 단위로 생성한다. 절대 임의로 강수나 페이지를 채워넣지 않는다

        [2단계 — 투두 생성 규칙]
        1. 솔루션에 언급된 모든 교재·강의를 빠짐없이 커버한다 (일부만 반영 금지)
        2. 총 분량 ÷ 남은 기간 ÷ 하루 투자시간으로 1회 투두 분량을 계산하여 배분한다 (남은 기간은 '현재 일시'부터 마감기한까지로 계산)
        3. 투두 제목에 검색으로 확인된 실제 강의 제목 또는 챕터명을 포함한다
           예) "인프런 - 한입 리액트 13강 컴포넌트 만들기 수강" (검색으로 확인된 실제 제목)
        4. 1회 투두 분량은 하루 투자시간의 50%%를 초과하지 않는다
        5. 교재·강의가 없으면 솔루션에 제공된 정보만으로 투두 생성 (새 교재·강의 검색·추천 금지)
        6. RECURRING 투두는 매번 내용이 동일한 작업만 해당 (예: 매일 단어 암기, 매일 운동)
        7. 인강 수강처럼 매번 다른 내용을 학습하는 것은 ONE_TIME 투두 여러 개로 생성
        8. 이론 나열 금지. '수기 복습', '문제 풀이', '오답 노트' 등 아웃풋 태스크 반드시 중간에 배치
        9. 마감 D-1~2는 진도 금지. '최종 점검', '백지 복습' 등 마무리 태스크만 배치
        10. WEEKLY routineDays는 요일 인덱스 배열: 월=0, 화=1, 수=2, 목=3, 금=4, 토=5, 일=6 (예: 월·수·금 → [0,2,4])
        11. MONTHLY routineDays는 일자 배열(1~31): 그 달 며칠에 반복할지 (예: 3일·20일 → [3,20])
        12. DAILY는 routineDays 불필요 (null)
        13. dueDate는 yyyy-MM-dd 형식 또는 null, dueTime은 HH:mm 형식 또는 null, routineTime은 HH:mm 형식 또는 null
        14. type은 "ONE_TIME" 또는 "RECURRING"만 허용
        15. routineType은 "DAILY", "WEEKLY", "MONTHLY" 중 하나 (ONE_TIME이면 null)
        15-1. ONE_TIME: dueDate/dueTime은 그 투두의 마감 일시, routineTime은 null
        15-2. RECURRING: routineTime은 매 회차를 수행할 시각(반복시간), dueDate/dueTime은 반복이 끝나는
              종료 일정(기본값: 목표 마감일, 목표 마감이 없으면 null). 수행 시각을 dueTime에 넣지 않는다
        15-3. 모든 마감은 반드시 '현재 일시' 이후여야 한다. ONE_TIME의 dueDate/dueTime도, RECURRING의 종료 일정도
              이미 지난 날짜·시각으로 절대 만들지 않는다. dueDate가 오늘이면 dueTime은 현재 시각 이후여야 한다
        16. overallReason: 이 추천 전체에 대한 총평을 서술체("~합니다", "~했습니다")로 작성. 조언·명령형("~하세요") 절대 금지.
            - 어떤 기준으로 투두를 구성했는지, 핵심 전략이 무엇인지 서술
            - 솔루션에 교재·강의가 포함된 경우 각 항목마다 아래 형식으로 출처 정보를 포함:
              · 책: "교재명 (저자: OOO / 출판사: OOO / 링크: https://...)"
              · 강의: "강의명 (강사: OOO / 플랫폼: OOO / 링크: https://...)"
            - 링크는 Google Search로 확인된 실제 URL만 사용. 확인 불가 시 링크 생략

        반드시 아래 JSON만 출력하세요 (다른 설명 없이):
        {"overallReason":"","todos":[{"type":"","title":"","dueDate":null,"dueTime":null,"routineType":null,"routineDays":null,"routineTime":null,"tagId":null}]}
        """
            .formatted(req.goal(), deadlineInfo, now, buildSolutionInfo(req.solutions()), tagInfo);
    return prompt + refreshStyleBlock(req.refreshCount() == null ? 0 : req.refreshCount());
  }

  /** 새로고침 회차(1~3)에 맞는 스타일 안내 블록. 0/그 외는 빈 문자열(0회차는 프롬프트 변경 없음). */
  static String refreshStyleBlock(int refreshCount) {
    String line =
        switch (refreshCount) {
          case 1 -> "1회차(여유): 마감에 5~6일 버퍼를 넉넉히 둔다. 할 일은 아주 잘게(마이크로) 쪼개고, 쉬운 것부터 정순으로 배치한다.";
          case 2 -> "2회차(벼락치기): 버퍼 없이 혹은 마이너스로 빡빡하게 잡는다. 가장 핵심 1개(1-Pick) 위주로 줄이고, 어려운 것부터 역순으로 배치한다.";
          case 3 -> "3회차(환경 변경): 분량·난이도는 적정 수준으로 두되, 기존 진행 시간대·요일을 실제로 다른 쪽으로 옮겨 배치한다."
              + " 예: 평일 저녁 → 주말 오전, 평일 → 주말. 각 투두의 수행 시각(ONE_TIME은 dueTime,"
              + " RECURRING은 routineTime)을 기존과 다른 시간대로 바꾸거나"
              + " routineType/routineDays를 주말(토·일) 쪽으로 옮긴다.";
          default -> null;
        };
    if (line == null) {
      return "";
    }
    return "\n\n[이번 새로고침 스타일]\n"
        + line
        + "\n위 스타일을 우선으로 적용하되, 결과는 위 공통 규칙(투두 형식·JSON 포맷)을 그대로 따른다.";
  }

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

  private String buildDeadlineInfo(String deadlineDate, String deadlineTime) {
    if (deadlineDate == null && deadlineTime == null) return "미입력";
    if (deadlineDate != null && deadlineTime != null) return deadlineDate + " " + deadlineTime;
    if (deadlineDate != null) return deadlineDate;
    return deadlineTime;
  }

  TodoRecommendationResponse parseRecommendResponse(String raw, List<Tag> tags) {
    // 유저의 실제 태그만 담은 (id -> 이름) 맵. AI가 준 tagId 검증·태그명 확정에 사용한다.
    Map<Long, String> validTags = new LinkedHashMap<>();
    if (tags != null) {
      for (Tag tag : tags) {
        validTags.put(tag.getId(), tag.getTitle());
      }
    }
    try {
      String json = extractJson(raw);
      JsonNode root = objectMapper.readTree(json);
      String overallReason = root.path("overallReason").asText(null);
      JsonNode todosNode = root.path("todos");

      List<RecommendedTodo> todos = new ArrayList<>();
      for (JsonNode node : todosNode) {
        String type = node.path("type").asText();
        String title = node.path("title").asText();
        String dueDate = node.path("dueDate").isNull() ? null : node.path("dueDate").asText(null);
        String dueTime = node.path("dueTime").isNull() ? null : node.path("dueTime").asText(null);
        String routineType =
            node.path("routineType").isNull() ? null : node.path("routineType").asText(null);
        // routineTime: 매 회차 수행 시각(HH:mm). RECURRING 전용 — 그 외에는 null로 강제.
        String routineTime =
            node.path("routineTime").isNull() ? null : node.path("routineTime").asText(null);
        if (!"RECURRING".equals(type)) {
          routineTime = null;
        }
        // routineDays: 반복 날짜 배열 (WEEKLY=요일 인덱스 0~6, MONTHLY=일자 1~31). 없으면 null.
        List<Integer> routineDays = null;
        JsonNode routineDaysNode = node.path("routineDays");
        if (routineDaysNode.isArray()) {
          routineDays = new ArrayList<>();
          for (JsonNode e : routineDaysNode) {
            if (e.canConvertToInt()) {
              routineDays.add(e.asInt());
            } else if (e.isTextual()) {
              // 숫자로 못 바꾸면 NumberFormatException → 아래 catch(Exception)에서 파싱 오류로 처리
              routineDays.add(Integer.valueOf(e.asText().trim()));
            } else {
              // 숫자/숫자문자열이 아닌 원소는 잘못된 응답으로 본다.
              throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
            }
          }
        }
        // ONE_TIME·DAILY는 반복 날짜가 없어야 하고, WEEKLY/MONTHLY는 범위 안의 값이 하나 이상 있어야 한다.
        // 부분 파싱한 값을 그대로 흘려보내지 않고 여기서 전체를 검증한다.
        if (!"RECURRING".equals(type) || routineType == null || "DAILY".equals(routineType)) {
          routineDays = null;
        } else if (!isValidRecommendedRoutineDays(routineType, routineDays)) {
          throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
        }
        // AI가 준 tagId를 유저의 실제 태그 목록으로 검증한다.
        // 실제 태그면 그대로 두고 이름은 DB 기준으로 확정, 아니면(지어낸 값 등) 태그 없이(null) 처리.
        Long tagId = null;
        String tagName = null;
        JsonNode tagIdNode = node.path("tagId");
        if (!tagIdNode.isNull() && !tagIdNode.isMissingNode()) {
          // 숫자(1)뿐 아니라 문자열("1")로 온 경우도 받아들인다. LLM이 숫자를 따옴표로 감싸 주는 일이 흔하다.
          Long candidate = null;
          if (tagIdNode.canConvertToLong()) {
            candidate = tagIdNode.asLong();
          } else if (tagIdNode.isTextual()) {
            try {
              candidate = Long.valueOf(tagIdNode.asText().trim());
            } catch (NumberFormatException ignored) {
              candidate = null;
            }
          }
          if (candidate != null && validTags.containsKey(candidate)) {
            tagId = candidate;
            tagName = validTags.get(candidate);
          }
        }

        todos.add(
            clampPastDue(
                new RecommendedTodo(
                    type,
                    title,
                    dueDate,
                    dueTime,
                    routineType,
                    routineDays,
                    routineTime,
                    tagId,
                    tagName)));
      }
      return new TodoRecommendationResponse(overallReason, todos);
    } catch (Exception e) {
      log.error("Gemini recommend 응답 파싱 실패: {}", raw, e);
      throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
    }
  }

  /**
   * AI가 이미 지난 마감 일시를 주면 코드로 보정한다. 날짜 비교를 AI에만 맡기면 결과가 오락가락하므로 프롬프트 규칙과 별개로 반드시 검사한다.
   *
   * <p>ONE_TIME: 지난 날짜는 오늘로 올리고, 시각까지 지났으면 시각을 비워 그날 끝까지로 취급한다. RECURRING: dueDate/dueTime은 반복 종료
   * 일정이므로 지났으면 둘 다 비워 무기한으로 취급한다(수행 시각 routineTime은 건드리지 않는다). 형식이 잘못된 값은 여기서 판단하지 않고 그대로 둔다.
   */
  RecommendedTodo clampPastDue(RecommendedTodo todo) {
    if (todo.dueDate() == null) {
      return todo;
    }
    try {
      LocalDateTime now = LocalDateTime.now(clock);
      LocalDate date = LocalDate.parse(todo.dueDate());
      LocalTime time = todo.dueTime() != null ? LocalTime.parse(todo.dueTime()) : LocalTime.MAX;
      if (!LocalDateTime.of(date, time).isBefore(now)) {
        return todo;
      }
      String dueDate;
      String dueTime;
      if ("RECURRING".equals(todo.type())) {
        dueDate = null;
        dueTime = null;
      } else {
        dueDate = now.toLocalDate().toString();
        dueTime = todo.dueTime() != null && time.isAfter(now.toLocalTime()) ? todo.dueTime() : null;
      }
      return new RecommendedTodo(
          todo.type(),
          todo.title(),
          dueDate,
          dueTime,
          todo.routineType(),
          todo.routineDays(),
          todo.routineTime(),
          todo.tagId(),
          todo.tagName());
    } catch (DateTimeParseException e) {
      return todo;
    }
  }

  /** WEEKLY(요일 0~6)/MONTHLY(일자 1~31) 반복 날짜 배열이 비어있지 않고 범위 안인지 검사한다. */
  private boolean isValidRecommendedRoutineDays(String routineType, List<Integer> routineDays) {
    if (routineDays == null || routineDays.isEmpty()) {
      return false;
    }
    return switch (routineType) {
      case "WEEKLY" -> routineDays.stream().allMatch(d -> d >= 0 && d <= 6);
      case "MONTHLY" -> routineDays.stream().allMatch(d -> d >= 1 && d <= 31);
      default -> false;
    };
  }

  String callGemini(String prompt) {
    return callGemini(prompt, null);
  }

  /**
   * Gemini에 요청을 보내고 응답 본문 텍스트를 돌려준다. responseSchema를 주면 그 구조의 완결된 JSON만 받도록 강제한다. 응답이 길이 제한에 걸려
   * 잘리면(finishReason=MAX_TOKENS) 반쪽 JSON이므로 명확히 로그를 남기고 파싱 오류로 처리한다.
   */
  String callGemini(String prompt, Map<String, Object> responseSchema) {
    Map<String, Object> body = buildGeminiRequestBody(prompt, responseSchema);

    try {
      String response =
          geminiRestClient
              .post()
              .uri(GEMINI_URL + "?key=" + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);

      JsonNode root = objectMapper.readTree(response);
      JsonNode candidate = root.path("candidates").path(0);
      String text = candidate.path("content").path("parts").path(0).path("text").asText();
      if ("MAX_TOKENS".equals(candidate.path("finishReason").asText(""))) {
        log.error("Gemini 응답이 길이 제한에 걸려 잘렸습니다(finishReason=MAX_TOKENS). 받은 일부 응답: {}", text);
        throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
      }
      return text;
    } catch (CustomException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Gemini API 호출 실패", e);
      throw new CustomException(GoalErrorCode.GEMINI_API_ERROR);
    } catch (Exception e) {
      log.error("Gemini 응답 처리 실패", e);
      throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
    }
  }

  /** Gemini 요청 본문을 만든다. 출력 길이 상한과 '생각' 제한을 항상 넣어 응답 잘림을 막고, 응답 스키마가 있으면 JSON 강제출력 설정을 추가한다. */
  Map<String, Object> buildGeminiRequestBody(String prompt, Map<String, Object> responseSchema) {
    Map<String, Object> generationConfig = new LinkedHashMap<>();
    generationConfig.put("maxOutputTokens", GEMINI_MAX_OUTPUT_TOKENS);
    // '생각' 분량을 낮게 제한해 출력 예산 대부분을 실제 답에 쓰게 한다(과한 생각으로 답이 잘리는 것 방지).
    generationConfig.put("thinkingConfig", Map.of("thinkingLevel", "low"));
    if (responseSchema != null) {
      generationConfig.put("responseMimeType", "application/json");
      generationConfig.put("responseSchema", responseSchema);
    }

    return Map.of(
        "tools", new Object[] {Map.of("google_search", Map.of())},
        "contents", new Object[] {Map.of("parts", new Object[] {Map.of("text", prompt)})},
        "generationConfig", generationConfig);
  }

  /**
   * 응답 스키마 JSON 문자열을 Gemini 요청 본문에 넣을 Map으로 변환한다. JsonNode로 넘기면 HTTP 클라이언트가 JsonNode의 게터 (isArray
   * 등)를 필드로 직렬화해 Gemini가 거부하므로, 반드시 Map으로 변환해 넘긴다.
   */
  private Map<String, Object> schema(String schemaJson) {
    try {
      return objectMapper.readValue(schemaJson, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("잘못된 응답 스키마 정의", e);
    }
  }

  private String extractJson(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start == -1 || end == -1 || end < start) {
      throw new IllegalArgumentException("JSON 블록을 찾을 수 없습니다: " + text);
    }
    return text.substring(start, end + 1);
  }
}
