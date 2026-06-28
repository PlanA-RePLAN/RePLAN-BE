package plana.replan.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationTriggerServiceTest {

  @Mock private TodoRepository todoRepository;
  @Mock private NotificationService notificationService;

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  private NotificationTriggerService service() {
    return new NotificationTriggerService(todoRepository, notificationService, clock);
  }

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("n")
        .role(Role.ROLE_USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("내일 마감 핀 투두마다 마감 임박 알림을 보낸다")
  void sendDueSoon() {
    User u = user();
    Todo t = Todo.builder().title("영단어").user(u).build();
    given(todoRepository.findPinnedDueBetween(any(), any())).willReturn(List.of(t));

    service().sendDueSoon();

    verify(notificationService)
        .send(
            eq(u),
            eq(NotificationType.TODO_DUE_SOON),
            eq("'영단어' 투두"),
            eq("주요 투두로 설정한 투두의 마감 시간이 하루 남았어요."),
            eq(TargetType.TODO),
            any());
  }

  @Test
  @DisplayName("실패 투두는 사용자별 개수로 묶어 요약 1건을 보낸다")
  void sendFailedReplanGrouped() {
    User u = user();
    Todo t1 = Todo.builder().title("a").user(u).build();
    Todo t2 = Todo.builder().title("b").user(u).build();
    given(todoRepository.findFailedBetween(any(), any())).willReturn(List.of(t1, t2));

    service().sendFailedReplan();

    verify(notificationService, times(1))
        .send(
            eq(u),
            eq(NotificationType.TODO_FAILED_REPLAN),
            eq("오늘 실패한 투두 2개 있어요."),
            eq("실패한 투두의 리플랜을 진행해보세요."),
            eq(TargetType.REPLAN),
            isNull());
  }
}
