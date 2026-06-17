package plana.replan.domain.replan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import plana.replan.domain.replan.dto.ChangedField;
import plana.replan.domain.replan.dto.QuestionType;
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

  private final RestClient geminiRestClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

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
