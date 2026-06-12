package plana.replan.domain.monthlyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import plana.replan.domain.monthlyreport.entity.AiInsight;
import plana.replan.domain.monthlyreport.entity.AnalysisData;

@ExtendWith(MockitoExtension.class)
class MonthlyReportAiServiceTest {

  @Mock private RestClient geminiRestClient;

  private MonthlyReportAiService aiService;

  @BeforeEach
  void setUp() {
    aiService = new MonthlyReportAiService(geminiRestClient);
    ReflectionTestUtils.setField(aiService, "apiKey", "test-api-key");
  }

  private void stubGemini(String geminiResponseBody) {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    given(geminiRestClient.post()).willReturn(uriSpec);
    given(uriSpec.uri(anyString())).willReturn(bodySpec);
    given(bodySpec.header(anyString(), any(String[].class))).willReturn(bodySpec);
    given(bodySpec.contentType(any(MediaType.class))).willReturn(bodySpec);
    given(bodySpec.body(any(Object.class))).willReturn(bodySpec);
    given(bodySpec.retrieve()).willReturn(responseSpec);
    given(responseSpec.body(String.class)).willReturn(geminiResponseBody);
  }

  private CalculatedStats minimalStats() {
    AnalysisData ad = new AnalysisData(null, List.of(), null, null, null, null, List.of());
    return new CalculatedStats(10, 7, new BigDecimal("70.00"), null, 0, null, ad, true);
  }

  // ── 정상 파싱 ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Gemini 정상 응답: insights와 writingTip 파싱")
  void generateInsight_success_parsedCorrectly() {
    String insightJson =
        """
        {"insights":[{"summary":"화요일 집중력 최고","detail":"화요일 달성률이 90%였습니다."}],"writing_tip":"월요일에는 가벼운 투두를 추천합니다."}
        """;
    String geminiResponse =
        """
        {"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
        """
            .formatted(insightJson.strip().replace("\"", "\\\""));

    stubGemini(geminiResponse);

    AiInsight result = aiService.generateInsight(minimalStats(), YearMonth.of(2025, 5));

    assertThat(result.insights()).hasSize(1);
    assertThat(result.insights().get(0).summary()).isEqualTo("화요일 집중력 최고");
    assertThat(result.insights().get(0).detail()).isEqualTo("화요일 달성률이 90%였습니다.");
    assertThat(result.writingTip()).isEqualTo("월요일에는 가벼운 투두를 추천합니다.");
  }

  @Test
  @DisplayName("Gemini 응답 텍스트에 JSON 마크다운 블록 포함: { } 사이만 추출해 파싱")
  void generateInsight_jsonInMarkdown_extractedAndParsed() {
    String rawText =
        "```json\\n{\\\"insights\\\":[{\\\"summary\\\":\\\"좋은 달\\\",\\\"detail\\\":\\\"잘 했습니다.\\\"}],\\\"writing_tip\\\":\\\"파이팅\\\"}\\n```";
    String geminiResponse =
        """
        {"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
        """
            .formatted(rawText);

    stubGemini(geminiResponse);

    AiInsight result = aiService.generateInsight(minimalStats(), YearMonth.of(2025, 5));

    assertThat(result.insights()).hasSize(1);
    assertThat(result.insights().get(0).summary()).isEqualTo("좋은 달");
    assertThat(result.writingTip()).isEqualTo("파이팅");
  }

  // ── fallback ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Gemini 응답 텍스트가 유효하지 않은 JSON: 빈 AiInsight 반환")
  void generateInsight_malformedJson_returnsFallback() {
    String geminiResponse =
        """
        {"candidates":[{"content":{"parts":[{"text":"이건 JSON이 아닙니다"}]}}]}
        """;

    stubGemini(geminiResponse);

    AiInsight result = aiService.generateInsight(minimalStats(), YearMonth.of(2025, 5));

    assertThat(result.insights()).isEmpty();
    assertThat(result.writingTip()).isNull();
  }

  @Test
  @DisplayName("RestClient 예외 발생: fallback JSON으로 빈 AiInsight 반환")
  @SuppressWarnings("unchecked")
  void generateInsight_restClientThrows_returnsFallback() {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    given(geminiRestClient.post()).willReturn(uriSpec);
    given(uriSpec.uri(anyString())).willThrow(new RuntimeException("네트워크 오류"));

    AiInsight result = aiService.generateInsight(minimalStats(), YearMonth.of(2025, 5));

    assertThat(result.insights()).isEmpty();
    assertThat(result.writingTip()).isNull();
  }
}
