package plana.replan.domain.todo.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum TodoErrorCode implements ErrorCode {
  TODO_NOT_FOUND(404, "투두를 찾을 수 없습니다."),
  INVALID_FILTER(400, "유효하지 않은 필터 값입니다. (all, day, week, month 중 하나)"),
  INVALID_SORT(400, "유효하지 않은 정렬 값입니다. (priority, dueDate 중 하나)"),
  ROUTINE_TODO_DUE_DATE_CHANGE_NOT_ALLOWED(400, "루틴 투두의 마감일은 변경할 수 없습니다.");

  private final int status;
  private final String message;
}
