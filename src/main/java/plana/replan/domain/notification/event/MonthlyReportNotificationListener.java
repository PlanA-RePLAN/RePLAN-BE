package plana.replan.domain.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class MonthlyReportNotificationListener {

  private final UserRepository userRepository;
  private final NotificationService notificationService;

  @EventListener
  public void handle(MonthlyReportCreatedEvent event) {
    User user =
        userRepository
            .findById(event.userId())
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    notificationService.send(
        user,
        NotificationType.REPORT_READY,
        "이번 달 리포트가 나왔어요.",
        event.month() + "월 리포트를 확인해보세요.",
        TargetType.REPORT,
        event.reportId());
  }
}
