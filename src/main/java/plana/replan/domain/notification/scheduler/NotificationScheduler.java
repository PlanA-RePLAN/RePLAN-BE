package plana.replan.domain.notification.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import plana.replan.domain.notification.service.NotificationTriggerService;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

  private final NotificationTriggerService notificationTriggerService;

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void runDailyNotifications() {
    notificationTriggerService.sendDueSoon();
    notificationTriggerService.sendFailedReplan();
  }
}
