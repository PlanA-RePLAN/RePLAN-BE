package plana.replan.domain.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;

@Service
@RequiredArgsConstructor
public class NotificationTriggerService {

  private static final String DUE_SOON_BODY = "주요 투두로 설정한 투두의 마감 시간이 하루 남았어요.";
  private static final String FAILED_BODY = "실패한 투두의 리플랜을 진행해보세요.";

  private final TodoRepository todoRepository;
  private final NotificationService notificationService;
  private final Clock clock;

  /** 내일 마감인 핀 고정 투두마다 마감 임박 알림 1건씩. */
  public void sendDueSoon() {
    LocalDate today = LocalDate.now(clock);
    LocalDateTime start = today.plusDays(1).atStartOfDay();
    LocalDateTime end = today.plusDays(2).atStartOfDay();

    for (Todo todo : todoRepository.findPinnedDueBetween(start, end)) {
      notificationService.send(
          todo.getUser(),
          NotificationType.TODO_DUE_SOON,
          "'" + todo.getTitle() + "' 투두",
          DUE_SOON_BODY,
          TargetType.TODO,
          todo.getId());
    }
  }

  /** 어제 마감 지나고 못 끝낸 투두를 사용자별 개수로 묶어 요약 1건씩. */
  public void sendFailedReplan() {
    LocalDate today = LocalDate.now(clock);
    LocalDateTime start = today.minusDays(1).atStartOfDay();
    LocalDateTime end = today.atStartOfDay();

    Map<User, Integer> countByUser = new LinkedHashMap<>();
    for (Todo todo : todoRepository.findFailedBetween(start, end)) {
      countByUser.merge(todo.getUser(), 1, Integer::sum);
    }

    countByUser.forEach(
        (user, count) ->
            notificationService.send(
                user,
                NotificationType.TODO_FAILED_REPLAN,
                "오늘 실패한 투두 " + count + "개 있어요.",
                FAILED_BODY,
                TargetType.REPLAN,
                null));
  }
}
