package plana.replan.domain.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.notification.service.NotificationTriggerService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/notifications/dev")
@RequiredArgsConstructor
@Profile("local")
public class NotificationDevController {

  private final NotificationTriggerService notificationTriggerService;

  @PostMapping("/trigger-due-soon")
  public ResponseEntity<ApiResult<Void>> triggerDueSoon() {
    notificationTriggerService.sendDueSoon();
    return ResponseEntity.ok(ApiResult.ok());
  }

  @PostMapping("/trigger-failed-replan")
  public ResponseEntity<ApiResult<Void>> triggerFailedReplan() {
    notificationTriggerService.sendFailedReplan();
    return ResponseEntity.ok(ApiResult.ok());
  }
}
