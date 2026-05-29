package plana.replan.domain.goal.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "생성된 투두 항목")
public record CreatedTodoItem(
    @Schema(description = "투두 유형 (ONE_TIME / RECURRING)", example = "ONE_TIME") String type,
    @Schema(description = "투두 제목", example = "단어 암기") String title,
    @Schema(description = "생성된 투두 ID. ONE_TIME만 해당. RECURRING이면 null.", example = "101")
        Long todoId,
    @Schema(description = "생성된 루틴 ID. RECURRING만 해당. ONE_TIME이면 null.", example = "null")
        Long routineId) {

  public static CreatedTodoItem ofTodo(Long todoId, String title) {
    return new CreatedTodoItem("ONE_TIME", title, todoId, null);
  }

  public static CreatedTodoItem ofRoutine(Long routineId, String title) {
    return new CreatedTodoItem("RECURRING", title, null, routineId);
  }
}
