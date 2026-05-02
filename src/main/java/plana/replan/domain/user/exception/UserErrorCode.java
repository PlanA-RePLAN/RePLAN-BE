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
  LOGIN_FAILED(401, "이메일 또는 비밀번호가 올바르지 않습니다."),
  OAUTH_PROVIDER_CONFLICT(409, "해당 이메일은 이미 다른 방식으로 가입되어 있습니다."),
  GOOGLE_TOKEN_INVALID(401, "Google ID Token 검증에 실패했습니다."),
  NAVER_TOKEN_INVALID(401, "Naver Access Token 검증에 실패했습니다."),
  KAKAO_TOKEN_INVALID(401, "Kakao Access Token 검증에 실패했습니다."),
  INVALID_TEMP_TOKEN(401, "유효하지 않은 임시 토큰입니다."),
  DUPLICATE_NICKNAME(409, "이미 사용 중인 닉네임입니다."),
  INVALID_S3_KEY(400, "유효하지 않은 S3 키입니다."),
  INVALID_FILENAME(400, "유효하지 않은 파일명입니다."),
  UNSUPPORTED_CONTENT_TYPE(400, "지원하지 않는 파일 형식입니다."),
  OAUTH_SERVER_UNAVAILABLE(503, "OAuth 서버와 통신에 실패했습니다.");

  private final int status;
  private final String message;
}
