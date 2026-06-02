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
  GOAL_INVALID_MONTH(400, "월은 1 이상 12 이하여야 합니다."),
  TODO_DUE_TIME_WITHOUT_DATE(400, "dueTime을 설정하려면 dueDate가 필요합니다."),
  GOAL_DUE_TIME_WITHOUT_DATE(400, "목표 기한의 dueTime을 설정하려면 dueDate가 필요합니다."),
  TODO_INVALID_TYPE(400, "투두 유형은 ONE_TIME 또는 RECURRING이어야 합니다."),
  TODO_SUB_TODO_NOT_ALLOWED_FOR_RECURRING(400, "반복형 투두에는 하위 투두를 추가할 수 없습니다."),
  TODO_SUB_ROUTINE_NOT_ALLOWED_FOR_ONE_TIME(400, "단발성 투두에는 하위 루틴을 추가할 수 없습니다."),
  TODO_SUB_ROUTINE_INVALID_TITLE(400, "하위 루틴 제목은 비어있을 수 없습니다."),
  GEMINI_API_ERROR(502, "AI 추천 서비스에 일시적인 오류가 발생했습니다."),
  GEMINI_PARSE_ERROR(502, "AI 응답을 처리하는 중 오류가 발생했습니다.");

  private final int status;
  private final String message;
}
