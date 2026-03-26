package plana.replan.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

  // 공통 에러
  INVALID_INPUT(400, "잘못된 입력입니다."),
  UNAUTHORIZED(401, "인증이 필요합니다."),
  FORBIDDEN(403, "권한이 없습니다."),
  NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
  INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다.");

  private final int status;
  private final String message;
}
