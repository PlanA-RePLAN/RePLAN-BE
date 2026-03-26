package plana.replan.global.exception;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
    log.error("CustomException: {}", e.getMessage());
    return ResponseEntity.status(e.getErrorCode().getStatus())
        .body(ErrorResponse.of(e.getErrorCode(), e.getDetail()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidException(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.builder()
                .status(400)
                .code("INVALID_INPUT")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("UnhandledException: ", e);
    return ResponseEntity.internalServerError()
        .body(ErrorResponse.of(GlobalErrorCode.INTERNAL_SERVER_ERROR));
  }
}
