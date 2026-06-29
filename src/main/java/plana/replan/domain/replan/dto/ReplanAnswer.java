package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "추가 질문 답변 1건")
public record ReplanAnswer(
    @Schema(description = "질문 key", example = "priority_targets") String key,
    @Schema(description = "TEXT 답변", nullable = true, example = "ADsP 4챕터") String text,
    @Schema(description = "TODO_SELECT 선택 투두 ID 목록", nullable = true) List<Long> selectedTodoIds) {}
