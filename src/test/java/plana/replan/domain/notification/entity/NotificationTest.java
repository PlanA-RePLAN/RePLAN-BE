package plana.replan.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  @DisplayName("타입이 정해지면 카테고리가 자동으로 따라온다")
  void categoryFollowsType() {
    Notification n =
        Notification.builder()
            .user(null)
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
            .user(null)
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
