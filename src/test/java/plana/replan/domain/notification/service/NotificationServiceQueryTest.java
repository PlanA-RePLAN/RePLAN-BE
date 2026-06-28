package plana.replan.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.notification.dto.NotificationListResponse;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.notification.repository.NotificationRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceQueryTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private DeviceTokenRepository deviceTokenRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private NotificationService notificationService;

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("n")
        .role(Role.ROLE_USER)
        .provider(Provider.LOCAL)
        .build();
  }

  private Notification noti(long id) {
    Notification n =
        Notification.builder()
            .user(user())
            .type(NotificationType.TODO_DUE_SOON)
            .title("t")
            .body("b")
            .targetType(TargetType.TODO)
            .targetId(1L)
            .build();
    ReflectionTestUtils.setField(n, "id", id);
    return n;
  }

  @Test
  @DisplayName("size+1 개가 오면 hasNext=true 이고 마지막 1개는 잘라낸다")
  void listHasNext() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(java.util.Optional.of(u));
    given(notificationRepository.findPage(eq(u), eq(Long.MAX_VALUE), any(Pageable.class)))
        .willReturn(List.of(noti(10), noti(9), noti(8))); // size=2 요청 → 3개 반환

    NotificationListResponse res = notificationService.getList(1L, null, null, 2);

    assertThat(res.items()).hasSize(2);
    assertThat(res.hasNext()).isTrue();
    assertThat(res.nextCursor()).isEqualTo(9L);
  }

  @Test
  @DisplayName("내 알림이 아니면 읽음 처리 시 예외")
  void markReadNotFound() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(java.util.Optional.of(u));
    given(notificationRepository.findByIdAndUser(99L, u)).willReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> notificationService.markRead(1L, 99L))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getMessage());
  }
}
