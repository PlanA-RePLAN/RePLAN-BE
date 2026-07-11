package plana.replan.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.Platform;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.infra.PushResult;
import plana.replan.domain.notification.infra.PushSender;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.notification.repository.NotificationRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private DeviceTokenRepository deviceTokenRepository;
  @Mock private PushSender pushSender;
  @InjectMocks private NotificationService notificationService;

  private User userWithStatsOff() {
    User u =
        User.builder()
            .email("a@a.com")
            .nickname("n")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    u.updateNotificationSettings(true, false, null); // 통계(리포트) 알림 끔
    return u;
  }

  private User userAllOn() {
    return User.builder()
        .email("b@b.com")
        .nickname("n")
        .role(Role.ROLE_USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("설정이 꺼져 있으면 저장도 발송도 하지 않는다")
  void skipsWhenSettingOff() {
    notificationService.send(
        userWithStatsOff(), NotificationType.REPORT_READY, "t", "b", TargetType.REPORT, 1L);

    verify(notificationRepository, never()).save(any());
    verify(pushSender, never()).send(any(), any(), any(), anyMap(), any());
  }

  @Test
  @DisplayName("설정이 켜져 있으면 알림함에 저장하고 토큰마다 발송한다")
  void savesAndSends() {
    User u = userAllOn();
    DeviceToken t1 = DeviceToken.builder().user(u).token("a").platform(Platform.WEB).build();
    DeviceToken t2 = DeviceToken.builder().user(u).token("b").platform(Platform.ANDROID).build();
    given(deviceTokenRepository.findAllByUser(u)).willReturn(List.of(t1, t2));
    given(pushSender.send(any(), any(), any(), anyMap(), any())).willReturn(PushResult.SUCCESS);

    notificationService.send(
        u, NotificationType.TODO_DUE_SOON, "title", "body", TargetType.TODO, 9L);

    verify(notificationRepository).save(any(Notification.class));
    // 토큰의 플랫폼이 그대로 전달돼야 한다(웹/네이티브 분기의 근거).
    verify(pushSender).send(eq("a"), eq("title"), eq("body"), anyMap(), eq(Platform.WEB));
    verify(pushSender).send(eq("b"), eq("title"), eq("body"), anyMap(), eq(Platform.ANDROID));
  }

  @Test
  @DisplayName("죽은 토큰 응답을 받으면 그 토큰을 삭제한다")
  void deletesDeadToken() {
    User u = userAllOn();
    DeviceToken dead = DeviceToken.builder().user(u).token("dead").platform(Platform.WEB).build();
    given(deviceTokenRepository.findAllByUser(u)).willReturn(List.of(dead));
    given(pushSender.send(any(), any(), any(), anyMap(), any())).willReturn(PushResult.DEAD_TOKEN);

    notificationService.send(
        u, NotificationType.TODO_DUE_SOON, "title", "body", TargetType.TODO, 9L);

    verify(deviceTokenRepository).delete(dead);
  }
}
