package plana.replan.domain.notification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.infra.PushResult;
import plana.replan.domain.notification.infra.PushSender;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.notification.repository.NotificationRepository;
import plana.replan.domain.user.entity.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final DeviceTokenRepository deviceTokenRepository;
  private final PushSender pushSender;

  @Transactional
  public void send(
      User user,
      NotificationType type,
      String title,
      String body,
      TargetType targetType,
      Long targetId) {
    if (!isEnabled(user, type)) {
      return;
    }

    notificationRepository.save(
        Notification.builder()
            .user(user)
            .type(type)
            .title(title)
            .body(body)
            .targetType(targetType)
            .targetId(targetId)
            .build());

    Map<String, String> data = new HashMap<>();
    data.put("type", type.name());
    data.put("targetType", targetType == null ? "" : targetType.name());
    data.put("targetId", targetId == null ? "" : String.valueOf(targetId));

    List<DeviceToken> tokens = deviceTokenRepository.findAllByUser(user);
    for (DeviceToken token : tokens) {
      PushResult result;
      try {
        result = pushSender.send(token.getToken(), title, body, data);
      } catch (Exception e) {
        // 푸시 실패가 알림함 저장/다음 처리를 막으면 안 된다.
        log.warn("푸시 발송 중 예외 - tokenId={}", token.getId(), e);
        continue;
      }
      if (result == PushResult.DEAD_TOKEN) {
        deviceTokenRepository.delete(token);
      }
    }
  }

  private boolean isEnabled(User user, NotificationType type) {
    return switch (type) {
      case TODO_DUE_SOON -> user.isNotifyTodoDue();
      case TODO_FAILED_REPLAN -> user.isNotifyTodoFailed();
      case REPORT_READY -> user.isNotifyReport();
    };
  }
}
