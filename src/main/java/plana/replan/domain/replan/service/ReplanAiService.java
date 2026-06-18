package plana.replan.domain.replan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import plana.replan.domain.replan.dto.ChangedField;
import plana.replan.domain.replan.dto.QuestionType;
import plana.replan.domain.replan.dto.RecommendInput;
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.exception.ReplanErrorCode;
import plana.replan.global.exception.CustomException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplanAiService {

  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

  @Value("${gemini.api-key}")
  private String apiKey;

  private final RestClient geminiRestClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ReplanRecommendResponse generateRecommend(RecommendInput input) {
    return parseRecommend(callGemini(buildRecommendPrompt(input)));
  }

  public List<ReplanQuestion> generateQuestions(RecommendInput input) {
    return parseQuestions(callGemini(buildQuestionsPrompt(input)));
  }

  public String buildQuestionsPrompt(RecommendInput input) {
    return """
        당신은 일정 재계획(리플랜) 도우미입니다.
        사용자가 아래 투두를 오늘 끝내지 못했고, 실패 이유를 골랐습니다.
        더 정확한 투두 개선을 위해 추가로 물어볼 질문이 필요하면 만들어 주세요.
        정보가 충분하면 질문 없이 빈 배열을 반환합니다.

        투두 제목: %s
        실패 이유: %s

        질문 규칙:
        1. type은 TEXT(자유입력), TODO_SELECT(내 투두 목록에서 다중 선택), CHIP(제시 선택지 중 선택) 중 하나
        2. CHIP일 때만 chips 배열을 채우고, 그 외에는 chips를 null로 둔다
        3. 각 질문에 고유한 key(영문 snake_case)를 부여한다
        4. 우선순위를 정하지 못한 경우 TODO_SELECT(우선순위 매길 투두 선택) 질문을 포함한다
        5. 꼭 필요한 질문만. 불필요하면 questions를 빈 배열로 둔다

        반드시 아래 JSON만 출력 (다른 설명 없이):
        {"questions":[{"key":"","type":"TEXT","title":"","chips":null}]}
        """
        .formatted(input.anchorTitle(), String.join(", ", input.reasonLabels()));
  }

  public String buildRecommendPrompt(RecommendInput input) {
    String answers =
        input.answers().isEmpty()
            ? "없음"
            : input.answers().stream().map(this::formatAnswer).collect(Collectors.joining("\n"));
    String routineInfo =
        input.routine() ? "반복 투두(루틴). routineType=" + input.routineType() : "일반 투두";
    return """
        당신은 일정 재계획(리플랜) 도우미입니다.
        사용자가 아래 투두를 끝내지 못한 이유에 맞춰, 투두 수정안과 추가안을 제안하세요.

        오늘 날짜: %s
        대상 투두: %s
        투두 종류: %s
        마감일: %s
        태그: %s
        실패 이유: %s
        추가 질문 답변:
        %s

        규칙:
        1. 결과는 기존 투두 형식(제목/마감기한/태그/반복)만 사용. 새로운 형태 금지
        2. 억지스러운 멘탈케어성 내용은 투두로 만들지 말고 tipNote(줄글)로 안내
        3. summary: 사용자 자유입력을 구조화해 1~3줄로 요약(없으면 빈 문자열)
        4. operations: 각 작업의 action은 ADD/MODIFY_TODO/MODIFY_ROUTINE/CREATE_ROUTINE 중 하나
           - 일반 투두 수정은 MODIFY_TODO(targetTodoId=대상), 새 투두는 ADD(targetTodoId=null)
           - 반복 투두 규칙 변경은 MODIFY_ROUTINE, 새 루틴은 CREATE_ROUTINE
        5. changedFields: 바뀐 필드만 {field, before, after}로. field는 title/dueTime/tag/routineType
           - ADD는 before=null
        6. dueDate는 yyyy-MM-dd 또는 null, dueTime은 HH:mm 또는 null
        7. routineType은 DAILY/WEEKLY/MONTHLY 또는 null, routineDate는 정수 또는 null
        8. 상반된 안(완전 휴식 vs 타협 실행)을 동시에 넣지 말 것. 가장 권장하는 한 가지 방향만

        반드시 아래 JSON만 출력 (다른 설명 없이):
        {"summary":"","tipNote":"","operations":[{"action":"","targetTodoId":null,"title":"","dueDate":null,"dueTime":null,"tagId":null,"routineType":null,"routineDate":null,"changedFields":[{"field":"","before":null,"after":""}]}]}
        """
        .formatted(
            input.today(),
            input.anchorTitle(),
            routineInfo,
            input.anchorDueDate() != null ? input.anchorDueDate() : "없음",
            input.anchorTagName() != null ? input.anchorTagName() : "없음",
            String.join(", ", input.reasonLabels()),
            answers);
  }

  private String formatAnswer(RecommendInput.AnswerInput a) {
    StringBuilder sb = new StringBuilder("- ").append(a.key()).append(": ");
    if (a.text() != null) sb.append(a.text());
    if (a.selectedChips() != null && !a.selectedChips().isEmpty())
      sb.append(" [선택칩: ").append(String.join(",", a.selectedChips())).append("]");
    if (a.selectedTodoIds() != null && !a.selectedTodoIds().isEmpty())
      sb.append(" [선택투두ID: ").append(a.selectedTodoIds()).append("]");
    return sb.toString();
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
      log.error("리플랜 Gemini API 호출 실패", e);
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_API_ERROR);
    } catch (Exception e) {
      log.error("리플랜 Gemini 응답 처리 실패", e);
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_PARSE_ERROR);
    }
  }

  public ReplanRecommendResponse parseRecommend(String raw) {
    try {
      JsonNode root = objectMapper.readTree(extractJson(raw));
      String summary = textOrNull(root.path("summary"));
      String tipNote = textOrNull(root.path("tipNote"));
      List<ReplanOperation> operations = new ArrayList<>();
      for (JsonNode op : root.path("operations")) {
        List<ChangedField> changed = new ArrayList<>();
        for (JsonNode cf : op.path("changedFields")) {
          changed.add(
              new ChangedField(
                  cf.path("field").asText(),
                  textOrNull(cf.path("before")),
                  textOrNull(cf.path("after"))));
        }
        operations.add(
            new ReplanOperation(
                ReplanAction.valueOf(op.path("action").asText()),
                longOrNull(op.path("targetTodoId")),
                textOrNull(op.path("title")),
                textOrNull(op.path("dueDate")),
                textOrNull(op.path("dueTime")),
                longOrNull(op.path("tagId")),
                textOrNull(op.path("routineType")),
                intOrNull(op.path("routineDate")),
                changed));
      }
      return new ReplanRecommendResponse(summary, tipNote, operations);
    } catch (Exception e) {
      log.error("리플랜 추천 응답 파싱 실패: {}", raw, e);
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_PARSE_ERROR);
    }
  }

  public List<ReplanQuestion> parseQuestions(String raw) {
    try {
      JsonNode root = objectMapper.readTree(extractJson(raw));
      List<ReplanQuestion> questions = new ArrayList<>();
      for (JsonNode q : root.path("questions")) {
        List<String> chips = null;
        if (q.path("chips").isArray()) {
          chips = new ArrayList<>();
          for (JsonNode c : q.path("chips")) {
            chips.add(c.asText());
          }
        }
        questions.add(
            new ReplanQuestion(
                q.path("key").asText(),
                QuestionType.valueOf(q.path("type").asText()),
                q.path("title").asText(),
                chips));
      }
      return questions;
    } catch (Exception e) {
      log.error("리플랜 질문 응답 파싱 실패: {}", raw, e);
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_PARSE_ERROR);
    }
  }

  private String textOrNull(JsonNode node) {
    return node.isNull() || node.isMissingNode() ? null : node.asText(null);
  }

  private Long longOrNull(JsonNode node) {
    return node.isNull() || node.isMissingNode() ? null : node.asLong();
  }

  private Integer intOrNull(JsonNode node) {
    return node.isNull() || node.isMissingNode() ? null : node.asInt();
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
