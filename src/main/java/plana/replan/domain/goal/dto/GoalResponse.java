package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import plana.replan.domain.goal.entity.Goal;

@Schema(description = "목표 단건 응답")
public record GoalResponse(
    @Schema(description = "목표 ID", example = "42") Long id,
    @Schema(description = "목표 제목", example = "토익 900점 달성") String title,
    @Schema(description = "목표 기한", example = "2025-12-31T00:00:00") LocalDateTime dueDate,
    @Schema(description = "참고 자료", example = "https://toeic.ets.org") String reference,
    @Schema(description = "마지막 수정 시간", example = "2025-05-07T12:00:00") LocalDateTime updatedAt) {

  public static GoalResponse from(Goal goal) {
    return new GoalResponse(
        goal.getId(), goal.getTitle(), goal.getDueDate(), goal.getReference(), goal.getUpdatedAt());
  }
}
