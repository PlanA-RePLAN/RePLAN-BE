package plana.replan.domain.monthlyreport.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnalysisData(
    @JsonProperty("top_failure_reason") String topFailureReason,
    @JsonProperty("failure_distribution") List<FailureItem> failureDistribution,
    @JsonProperty("best_achievement_tag") TagStat bestAchievementTag,
    @JsonProperty("worst_achievement_tag") TagStat worstAchievementTag,
    @JsonProperty("best_achievement_day") DayStat bestAchievementDay,
    @JsonProperty("worst_achievement_day") DayStat worstAchievementDay,
    @JsonProperty("pattern_combinations") List<PatternCombination> patternCombinations) {

  public record FailureItem(String reason, int count, double rate) {}

  public record TagStat(String tag, double rate) {}

  public record DayStat(String day, double rate) {}

  public record PatternCombination(String reason, String tag, String day, int count) {}
}
