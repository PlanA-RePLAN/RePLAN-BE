package plana.replan.domain.monthlyreport.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.ReplanRecord;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.RoutineSnapshot;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.TagOption;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.UncompletedTodo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 월간 통계 AI 호출부. 한 번의 Gemini 호출로 심층분석 인사이트(insights/writing_tip)와 팁노트(tip_note)를 함께 생성한다. 두 파트는 따로
 * 파싱해서 한쪽이 깨져도 다른 쪽은 살린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportAiService {

  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

  // 인사이트+팁노트를 한 번에 받으므로 출력이 길다. 출력 한도를 넉넉히 잡고 '생각'을 낮게 제한해
  // 생각이 출력 예산을 먹어 답이 잘리는 문제(finishReason=MAX_TOKENS)를 막는다.
  private static final int GEMINI_MAX_OUTPUT_TOKENS = 8192;

  private static final DateTimeFormatter DUE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private final RestClient geminiRestClient;
  private final TipNoteDraftParser tipNoteDraftParser;
  private final Clock clock;
  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Value("${gemini.api-key}")
  private String apiKey;

  /** 인사이트와 팁노트를 한 번의 호출로 생성한다. */
  public MonthlyAiResult generate(
      CalculatedStats stats, YearMonth targetMonth, TipNoteMaterials materials) {
    LocalDate today = LocalDate.now(clock);
    String prompt = buildPrompt(stats, targetMonth, materials, today);
    String raw = callGemini(prompt);
    return new MonthlyAiResult(parseInsight(raw), parseTipNote(raw, materials, today));
  }

  private String buildPrompt(
      CalculatedStats stats, YearMonth targetMonth, TipNoteMaterials materials, LocalDate today) {
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

        [지난달 미완료 투두 전체 목록]
        %s

        [지난달 리플랜 기록 — 투두별 실패 이유]
        %s
        ※ 리플랜 기록은 이 사용자의 실패 성향(패턴)을 파악하는 재료입니다.
        리플랜된 투두 자체를 다시 고치라는 뜻이 아닙니다.

        [현재 살아있는 루틴 목록 — 수정 제안(MODIFY_ROUTINE)의 대상]
        %s

        [사용 가능한 태그 목록]
        %s

        오늘 날짜(한국시간): %s

        위 데이터를 바탕으로 다음 세 가지를 작성하세요:
        1. 핵심 인사이트 2~3개 (summary: 한 줄 제목, detail: 2~3문장 구체적 설명)
        2. 다음 달 투두 작성 팁 (writing_tip: 3~4문장, 이 사용자의 데이터에 맞는 구체적인 팁)
        3. 팁노트(tip_note): 다음 달 투두리스트 제안
           - tip: 지난달의 주된 실패 패턴 요약 + 어떤 방향의 투두리스트를 제안하는지 2~3문장
           - items: 추천 2~4개. action은 ADD_TODO(새 일반 투두) / ADD_ROUTINE(새 루틴) /
             MODIFY_ROUTINE(기존 루틴 수정) 중 하나

        [팁노트 작성 규칙]
        - 미완료 투두와 실패 이유에 직결된, 바로 실행 가능한 제안만 만드세요. 억지스러운 멘탈케어성
          내용(명언 읽기 등)은 금지.
        - 날짜 규칙: todoDueAt("yyyy-MM-dd HH:mm")과 routineEndAt("yyyy-MM-dd")은 반드시
          오늘(%s) 이후의 날짜여야 합니다.
        - ADD_TODO: todoDueAt 필수. routineType/routineDays/routineTime/routineEndAt은 null.
        - ADD_ROUTINE: routineType(DAILY/WEEKLY/MONTHLY)과 routineTime("HH:mm")을 지정.
          routineDays는 WEEKLY=요일 인덱스 배열(월=0, 화=1, 수=2, 목=3, 금=4, 토=5, 일=6, 예 월·수·금=[0,2,4]),
          MONTHLY=일자 배열(1~31, 예 [3,20]), DAILY=null. routineEndAt은 반복 종료일(무기한이면 null).
        - MODIFY_ROUTINE: targetRoutineId는 반드시 위 루틴 목록에 있는 id(숫자)만 사용.
          바꾸지 않는 필드도 기존 값을 그대로 채워 "수정 후 최종 모습 전체"를 출력하세요.
          반복 타입(routineType)을 바꾸면 새 타입에 맞는 routineDays를 반드시 함께 출력하세요.
        - tagId는 반드시 위 태그 목록에 있는 id(숫자)만 사용. 목록에 없는 id나 태그 '이름'을 넣지 말 것.
          마땅한 태그가 없으면 null.
        - 상호 모순되는 제안(완전 휴식 vs 무리한 강행)을 동시에 넣지 마세요.

        insights와 writing_tip의 모든 텍스트는 "~합니다", "~했습니다", "~입니다" 등 서술형으로 작성하세요.
        "~하세요", "~해야 합니다" 등 명령·조언형 말투는 절대 사용하지 마세요.

        반드시 아래 JSON만 출력하세요 (다른 설명 없이):
        {"insights":[{"summary":"","detail":""}],"writing_tip":"","tip_note":{"tip":"","items":[{"action":"","targetRoutineId":null,"title":"","tagId":null,"todoDueAt":null,"routineEndAt":null,"routineTime":null,"routineType":null,"routineDays":null}]}}
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
            worstDayText,
            uncompletedText(materials.uncompletedTodos()),
            replanText(materials.replanRecords()),
            routineText(materials.routines()),
            tagText(materials.tags()),
            today,
            today);
  }

  private String uncompletedText(List<UncompletedTodo> todos) {
    if (todos.isEmpty()) {
      return "없음";
    }
    StringBuilder sb = new StringBuilder();
    for (UncompletedTodo todo : todos) {
      sb.append("- ").append(todo.title());
      sb.append(" (마감 ").append(todo.dueDate() != null ? todo.dueDate().format(DUE_FORMAT) : "없음");
      if (todo.tagName() != null) {
        sb.append(", 태그 ").append(todo.tagName());
      }
      if (todo.routine()) {
        sb.append(", 반복 투두");
      }
      sb.append(")\n");
    }
    return sb.toString().strip();
  }

  private String replanText(List<ReplanRecord> records) {
    if (records.isEmpty()) {
      return "없음";
    }
    StringBuilder sb = new StringBuilder();
    for (ReplanRecord record : records) {
      sb.append("- \"")
          .append(record.todoTitle() != null ? record.todoTitle() : "(삭제된 투두)")
          .append("\": ")
          .append(String.join(" > ", record.reasonLabels()))
          .append("\n");
    }
    return sb.toString().strip();
  }

  private String routineText(List<RoutineSnapshot> routines) {
    if (routines.isEmpty()) {
      return "없음";
    }
    StringBuilder sb = new StringBuilder();
    for (RoutineSnapshot routine : routines) {
      sb.append("- id=").append(routine.id());
      sb.append(" | 제목=").append(routine.title());
      sb.append(" | 반복=").append(routine.routineType());
      if (routine.routineDays() != null) {
        sb.append(" ").append(routine.routineDays());
      }
      sb.append(" | 시각=")
          .append(routine.routineTime() != null ? routine.routineTime().format(TIME_FORMAT) : "없음");
      sb.append(" | 종료일=").append(routine.endAt() != null ? routine.endAt().toLocalDate() : "무기한");
      sb.append(" | 태그=").append(routine.tagName() != null ? routine.tagName() : "없음");
      sb.append("\n");
    }
    return sb.toString().strip();
  }

  private String tagText(List<TagOption> tags) {
    if (tags.isEmpty()) {
      return "없음";
    }
    StringBuilder sb = new StringBuilder();
    for (TagOption tag : tags) {
      sb.append("- id=").append(tag.id()).append(", name=").append(tag.name()).append("\n");
    }
    return sb.toString().strip();
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

  /** 팁노트 파트만 따로 파싱한다. 실패해도 인사이트는 살아야 하므로 예외 대신 null(팁노트 없음)로 처리. */
  private TipNoteDraft parseTipNote(String raw, TipNoteMaterials materials, LocalDate today) {
    try {
      JsonNode root = objectMapper.readTree(extractJson(raw));
      JsonNode tipNote = root.path("tip_note");
      if (tipNote.isMissingNode() || tipNote.isNull()) {
        return null;
      }
      return tipNoteDraftParser.parse(tipNote, materials, today);
    } catch (Exception e) {
      log.error("팁노트 응답 파싱 실패 (length={})", raw == null ? 0 : raw.length(), e);
      return null;
    }
  }

  private String callGemini(String prompt) {
    Map<String, Object> generationConfig = new LinkedHashMap<>();
    generationConfig.put("maxOutputTokens", GEMINI_MAX_OUTPUT_TOKENS);
    // '생각' 분량을 낮게 제한해 출력 예산 대부분을 실제 답에 쓰게 한다(과한 생각으로 답이 잘리는 것 방지).
    generationConfig.put("thinkingConfig", Map.of("thinkingLevel", "low"));

    Map<String, Object> body =
        Map.of(
            "contents",
            new Object[] {Map.of("parts", new Object[] {Map.of("text", prompt)})},
            "generationConfig",
            generationConfig);

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
      JsonNode candidate = root.path("candidates").path(0);
      if ("MAX_TOKENS".equals(candidate.path("finishReason").asText(""))) {
        log.error("통계 Gemini 응답이 길이 제한에 잘림(finishReason=MAX_TOKENS)");
      }
      return candidate.path("content").path("parts").path(0).path("text").asText();
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
