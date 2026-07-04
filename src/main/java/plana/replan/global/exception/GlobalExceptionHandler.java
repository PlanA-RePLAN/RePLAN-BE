package plana.replan.global.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import plana.replan.global.common.ApiResult;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResult<?>> handleCustomException(CustomException e) {
    String ref = UUID.randomUUID().toString().substring(0, 8);
    log.error("[{}] CustomException", ref, e);
    int status = e.getErrorCode().getStatus();
    return ResponseEntity.status(status)
        .body(ApiResult.error(status, ErrorDetail.of(e.getErrorCode(), "(ref: " + ref + ")")));
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

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResult<?>> handleConstraintViolation(ConstraintViolationException e) {
    String detail =
        e.getConstraintViolations().stream()
            .findFirst()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .orElse(e.getMessage());
    log.error("ConstraintViolationException: {}", detail);
    return ResponseEntity.badRequest()
        .body(ApiResult.error(400, ErrorDetail.of(GlobalErrorCode.INVALID_INPUT, detail)));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResult<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    String detail = e.getName() + ": 값 형식이 올바르지 않습니다";
    log.error("MethodArgumentTypeMismatchException: {}", detail);
    return ResponseEntity.badRequest()
        .body(ApiResult.error(400, ErrorDetail.of(GlobalErrorCode.INVALID_INPUT, detail)));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResult<?>> handleMissingParameter(
      MissingServletRequestParameterException e) {
    String detail = e.getParameterName() + ": 필수 파라미터입니다";
    log.error("MissingServletRequestParameterException: {}", detail);
    return ResponseEntity.badRequest()
        .body(ApiResult.error(400, ErrorDetail.of(GlobalErrorCode.INVALID_INPUT, detail)));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResult<?>> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e) {
    log.error(
        "HttpMessageNotReadableException cause: {}",
        e.getCause() != null ? e.getCause().getClass().getName() : "null");
    String detail = resolveParseDetail(e.getCause());
    log.error("HttpMessageNotReadableException detail: {}", detail);
    return ResponseEntity.badRequest()
        .body(ApiResult.error(400, ErrorDetail.of(GlobalErrorCode.INVALID_INPUT, detail)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<?>> handleException(Exception e) {
    String ref = UUID.randomUUID().toString().substring(0, 8);
    log.error("[{}] UnhandledException: ", ref, e);
    return ResponseEntity.internalServerError()
        .body(
            ApiResult.error(
                500,
                ErrorDetail.of(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. (ref: " + ref + ")")));
  }

  private String resolveParseDetail(Throwable cause) {
    if (cause instanceof StreamReadException) {
      return "JSON 형식이 올바르지 않습니다";
    }
    if (cause instanceof InvalidFormatException ife) {
      String path =
          ife.getPath().stream()
              .map(
                  (JacksonException.Reference ref) ->
                      ref.getPropertyName() != null
                          ? ref.getPropertyName()
                          : "[" + ref.getIndex() + "]")
              .collect(Collectors.joining("."));
      return path.isBlank() ? "값 형식이 올바르지 않습니다" : path + ": 값 형식이 올바르지 않습니다";
    }
    if (cause instanceof MismatchedInputException mie) {
      String path =
          mie.getPath().stream()
              .map(
                  (JacksonException.Reference ref) ->
                      ref.getPropertyName() != null
                          ? ref.getPropertyName()
                          : "[" + ref.getIndex() + "]")
              .collect(Collectors.joining("."));
      return path.isBlank() ? "입력 형식이 올바르지 않습니다" : path + ": 입력 형식이 올바르지 않습니다";
    }
    return "요청 본문을 읽을 수 없습니다";
  }
}
