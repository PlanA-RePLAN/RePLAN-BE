package plana.replan.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import plana.replan.global.exception.ErrorDetail;

@Getter
@JsonPropertyOrder({"status", "success", "data", "error"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

  private final int status;
  private final boolean success;
  private final T data;
  private final ErrorDetail error;

  private ApiResult(int status, T data) {
    this.status = status;
    this.success = true;
    this.data = data;
    this.error = null;
  }

  private ApiResult(int status, ErrorDetail error) {
    this.status = status;
    this.success = false;
    this.data = null;
    this.error = error;
  }

  public static <T> ApiResult<T> ok(T data) {
    return new ApiResult<>(200, data);
  }

  public static ApiResult<Void> ok() {
    return new ApiResult<>(200, (Void) null);
  }

  public static ApiResult<Void> error(int status, ErrorDetail error) {
    return new ApiResult<>(status, error);
  }
}
