package plana.replan.domain.notification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportNotificationListener {

  private final UserRepository userRepository;
  private final NotificationService notificationService;

  @EventListener
  public void handle(MonthlyReportCreatedEvent event) {
    // 알림은 부가 기능이므로 사용자를 찾지 못해도 예외로 리포트 배치 흐름을 끊지 않고 건너뛴다.
    User user = userRepository.findById(event.userId()).orElse(null);
    if (user == null) {
      log.warn("리포트 알림 대상 사용자를 찾을 수 없어 건너뜀 - userId={}", event.userId());
      return;
    }
    notificationService.send(
        user,
        NotificationType.REPORT_READY,
        "이번 달 리포트가 나왔어요.",
        event.month() + "월 리포트를 확인해보세요.",
        TargetType.REPORT,
        event.reportId());
  }
}
