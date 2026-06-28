package plana.replan.domain.notification.event;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MonthlyReportNotificationListenerTest {

  @Mock private UserRepository userRepository;
  @Mock private NotificationService notificationService;
  @InjectMocks private MonthlyReportNotificationListener listener;

  @Test
  @DisplayName("리포트 생성 이벤트를 받으면 리포트 도착 알림을 보낸다")
  void handle() {
    User u =
        User.builder()
            .email("a@a.com")
            .nickname("n")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    listener.handle(new MonthlyReportCreatedEvent(1L, 55L, 6));

    verify(notificationService)
        .send(
            eq(u),
            eq(NotificationType.REPORT_READY),
            eq("이번 달 리포트가 나왔어요."),
            eq("6월 리포트를 확인해보세요."),
            eq(TargetType.REPORT),
            eq(55L));
  }
}
