package plana.replan.domain.goal.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "생성된 투두 항목")
public record CreatedTodoItem(
    @Schema(description = "투두 유형 (ONE_TIME / RECURRING)", example = "ONE_TIME") String type,
    @Schema(description = "투두 제목", example = "단어 암기") String title,
    @Schema(description = "생성된 투두 ID. ONE_TIME만 해당. RECURRING이면 null.", example = "101")
        Long todoId,
    @Schema(description = "생성된 루틴 ID. RECURRING만 해당. ONE_TIME이면 null.", example = "null")
        Long routineId,
    @Schema(
            description =
                "생성된 하위 루틴 ID 목록. 항상 배열로 직렬화되며 null이 아니다. ONE_TIME이거나 subRoutines가 비어있으면 빈 배열.",
            example = "[201, 202]")
        List<Long> subRoutineIds) {

  public static CreatedTodoItem ofTodo(Long todoId, String title) {
    return new CreatedTodoItem("ONE_TIME", title, todoId, null, List.of());
  }

  public static CreatedTodoItem ofRoutine(Long routineId, String title, List<Long> subRoutineIds) {
    return new CreatedTodoItem("RECURRING", title, null, routineId, subRoutineIds);
  }
}
