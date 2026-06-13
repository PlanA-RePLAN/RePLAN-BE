package plana.replan.domain.monthlyreport.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import plana.replan.domain.monthlyreport.entity.AiInsight;
import plana.replan.domain.monthlyreport.entity.AnalysisData;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportAiService {

  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

  private final RestClient geminiRestClient;
  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Value("${gemini.api-key}")
  private String apiKey;

  public AiInsight generateInsight(CalculatedStats stats, YearMonth targetMonth) {
    String prompt = buildPrompt(stats, targetMonth);
    String raw = callGemini(prompt);
    return parseInsight(raw);
  }

  private String buildPrompt(CalculatedStats stats, YearMonth targetMonth) {
    AnalysisData ad = stats.analysisData();

    String prevDiffText =
        stats.prevMonthDiff() == null
            ? "전월 데이터 없음"
            : (stats.prevMonthDiff().signum() >= 0
                ? "+" + stats.prevMonthDiff() + "%p"
                : stats.prevMonthDiff() + "%p");

    String bestTagText =
        ad.bestAchievementTag() == null
            ? "없음"
            : String.format(
                "%s (%.1f%%)", ad.bestAchievementTag().tag(), ad.bestAchievementTag().rate());
    String worstTagText =
        ad.worstAchievementTag() == null
            ? "없음"
            : String.format(
                "%s (%.1f%%)", ad.worstAchievementTag().tag(), ad.worstAchievementTag().rate());
    String bestDayText =
        ad.bestAchievementDay() == null
            ? "없음"
            : String.format(
                "%s (%.1f%%)", ad.bestAchievementDay().day(), ad.bestAchievementDay().rate());
    String worstDayText =
        ad.worstAchievementDay() == null
            ? "없음"
            : String.format(
                "%s (%.1f%%)", ad.worstAchievementDay().day(), ad.worstAchievementDay().rate());

    String failureText =
        ad.failureDistribution() == null || ad.failureDistribution().isEmpty()
            ? "리플랜 없음"
            : ad.failureDistribution().stream()
                .map(f -> String.format("- %s: %d건 (%.1f%%)", f.reason(), f.count(), f.rate()))
                .reduce("", (a, b) -> a + "\n" + b)
                .strip();

    return """
        당신은 할일 관리 전문 코치입니다.

        사용자의 %d년 %d월 투두 달성 분석 데이터입니다:
        - 전체 달성률: %.1f%%
        - 전월 대비: %s
        - 총 투두: %d개 (달성 %d개, 미달성 %d개)
        - 리플랜 횟수: %d회

        실패 원인 분포:
        %s

        달성률 높은 태그: %s
        달성률 낮은 태그: %s
        달성률 높은 요일: %s
        달성률 낮은 요일: %s

        위 데이터를 바탕으로 다음을 작성하세요:
        1. 핵심 인사이트 2~3개 (summary: 한 줄 제목, detail: 2~3문장 구체적 설명)
        2. 다음 달 투두 작성 팁 (3~4문장, 이 사용자의 데이터에 맞는 구체적인 팁)

        모든 텍스트는 "~합니다", "~했습니다", "~입니다" 등 서술형으로 작성하세요.
        "~하세요", "~해야 합니다" 등 명령·조언형 말투는 절대 사용하지 마세요.

        반드시 아래 JSON만 출력하세요 (다른 설명 없이):
        {"insights":[{"summary":"","detail":""}],"writing_tip":""}
        """
        .formatted(
            targetMonth.getYear(),
            targetMonth.getMonthValue(),
            stats.achievementRate().doubleValue(),
            prevDiffText,
            stats.totalTodos(),
            stats.completedTodos(),
            stats.totalTodos() - stats.completedTodos(),
            stats.replanCount(),
            failureText,
            bestTagText,
            worstTagText,
            bestDayText,
            worstDayText);
  }

  private AiInsight parseInsight(String raw) {
    try {
      String json = extractJson(raw);
      JsonNode root = objectMapper.readTree(json);

      List<AiInsight.InsightItem> insights = new ArrayList<>();
      for (JsonNode node : root.path("insights")) {
        insights.add(
            new AiInsight.InsightItem(node.path("summary").asText(), node.path("detail").asText()));
      }

      String writingTip = root.path("writing_tip").asText(null);
      return new AiInsight(insights, writingTip);
    } catch (Exception e) {
      log.error("통계 AI 응답 파싱 실패 (length={})", raw == null ? 0 : raw.length(), e);
      return new AiInsight(List.of(), null);
    }
  }

  private String callGemini(String prompt) {
    Map<String, Object> body =
        Map.of("contents", new Object[] {Map.of("parts", new Object[] {Map.of("text", prompt)})});

    try {
      String response =
          geminiRestClient
              .post()
              .uri(GEMINI_URL)
              .header("x-goog-api-key", apiKey)
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
    } catch (Exception e) {
      log.error("Gemini 통계 API 호출 실패", e);
      return "{\"insights\":[],\"writing_tip\":null}";
    }
  }

  private String extractJson(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start == -1 || end == -1 || end < start) {
      throw new IllegalArgumentException("JSON 블록 없음: " + text);
    }
    return text.substring(start, end + 1);
  }
}
