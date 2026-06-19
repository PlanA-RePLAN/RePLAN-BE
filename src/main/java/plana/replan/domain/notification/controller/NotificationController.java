package plana.replan.domain.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.notification.dto.NotificationListResponse;
import plana.replan.domain.notification.dto.NotificationSettingResponse;
import plana.replan.domain.notification.dto.NotificationSettingUpdateRequest;
import plana.replan.domain.notification.dto.UnreadCountResponse;
import plana.replan.domain.notification.entity.NotificationCategory;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.domain.notification.service.NotificationSettingService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerDocs {

  private final NotificationService notificationService;
  private final NotificationSettingService notificationSettingService;

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<NotificationListResponse>> getNotifications(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) NotificationCategory category,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(
        ApiResult.ok(notificationService.getList(userId, category, cursor, size)));
  }

  @Override
  @GetMapping("/unread-count")
  public ResponseEntity<ApiResult<UnreadCountResponse>> getUnreadCount(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(notificationService.getUnreadCount(userId)));
  }

  @Override
  @PatchMapping("/{notificationId}/read")
  public ResponseEntity<ApiResult<Void>> readOne(
      @AuthenticationPrincipal Long userId, @PathVariable Long notificationId) {
    notificationService.markRead(userId, notificationId);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @PatchMapping("/read-all")
  public ResponseEntity<ApiResult<Void>> readAll(@AuthenticationPrincipal Long userId) {
    notificationService.markAllRead(userId);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @GetMapping("/settings")
  public ResponseEntity<ApiResult<NotificationSettingResponse>> getSettings(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(notificationSettingService.get(userId)));
  }

  @Override
  @PatchMapping("/settings")
  public ResponseEntity<ApiResult<NotificationSettingResponse>> updateSettings(
      @AuthenticationPrincipal Long userId, @RequestBody NotificationSettingUpdateRequest request) {
    return ResponseEntity.ok(ApiResult.ok(notificationSettingService.update(userId, request)));
  }
}
