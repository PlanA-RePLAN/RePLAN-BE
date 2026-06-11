package plana.replan.domain.monthlyreport.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiInsight(
    List<InsightItem> insights, @JsonProperty("writing_tip") String writingTip) {

  public record InsightItem(String summary, String detail) {}
}
