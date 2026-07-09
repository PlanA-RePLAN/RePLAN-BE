package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(
    description =
        """
        통합 아이템 삭제 요청.
        - kind=TODO: todoId 필수. 투두 삭제
        - kind=ROUTINE + scope=THIS: routineId+date 필수. 그 날짜 회차만 건너뛰기(skip)
        - kind=ROUTINE + scope=ALL: routineId 필수. 반복 전체(엄마 루틴) 삭제
        """)
public record ItemDeleteRequestDto(
    @Schema(description = "아이템 종류", example = "TODO") @NotNull ItemKind kind,
    @Schema(description = "투두 ID (kind=TODO일 때 필수)", example = "42") Long todoId,
    @Schema(description = "루틴 ID (kind=ROUTINE일 때 필수)", example = "7") Long routineId,
    @Schema(description = "회차 날짜 (kind=ROUTINE + scope=THIS일 때 필수)", example = "2026-07-10")
        LocalDate date,
    @Schema(description = "적용 범위. kind=ROUTINE일 때 필수 (THIS=이 회차만 건너뛰기 / ALL=반복 전체 삭제)")
        ItemScope scope) {}
