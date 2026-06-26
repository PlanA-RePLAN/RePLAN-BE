package plana.replan.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

class NotificationTest {

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("n")
        .role(Role.ROLE_USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("타입이 정해지면 카테고리가 자동으로 따라온다")
  void categoryFollowsType() {
    Notification n =
        Notification.builder()
            .user(user())
            .type(NotificationType.REPORT_READY)
            .title("t")
            .body("b")
            .targetType(TargetType.REPORT)
            .targetId(5L)
            .build();

    assertThat(n.getCategory()).isEqualTo(NotificationCategory.STATS);
    assertThat(n.isRead()).isFalse();
  }

  @Test
  @DisplayName("읽음 처리하면 isRead 가 true 가 된다")
  void markRead() {
    Notification n =
        Notification.builder()
            .user(user())
            .type(NotificationType.TODO_DUE_SOON)
            .title("t")
            .body("b")
            .targetType(TargetType.TODO)
            .targetId(1L)
            .build();

    n.markRead();

    assertThat(n.isRead()).isTrue();
  }
}
