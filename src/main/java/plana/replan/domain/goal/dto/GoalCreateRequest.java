package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Schema(description = "목표 생성 요청")
public record GoalCreateRequest(
    @Schema(
            description = "목표 제목",
            example = "토익 900점 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목은 필수입니다.")
        String title,
    @Schema(description = "목표 기한 (ISO 8601 형식)", example = "2025-12-31T00:00:00")
        LocalDateTime dueDate,
    @Schema(description = "참고 자료 (URL 또는 메모)", example = "https://toeic.ets.org")
        String reference) {}
