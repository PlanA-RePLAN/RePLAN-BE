package plana.replan.domain.routine.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum RoutineErrorCode implements ErrorCode {
  ROUTINE_INVALID_DATE(400, "유효하지 않은 반복 날짜입니다.");

  private final int status;
  private final String message;
}
