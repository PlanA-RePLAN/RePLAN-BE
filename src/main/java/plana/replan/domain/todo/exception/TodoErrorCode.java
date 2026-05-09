package plana.replan.domain.todo.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum TodoErrorCode implements ErrorCode {
  TODO_NOT_FOUND(404, "투두를 찾을 수 없습니다.");

  private final int status;
  private final String message;
}
