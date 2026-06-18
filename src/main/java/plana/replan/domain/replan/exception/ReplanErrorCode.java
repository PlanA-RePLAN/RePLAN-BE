package plana.replan.domain.replan.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ReplanErrorCode implements ErrorCode {
  REPLAN_TODO_NOT_FOUND(404, "리플랜 대상 투두를 찾을 수 없습니다."),
  REPLAN_INVALID_REASON(400, "실패 이유는 최소 1개, 최대 3개여야 합니다."),
  REPLAN_INVALID_OPERATION(400, "수락한 작업의 형식이 올바르지 않습니다."),
  REPLAN_GEMINI_API_ERROR(502, "AI 추천 서비스에 일시적인 오류가 발생했습니다."),
  REPLAN_GEMINI_PARSE_ERROR(502, "AI 응답을 처리하는 중 오류가 발생했습니다."),
  REPLAN_INVALID_REFRESH_COUNT(400, "새로고침 횟수는 0 이상 3 이하여야 합니다.");

  private final int status;
  private final String message;
}
