package plana.replan.domain.notification.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
  TOKEN_NOT_FOUND(404, "등록된 기기 토큰을 찾을 수 없습니다."),
  NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다.");

  private final int status;
  private final String message;
}
