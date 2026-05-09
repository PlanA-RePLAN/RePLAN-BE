package plana.replan.domain.todo.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TodoCreateRequestDto {

  @NotBlank(message = "제목은 필수입니다.")
  private String title;

  private LocalDateTime dueDate;

  private Long tagId;
}
