package plana.replan.global.exception;

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

    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse(e.getMessage());
    log.error("ValidationException: {}", detail);

    return ResponseEntity.badRequest()
        .body(ErrorResponse.of(GlobalErrorCode.INVALID_INPUT, detail));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("UnhandledException: ", e);
    return ResponseEntity.internalServerError()
        .body(ErrorResponse.of(GlobalErrorCode.INTERNAL_SERVER_ERROR));
  }
}
