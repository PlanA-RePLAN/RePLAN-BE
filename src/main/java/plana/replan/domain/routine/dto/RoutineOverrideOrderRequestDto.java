package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "루틴 인스턴스 정렬 변경 요청")
public record RoutineOverrideOrderRequestDto(
    @Schema(description = "정렬 순서 (인접 항목의 sortOrder 기반으로 프론트가 계산)", example = "5000.0") @NotNull
        Double sortOrder) {}
