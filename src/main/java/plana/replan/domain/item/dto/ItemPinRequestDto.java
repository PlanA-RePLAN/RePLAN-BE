package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "통합 아이템 핀 요청. TODO면 todoId, ROUTINE이면 routineId+date 필수")
public record ItemPinRequestDto(
    @Schema(description = "아이템 종류", example = "TODO") @NotNull ItemKind kind,
    @Schema(description = "투두 ID (kind=TODO일 때 필수)", example = "42") Long todoId,
    @Schema(description = "루틴 ID (kind=ROUTINE일 때 필수)", example = "7") Long routineId,
    @Schema(description = "회차 날짜 (kind=ROUTINE일 때 필수)", example = "2026-07-10") LocalDate date,
    @Schema(description = "true면 핀, false면 언핀", example = "true") @NotNull Boolean isPinned) {}
