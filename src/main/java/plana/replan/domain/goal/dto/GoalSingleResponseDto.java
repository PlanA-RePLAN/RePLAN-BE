package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import plana.replan.domain.goal.entity.Goal;

@Schema(description = "목표 단건 응답")
public record GoalSingleResponseDto(
    @Schema(description = "목표 ID", example = "10") Long id,
    @Schema(description = "목표 제목", example = "토익 850점 달성") String title,
    @Schema(description = "마감기한 (없으면 null)", example = "2026-05-26T20:00:00") LocalDateTime dueDate,
    @Schema(description = "참고 자료 (없으면 null)", example = "https://toeic.ets.org") String reference) {

  public static GoalSingleResponseDto from(Goal goal) {
    return new GoalSingleResponseDto(
        goal.getId(), goal.getTitle(), goal.getDueDate(), goal.getReference());
  }
}
