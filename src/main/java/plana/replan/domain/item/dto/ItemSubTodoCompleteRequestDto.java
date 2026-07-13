package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "통합 아이템 하위 투두 완료/미완료 요청. 행이 있는 하위(todoId 존재)만 가능 — 예약분·예정분은 행이 없어 완료 개념이 없다")
public record ItemSubTodoCompleteRequestDto(
    @Schema(description = "부모 투두 ID (상세 응답의 todoId)", example = "42") @NotNull Long parentTodoId,
    @Schema(description = "하위 투두 ID (상세 응답 subTodos의 todoId)", example = "43") @NotNull
        Long subTodoId,
    @Schema(description = "true면 완료, false면 미완료", example = "true") @NotNull Boolean isCompleted) {}
