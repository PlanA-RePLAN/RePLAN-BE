package plana.replan.domain.notification.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.domain.notification.service.DeviceTokenService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/notifications/tokens")
@RequiredArgsConstructor
public class NotificationTokenController implements NotificationTokenControllerDocs {

  private final DeviceTokenService deviceTokenService;

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<Void>> registerToken(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody DeviceTokenRegisterRequest request) {
    deviceTokenService.register(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @DeleteMapping
  public ResponseEntity<ApiResult<Void>> deleteToken(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody DeviceTokenDeleteRequest request) {
    deviceTokenService.delete(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }
}
