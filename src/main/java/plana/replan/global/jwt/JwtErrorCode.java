package plana.replan.global.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum JwtErrorCode implements ErrorCode {
  INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
  EXPIRED_TOKEN(401, "만료된 토큰입니다."),
  UNSUPPORTED_TOKEN(401, "지원하지 않는 토큰입니다."),
  EMPTY_TOKEN(401, "토큰이 없습니다.");

  private final int status;
  private final String message;
}
