package plana.replan.domain.notification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.notification.dto.NotificationListResponse;
import plana.replan.domain.notification.dto.NotificationResponse;
import plana.replan.domain.notification.dto.UnreadCountResponse;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationCategory;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.infra.PushResult;
import plana.replan.domain.notification.infra.PushSender;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.notification.repository.NotificationRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final DeviceTokenRepository deviceTokenRepository;
  private final PushSender pushSender;
  private final UserRepository userRepository;

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

  @Transactional(readOnly = true)
  public NotificationListResponse getList(
      Long userId, NotificationCategory category, Long cursor, int size) {
    User user = findUser(userId);
    int safeSize = Math.min(Math.max(size, 1), 100);
    long effectiveCursor = cursor == null ? Long.MAX_VALUE : cursor;
    Pageable pageable = PageRequest.of(0, safeSize + 1);

    List<Notification> rows =
        category == null
            ? notificationRepository.findPage(user, effectiveCursor, pageable)
            : notificationRepository.findPageByCategory(user, category, effectiveCursor, pageable);

    boolean hasNext = rows.size() > safeSize;
    List<Notification> page = hasNext ? rows.subList(0, safeSize) : rows;
    Long nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).getId();

    return new NotificationListResponse(
        page.stream().map(NotificationResponse::from).toList(),
        hasNext ? nextCursor : null,
        hasNext);
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse getUnreadCount(Long userId) {
    User user = findUser(userId);
    return new UnreadCountResponse(notificationRepository.countByUserAndIsReadFalse(user));
  }

  @Transactional
  public void markRead(Long userId, Long notificationId) {
    User user = findUser(userId);
    Notification n =
        notificationRepository
            .findByIdAndUser(notificationId, user)
            .orElseThrow(() -> new CustomException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    n.markRead();
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }

  private boolean isEnabled(User user, NotificationType type) {
    return switch (type) {
      case TODO_DUE_SOON -> user.isNotifyTodoDue();
      case TODO_FAILED_REPLAN -> user.isNotifyTodoFailed();
      case REPORT_READY -> user.isNotifyReport();
    };
  }
}
