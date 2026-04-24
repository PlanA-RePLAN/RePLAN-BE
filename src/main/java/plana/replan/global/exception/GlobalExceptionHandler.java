package plana.replan.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import plana.replan.global.common.ApiResult;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResult<?>> handleCustomException(CustomException e) {
    log.error("CustomException: {}", e.getMessage());
    int status = e.getErrorCode().getStatus();
    return ResponseEntity.status(status)
        .body(ApiResult.error(status, ErrorDetail.of(e.getErrorCode(), e.getDetail())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResult<?>> handleValidException(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse(e.getMessage());
    log.error("ValidationException: {}", detail);
    return ResponseEntity.badRequest()
        .body(ApiResult.error(400, ErrorDetail.of(GlobalErrorCode.INVALID_INPUT, detail)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<?>> handleException(Exception e) {
    log.error("UnhandledException: ", e);
    return ResponseEntity.internalServerError()
        .body(ApiResult.error(500, ErrorDetail.of(GlobalErrorCode.INTERNAL_SERVER_ERROR)));
  }
}
