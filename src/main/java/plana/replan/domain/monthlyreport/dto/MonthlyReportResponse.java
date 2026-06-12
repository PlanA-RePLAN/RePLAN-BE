package plana.replan.domain.monthlyreport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "월간 통계 응답")
public record MonthlyReportResponse(
    @Schema(description = "연도", example = "2025") int year,
    @Schema(description = "월", example = "5") int month,
    @Schema(description = "해당 월 전체 투두 수", example = "30") int totalTodos,
    @Schema(description = "달성 투두 수", example = "21") int completedTodos,
    @Schema(description = "달성률 (%)", example = "70.00") double achievementRate,
    @Schema(description = "전월 대비 달성률 차이 (%p). 전월 데이터 없으면 null", example = "5.50")
        Double prevMonthDiff,
    @Schema(description = "리플랜 횟수", example = "3") int replanCount,
    @Schema(description = "리플랜 달성 효과 (%). 리플랜 없으면 null", example = "66.67")
        Double replanAchievementEffect,
    @Schema(description = "분석 데이터. 투두 활동 없으면 null") AnalysisDataResponse analysisData,
    @Schema(description = "AI 인사이트. 투두 활동 없으면 null") AiInsightResponse aiInsight) {

  @Schema(description = "분석 데이터")
  public record AnalysisDataResponse(
      @Schema(description = "가장 많은 실패 원인", example = "심리적 저항") String topFailureReason,
      @Schema(description = "실패 원인 분포") List<FailureDistributionItem> failureDistribution,
      @Schema(description = "달성률 높은 태그. 태그가 2개 이상인 경우에만 존재") TagStatResponse bestAchievementTag,
      @Schema(description = "달성률 낮은 태그. 태그가 2개 이상인 경우에만 존재") TagStatResponse worstAchievementTag,
      @Schema(description = "달성률 높은 요일") DayStatResponse bestAchievementDay,
      @Schema(description = "달성률 낮은 요일") DayStatResponse worstAchievementDay,
      @Schema(description = "실패 패턴 조합 목록") List<PatternCombinationResponse> patternCombinations) {}

  @Schema(description = "태그별 달성률")
  public record TagStatResponse(
      @Schema(description = "태그 이름", example = "운동") String title,
      @Schema(description = "태그 색상 hex 코드. 태그가 삭제된 경우 null", example = "#FF5733") String color,
      @Schema(description = "달성률 (%)", example = "85.00") double rate) {}

  @Schema(description = "요일별 달성률")
  public record DayStatResponse(
      @Schema(description = "요일", example = "월요일") String day,
      @Schema(description = "달성률 (%)", example = "75.00") double rate) {}

  @Schema(description = "실패 원인 분포 항목")
  public record FailureDistributionItem(
      @Schema(description = "실패 원인 (depth-1 라벨)", example = "심리적 저항") String reason,
      @Schema(description = "건수", example = "5") int count,
      @Schema(description = "비율 (%)", example = "55.56") double rate) {}

  @Schema(description = "실패 패턴 조합")
  public record PatternCombinationResponse(
      @Schema(description = "실패 원인", example = "심리적 저항") String reason,
      @Schema(description = "연관 태그. 요일 패턴이면 null", example = "운동") String tag,
      @Schema(description = "연관 요일. 태그 패턴이면 null", example = "월요일") String day,
      @Schema(description = "발생 횟수", example = "3") int count) {}

  @Schema(description = "AI 인사이트")
  public record AiInsightResponse(
      @Schema(description = "핵심 인사이트 목록") List<InsightItemResponse> insights,
      @Schema(description = "투두 작성 팁", example = "월요일에는 가벼운 투두 위주로 구성하면 달성률이 높아집니다.")
          String writingTip) {}

  @Schema(description = "인사이트 항목")
  public record InsightItemResponse(
      @Schema(description = "한 줄 요약", example = "화요일 집중력이 가장 높습니다") String summary,
      @Schema(description = "구체적 설명", example = "화요일 달성률이 90%로 가장 높았습니다.") String detail) {}
}
