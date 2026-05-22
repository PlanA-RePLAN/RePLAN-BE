package plana.replan.domain.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "투두 우선순위 변경 요청")
@Getter
@NoArgsConstructor
public class TodoOrderRequestDto {

  @Schema(description = "바로 앞 투두의 ID. 맨 앞으로 이동 시 null", example = "5")
  private Long prevTodoId;

  @Schema(description = "바로 뒤 투두의 ID. 맨 뒤로 이동 시 null", example = "10")
  private Long nextTodoId;
}
