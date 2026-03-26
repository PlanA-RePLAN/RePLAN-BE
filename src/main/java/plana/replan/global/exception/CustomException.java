package plana.replan.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

  private final ErrorCode errorCode; // 인터페이스 타입으로 모든 도메인 ErrorCode 받을 수 있음
  private final String detail;

  public CustomException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.detail = null;
  }

  public CustomException(ErrorCode errorCode, String detail) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.detail = detail;
  }
}
