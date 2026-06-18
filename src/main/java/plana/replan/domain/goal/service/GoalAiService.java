package plana.replan.domain.goal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import plana.replan.domain.goal.dto.recommend.RecommendedTodo;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationRequest;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationResponse;
import plana.replan.domain.goal.dto.refine.GoalRefinementRequest;
import plana.replan.domain.goal.dto.refine.GoalRefinementResponse;
import plana.replan.domain.goal.dto.refine.RefinedDeadline;
import plana.replan.domain.goal.dto.refine.RefinedField;
import plana.replan.domain.goal.dto.refine.RefinedNoteItem;
import plana.replan.domain.goal.dto.refine.RefinedNotes;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.global.exception.CustomException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalAiService {

  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

  private final RestClient geminiRestClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${gemini.api-key}")
  private String apiKey;

  public GoalRefinementResponse refineGoal(GoalRefinementRequest request) {
    String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    String prompt = buildRefinePrompt(request, today);
    String raw = callGemini(prompt);
    return parseRefineResponse(raw);
  }

  private String buildRefinePrompt(GoalRefinementRequest req, String today) {
    return """
        당신은 목표 달성 플래닝 전문가입니다.
        사용자가 제공한 목표 초안을 분석하고, 투두 리스트 생성에 최적화되도록 정제하세요.

        입력:
        목표: %s
        마감기한: %s
        현재수준: %s
        투자가능시간: %s
        특이사항:
        %s

        정제 규칙:
        1. 사용자가 변경 불가한 제약(특정 요일, 교재, 장소 등)은 반드시 그대로 유지
        2. goal: 막연한 표현을 제거하고 측정 가능한 수치·기준을 포함해 구체화. 섹션별 목표가 있으면 명시 (예: "토익 900점" → "토익 900점 달성 (LC 450·RC 450 이상)"). 투자 가능 시간 대비 과도하면 현실적으로 조정하고 이유 명시
        3. currentLevel: 구체적 수치·단계로 표현하고, 현재 수준에서 목표까지의 격차와 달성 난이도를 한 줄로 평가
        4. availableTime: 일/주/월 단위로 환산하고, 총 가용 학습 시간을 합산한 뒤 목표 달성 가능성을 한 줄로 평가
        5. deadline: 오늘 날짜(%s) 기준으로 date(yyyy-MM-dd), time(HH:mm)으로 분리 변환. 사용자가 "기한 없음", "마감기한 설정 안할래요" 등을 명시하면 date와 time 모두 null
        6. 목표 달성에 교재·강의가 필요하지만 사용자가 언급하지 않았다면 Google Search로 사용자 수준에 맞는 교재·강의를 검색하여 notes에 추가
        7. [교재·강의 포함 필수 조건 — 아래 3가지를 모두 충족해야만 포함 가능]
           (a) Google Search로 실제 인터넷에 존재함이 확인될 것
           (b) 책: 저자명·출판사·실제 구매 링크(인터넷 서점 URL 등)를 검색으로 확인할 것
               강의: 강사명·플랫폼명·강의 링크(플랫폼 강의 페이지 URL)를 검색으로 확인할 것
           (c) 링크를 확인할 수 없으면 해당 교재·강의는 반드시 제외할 것 (링크 없이 포함 절대 금지)
           예시 형식: "해커스 토익 기출 VOCA (저자: 해커스어학연구소 / 출판사: 해커스어학원 / 링크: https://www.yes24.com/...)"
                      "스프링 핵심 원리 기본편 (강사: 김영한 / 플랫폼: 인프런 / 링크: https://www.inflearn.com/...)"
        8. notes.value는 목표에 맞는 카테고리(교재/학습전략/루틴/마무리 등, 고정 아님)로 3~5개 항목을 구조화. 각 항목 content는 투두 생성에 바로 쓸 수 있도록 교재명·전략·루틴 방식을 구체적으로 서술
        9. notes.reason은 notes 전체에 대한 이유를 1문장으로 작성
        10. 각 필드 reason은 1~2문장으로 구체적으로 작성 (변경 없으면 "사용자 입력을 그대로 유지했습니다."로 작성)
        11. 모든 텍스트는 "~합니다", "~했습니다" 등 서술형으로 작성. "~하세요", "~하시기 바랍니다" 등 조언·명령형 말투 절대 금지

        반드시 아래 JSON만 출력하세요 (다른 설명 없이):
        {"goal":{"value":"","reason":""},"deadline":{"date":null,"time":null,"reason":""},"currentLevel":{"value":"","reason":""},"availableTime":{"value":"","reason":""},"notes":{"value":[{"title":"","content":""}],"reason":""}}
        """
        .formatted(
            req.goal(),
            req.deadline(),
            req.currentLevel() != null ? req.currentLevel() : "미입력",
            req.availableTime() != null ? req.availableTime() : "미입력",
            req.notes() != null ? req.notes() : "미입력",
            today);
  }

  private GoalRefinementResponse parseRefineResponse(String raw) {
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

      RefinedField currentLevel =
          new RefinedField(
              root.path("currentLevel").path("value").asText(),
              root.path("currentLevel").path("reason").asText());

      RefinedField availableTime =
          new RefinedField(
              root.path("availableTime").path("value").asText(),
              root.path("availableTime").path("reason").asText());

      JsonNode notesNode = root.path("notes");
      List<RefinedNoteItem> noteItems = new ArrayList<>();
      for (JsonNode item : notesNode.path("value")) {
        noteItems.add(
            new RefinedNoteItem(item.path("title").asText(), item.path("content").asText()));
      }
      RefinedNotes notes = new RefinedNotes(noteItems, notesNode.path("reason").asText());

      return new GoalRefinementResponse(goal, deadline, currentLevel, availableTime, notes);
    } catch (Exception e) {
      log.error("Gemini refine 응답 파싱 실패: {}", raw, e);
      throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
    }
  }

  public TodoRecommendationResponse recommendTodos(TodoRecommendationRequest request) {
    validateRefreshCount(request.refreshCount());
    String prompt = buildRecommendPrompt(request);
    String raw = callGemini(prompt);
    return parseRecommendResponse(raw);
  }

  /** 새로고침 횟수를 0~3 범위로 검증한다. null은 허용(첫 추천으로 취급). */
  private void validateRefreshCount(Integer refreshCount) {
    if (refreshCount != null && (refreshCount < 0 || refreshCount > 3)) {
      throw new CustomException(GoalErrorCode.INVALID_REFRESH_COUNT);
    }
  }

  String buildRecommendPrompt(TodoRecommendationRequest req) {
    String deadlineInfo = buildDeadlineInfo(req.deadlineDate(), req.deadlineTime());
    String prompt =
        """
        당신은 목표 달성 플래닝 전문가입니다.

        목표: %s
        마감기한: %s
        현재수준: %s
        투자가능시간: %s
        특이사항:
        %s

        [1단계 — 교재·강의 정보 수집, 투두 생성 전 반드시 먼저 수행]
        notes에 교재·강의명이 하나라도 있으면:
        - notes에 언급된 교재·강의를 빠짐없이 목록화한다
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
        1. notes에 언급된 모든 교재·강의를 빠짐없이 커버한다 (일부만 반영 금지)
        2. 총 분량 ÷ 남은 기간 ÷ 하루 투자시간으로 1회 투두 분량을 계산하여 배분한다
        3. 투두 제목에 검색으로 확인된 실제 강의 제목 또는 챕터명을 포함한다
           예) "인프런 - 한입 리액트 13강 컴포넌트 만들기 수강" (검색으로 확인된 실제 제목)
        4. 1회 투두 분량은 하루 투자시간의 50%%를 초과하지 않는다
        5. 교재·강의가 없으면 notes에 제공된 정보만으로 투두 생성 (새 교재·강의 검색·추천 금지)
        6. RECURRING 투두는 매번 내용이 동일한 작업만 해당 (예: 매일 단어 암기, 매일 운동)
        7. 인강 수강처럼 매번 다른 내용을 학습하는 것은 ONE_TIME 투두 여러 개로 생성
        8. 이론 나열 금지. '수기 복습', '문제 풀이', '오답 노트' 등 아웃풋 태스크 반드시 중간에 배치
        9. 마감 D-1~2는 진도 금지. '최종 점검', '백지 복습' 등 마무리 태스크만 배치
        10. WEEKLY routineDate는 bitmask: 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64
        11. MONTHLY routineDate는 일자(1~31)
        12. DAILY는 routineDate 불필요 (null)
        13. dueDate는 yyyy-MM-dd 형식 또는 null, dueTime은 HH:mm 형식 또는 null
        14. type은 "ONE_TIME" 또는 "RECURRING"만 허용
        15. routineType은 "DAILY", "WEEKLY", "MONTHLY" 중 하나 (ONE_TIME이면 null)
        16. overallReason: 이 추천 전체에 대한 총평을 서술체("~합니다", "~했습니다")로 작성. 조언·명령형("~하세요") 절대 금지.
            - 어떤 기준으로 투두를 구성했는지, 핵심 전략이 무엇인지 서술
            - 교재·강의가 포함된 경우 각 항목마다 아래 형식으로 출처 정보를 포함:
              · 책: "교재명 (저자: OOO / 출판사: OOO / 링크: https://...)"
              · 강의: "강의명 (강사: OOO / 플랫폼: OOO / 링크: https://...)"
            - 링크는 Google Search로 확인된 실제 URL만 사용. 확인 불가 시 링크 생략

        반드시 아래 JSON만 출력하세요 (다른 설명 없이):
        {"overallReason":"","todos":[{"type":"","title":"","dueDate":null,"dueTime":null,"routineType":null,"routineDate":null}]}
        """
            .formatted(
                req.goal(),
                deadlineInfo,
                req.currentLevel() != null ? req.currentLevel() : "미입력",
                req.availableTime() != null ? req.availableTime() : "미입력",
                req.notes() != null ? req.notes() : "미입력");
    return prompt + refreshStyleBlock(req.refreshCount() == null ? 0 : req.refreshCount());
  }

  /** 새로고침 회차(1~3)에 맞는 스타일 안내 블록. 0/그 외는 빈 문자열(0회차는 프롬프트 변경 없음). */
  static String refreshStyleBlock(int refreshCount) {
    String line =
        switch (refreshCount) {
          case 1 -> "1회차(여유): 마감에 5~6일 버퍼를 넉넉히 둔다. 할 일은 아주 잘게(마이크로) 쪼개고, 쉬운 것부터 정순으로 배치한다.";
          case 2 -> "2회차(벼락치기): 버퍼 없이 혹은 마이너스로 빡빡하게 잡는다. 가장 핵심 1개(1-Pick) 위주로 줄이고, 어려운 것부터 역순으로 배치한다.";
          case 3 -> "3회차(환경 변경): 분량·난이도는 적정 수준으로 두되, 진행 시간대나 요일을 옮기는 방향으로 제안한다.";
          default -> null;
        };
    if (line == null) {
      return "";
    }
    return "\n\n[이번 새로고침 스타일]\n"
        + line
        + "\n위 스타일을 우선으로 적용하되, 결과는 위 공통 규칙(투두 형식·JSON 포맷)을 그대로 따른다.";
  }

  private String buildDeadlineInfo(String deadlineDate, String deadlineTime) {
    if (deadlineDate == null && deadlineTime == null) return "미입력";
    if (deadlineDate != null && deadlineTime != null) return deadlineDate + " " + deadlineTime;
    if (deadlineDate != null) return deadlineDate;
    return deadlineTime;
  }

  private TodoRecommendationResponse parseRecommendResponse(String raw) {
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
        Integer routineDate = null;
        JsonNode routineDateNode = node.path("routineDate");
        if (!routineDateNode.isNull() && !routineDateNode.isMissingNode()) {
          if (routineDateNode.isInt()) {
            routineDate = routineDateNode.intValue();
          } else if (routineDateNode.isTextual()) {
            routineDate = Integer.valueOf(routineDateNode.asText());
          } else {
            throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
          }
        }
        todos.add(new RecommendedTodo(type, title, dueDate, dueTime, routineType, routineDate));
      }
      return new TodoRecommendationResponse(overallReason, todos);
    } catch (Exception e) {
      log.error("Gemini recommend 응답 파싱 실패: {}", raw, e);
      throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
    }
  }

  String callGemini(String prompt) {
    Map<String, Object> body =
        Map.of(
            "tools", new Object[] {Map.of("google_search", Map.of())},
            "contents", new Object[] {Map.of("parts", new Object[] {Map.of("text", prompt)})});

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
      return root.path("candidates")
          .path(0)
          .path("content")
          .path("parts")
          .path(0)
          .path("text")
          .asText();
    } catch (RestClientException e) {
      log.error("Gemini API 호출 실패", e);
      throw new CustomException(GoalErrorCode.GEMINI_API_ERROR);
    } catch (Exception e) {
      log.error("Gemini 응답 처리 실패", e);
      throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
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
