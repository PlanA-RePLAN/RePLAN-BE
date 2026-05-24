package plana.replan.domain.todo.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.todo.entity.Todo;

@Getter
@AllArgsConstructor
public class TodoResponseDto {

  private Long todoId;
  private String title;
  private LocalDateTime dueDate;
  private boolean isCompleted;
  private Long tagId;
  private Long parentId;

  public static TodoResponseDto from(Todo todo) {
    return new TodoResponseDto(
        todo.getId(),
        todo.getTitle(),
        todo.getDueDate(),
        todo.isCompleted(),
        todo.getTag() != null ? todo.getTag().getId() : null,
        todo.getParent() != null ? todo.getParent().getId() : null);
  }
}
