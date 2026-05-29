package plana.replan.domain.goal.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "목표+투두 일괄 생성 응답")
public record GoalWithTodosCreateResponse(
    @Schema(description = "생성된 목표 ID", example = "10") Long goalId,
    @Schema(description = "생성된 투두/루틴 목록") List<CreatedTodoItem> todos) {}
