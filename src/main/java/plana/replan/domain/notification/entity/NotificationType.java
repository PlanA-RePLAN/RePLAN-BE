package plana.replan.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
  TODO_DUE_SOON(NotificationCategory.TODO),
  TODO_FAILED_REPLAN(NotificationCategory.TODO),
  REPORT_READY(NotificationCategory.STATS);

  private final NotificationCategory category;
}
