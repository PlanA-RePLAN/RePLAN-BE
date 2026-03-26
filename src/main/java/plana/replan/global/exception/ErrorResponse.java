package plana.replan.global.exception;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonPropertyOrder({"status", "code", "message", "detail", "timestamp"})
public class ErrorResponse {

  private int status;
  private String code;
  private String message;
  private String detail;
  private LocalDateTime timestamp;

  public static ErrorResponse of(ErrorCode errorCode) {
    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.name())
        .message(errorCode.getMessage())
        .detail(null)
        .timestamp(LocalDateTime.now())
        .build();
  }

  public static ErrorResponse of(ErrorCode errorCode, String detail) {
    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.name())
        .message(errorCode.getMessage())
        .detail(detail)
        .timestamp(LocalDateTime.now())
        .build();
  }
}
