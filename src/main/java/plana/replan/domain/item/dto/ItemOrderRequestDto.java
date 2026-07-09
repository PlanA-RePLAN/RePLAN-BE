package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(
    description =
        "통합 아이템 정렬 요청. 프론트가 화면상 앞뒤 아이템의 sortOrder 중간값을 계산해서 보낸다."
            + " TODO면 todoId, ROUTINE이면 routineId+date 필수")
public record ItemOrderRequestDto(
    @Schema(description = "아이템 종류", example = "TODO") @NotNull ItemKind kind,
    @Schema(description = "투두 ID (kind=TODO일 때 필수)", example = "42") Long todoId,
    @Schema(description = "루틴 ID (kind=ROUTINE일 때 필수)", example = "7") Long routineId,
    @Schema(description = "회차 날짜 (kind=ROUTINE일 때 필수)", example = "2026-07-10") LocalDate date,
    @Schema(description = "새 정렬 순서 (앞뒤 아이템 sortOrder의 중간값)", example = "5000.0") @NotNull
        Double sortOrder) {}
