package plana.replan.domain.routine.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum RoutineErrorCode implements ErrorCode {
  ROUTINE_INVALID_DATE(400, "유효하지 않은 반복 날짜입니다."),
  ROUTINE_NOT_FOUND(404, "루틴을 찾을 수 없습니다."),
  ROUTINE_INVALID_TARGET(400, "이 API는 해당 루틴 종류(엄마/하위)에만 사용할 수 있습니다."),
  ROUTINE_OVERRIDE_CANNOT_SKIP_COMPLETED(400, "이미 완료된 Todo가 있는 날짜는 건너뜀 처리할 수 없습니다."),
  ROUTINE_OVERRIDE_SKIPPED(400, "건너뛴 날짜에는 하위 투두를 추가할 수 없습니다."),
  ROUTINE_OVERRIDE_SUBTODO_NOT_FOUND(404, "예약된 하위 투두를 찾을 수 없습니다.");

  private final int status;
  private final String message;
}
