package plana.replan.domain.tag.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum TagErrorCode implements ErrorCode {
  TAG_NOT_FOUND(404, "태그를 찾을 수 없습니다.");

  private final int status;
  private final String message;
}
