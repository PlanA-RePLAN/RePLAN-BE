package plana.replan.global.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorDetail {

  private String code;
  private String message;
  private String detail;

  public static ErrorDetail of(ErrorCode errorCode) {
    return new ErrorDetail(errorCode.name(), errorCode.getMessage(), null);
  }

  public static ErrorDetail of(ErrorCode errorCode, String detail) {
    return new ErrorDetail(errorCode.name(), errorCode.getMessage(), detail);
  }
}
