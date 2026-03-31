package plana.replan.domain.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
  USER_NOT_FOUND(404, "유저를 찾을 수 없습니다."),
  DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다."),
  INVALID_PASSWORD(401, "비밀번호가 일치하지 않습니다."),
  LOGIN_FAILED(401, "이메일 또는 비밀번호가 올바르지 않습니다.");

  private final int status;
  private final String message;
}
