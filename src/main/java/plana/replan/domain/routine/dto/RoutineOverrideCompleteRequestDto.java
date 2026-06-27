package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "루틴 인스턴스 완료 처리 요청")
public record RoutineOverrideCompleteRequestDto(
    @Schema(description = "true면 완료, false면 미완료 처리", example = "true") @NotNull
        Boolean isCompleted) {}
