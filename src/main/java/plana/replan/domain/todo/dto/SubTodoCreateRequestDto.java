package plana.replan.domain.todo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SubTodoCreateRequestDto {

  @NotBlank(message = "제목은 필수입니다.")
  private String title;
}
