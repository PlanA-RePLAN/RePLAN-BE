package plana.replan.domain.notification.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import plana.replan.domain.notification.service.NotificationTriggerService;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

  private final NotificationTriggerService notificationTriggerService;

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void runDailyNotifications() {
    // 한 알림 종류가 실패해도 다른 종류는 계속 발송되도록 각각 독립 실행한다.
    runSafely("마감 임박", notificationTriggerService::sendDueSoon);
    runSafely("실패 리플랜", notificationTriggerService::sendFailedReplan);
  }

  private void runSafely(String name, Runnable task) {
    try {
      task.run();
    } catch (Exception e) {
      log.error("알림 트리거 실패 - {}", name, e);
    }
  }
}
