package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "루틴 인스턴스 핀 요청")
public record RoutineOverridePinRequestDto(
    @Schema(description = "true면 핀, false면 언핀", example = "true") @NotNull Boolean isPinned) {}
