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
import plana.replan.domain.goal.dto.GoalRefinementRequestDto;
import plana.replan.domain.goal.dto.GoalRefinementResponseDto;
import plana.replan.domain.goal.dto.NoteItemDto;
import plana.replan.domain.goal.dto.RecommendedTodoDto;
import plana.replan.domain.goal.dto.RefinedDeadline;
import plana.replan.domain.goal.dto.RefinedField;
import plana.replan.domain.goal.dto.RefinedNotes;
import plana.replan.domain.goal.dto.TodoRecommendationRequestDto;
import plana.replan.domain.goal.dto.TodoRecommendationResponseDto;
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

  public GoalRefinementResponseDto refineGoal(GoalRefinementRequestDto request) {
    String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    String prompt = buildRefinePrompt(request, today);
    String raw = callGemini(prompt);
    return parseRefineResponse(raw);
  }

  private String buildRefinePrompt(GoalRefinementRequestDto req, String today) {
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
        7. notes.value는 목표에 맞는 카테고리(교재/학습전략/루틴/마무리 등, 고정 아님)로 3~5개 항목을 구조화. 각 항목 content는 투두 생성에 바로 쓸 수 있도록 교재명·전략·루틴 방식을 구체적으로 서술
        8. notes.reason은 notes 전체에 대한 이유를 1문장으로 작성
        9. 각 필드 reason은 1~2문장으로 구체적으로 작성 (변경 없으면 "사용자 입력을 그대로 유지했습니다."로 작성)

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

  private GoalRefinementResponseDto parseRefineResponse(String raw) {
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
      List<NoteItemDto> noteItems = new ArrayList<>();
      for (JsonNode item : notesNode.path("value")) {
        noteItems.add(new NoteItemDto(item.path("title").asText(), item.path("content").asText()));
      }
      RefinedNotes notes = new RefinedNotes(noteItems, notesNode.path("reason").asText());

      return new GoalRefinementResponseDto(goal, deadline, currentLevel, availableTime, notes);
    } catch (Exception e) {
      log.error("Gemini refine 응답 파싱 실패: {}", raw, e);
      throw new CustomException(GoalErrorCode.GEMINI_PARSE_ERROR);
    }
  }

  public TodoRecommendationResponseDto recommendTodos(TodoRecommendationRequestDto request) {
    String prompt = buildRecommendPrompt(request);
    String raw = callGemini(prompt);
    return parseRecommendResponse(raw);
  }

  private String buildRecommendPrompt(TodoRecommendationRequestDto req) {
    return """
        당신은 목표 달성 플래닝 전문가입니다.

        목표: %s
        마감기한: %s
        현재수준: %s
        투자가능시간: %s
        특이사항:
        %s

        투두 생성 규칙:
        1. 교재·강의가 포함된 경우 Google Search로 해당 교재·강의의 목차와 분량을 검색하여 그 분량 기반으로 투두를 세분화
        2. 교재·강의가 없으면 notes에 제공된 정보만으로 투두 생성 (새 교재·강의 검색·추천 금지)
        3. RECURRING 투두는 매번 내용이 동일한 작업만 해당 (예: 매일 물 마시기, 운동)
        4. 인강 수강처럼 매번 다른 내용을 학습하는 것은 ONE_TIME 투두 여러 개로 생성
        5. 이론 나열 금지. '수기 복습', '문제 풀이', '오답 노트' 등 아웃풋 태스크 반드시 중간에 배치
        6. 마감 D-1~2는 진도 금지. '실전 모의고사', '백지 복습', '최종 점검' 등 마무리 태스크만 배치
        7. WEEKLY routineDate는 bitmask: 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64
        8. MONTHLY routineDate는 일자(1~31)
        9. DAILY는 routineDate 불필요 (null)
        10. dueDate는 yyyy-MM-ddT00:00:00 형식 또는 null
        11. type은 "ONE_TIME" 또는 "RECURRING"만 허용
        12. routineType은 "DAILY", "WEEKLY", "MONTHLY" 중 하나 (ONE_TIME이면 null)

        반드시 아래 JSON만 출력하세요 (다른 설명 없이):
        {"todos":[{"type":"","title":"","dueDate":null,"routineType":null,"routineDate":null}]}
        """
        .formatted(
            req.goal(), req.deadline(), req.currentLevel(), req.availableTime(), req.notes());
  }

  private TodoRecommendationResponseDto parseRecommendResponse(String raw) {
    try {
      String json = extractJson(raw);
      JsonNode root = objectMapper.readTree(json);
      JsonNode todosNode = root.path("todos");

      List<RecommendedTodoDto> todos = new ArrayList<>();
      for (JsonNode node : todosNode) {
        String type = node.path("type").asText();
        String title = node.path("title").asText();
        String dueDate = node.path("dueDate").isNull() ? null : node.path("dueDate").asText(null);
        String routineType =
            node.path("routineType").isNull() ? null : node.path("routineType").asText(null);
        Integer routineDate =
            node.path("routineDate").isNull() ? null : node.path("routineDate").intValue();
        todos.add(new RecommendedTodoDto(type, title, dueDate, routineType, routineDate));
      }
      return new TodoRecommendationResponseDto(todos);
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
