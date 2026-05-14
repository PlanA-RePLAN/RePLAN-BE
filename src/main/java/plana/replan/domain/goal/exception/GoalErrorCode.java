package plana.replan.domain.goal.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum GoalErrorCode implements ErrorCode {
  GOAL_NOT_FOUND(404, "목표를 찾을 수 없습니다."),
  GOAL_ACCESS_DENIED(403, "본인의 목표만 삭제할 수 있습니다."),
  GOAL_INVALID_FILTER(400, "월별 조회 시 연도(year)는 필수입니다."),
  GOAL_INVALID_MONTH(400, "월은 1 이상 12 이하여야 합니다.");

  private final int status;
  private final String message;
}
