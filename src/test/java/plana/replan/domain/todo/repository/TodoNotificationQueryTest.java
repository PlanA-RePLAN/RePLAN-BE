package plana.replan.domain.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.config.TestFirebaseConfig;

@SpringBootTest
@Transactional
@Import(TestFirebaseConfig.class)
class TodoNotificationQueryTest {

  @Autowired private TodoRepository todoRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ReplanRepository replanRepository;

  private User newUser(String email) {
    return userRepository.save(
        User.builder()
            .email(email)
            .nickname("n")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build());
  }

  @Test
  @DisplayName("핀 고정 + 미완료 + 활성 + 내일 마감 투두만 찾는다")
  void findPinnedDueBetween() {
    User user = newUser("pinned-query@test.com");
    LocalDate tomorrow = LocalDate.now().plusDays(1);

    // 조회되어야 하는 투두: 핀 고정 + 미완료 + 활성
    Todo pinnedDueTomorrow =
        Todo.builder().title("핀").user(user).isPinned(true).dueDate(tomorrow.atTime(18, 0)).build();
    todoRepository.save(pinnedDueTomorrow);

    // 제외 조건 검증 1: 핀 고정 안 됨 → 조회 제외
    Todo notPinned = Todo.builder().title("일반").user(user).dueDate(tomorrow.atTime(18, 0)).build();
    todoRepository.save(notPinned);

    // 제외 조건 검증 2: 핀 고정 + 완료됨 → isCompleted=false 조건에 걸려 제외
    Todo pinnedCompleted =
        Todo.builder()
            .title("완료핀")
            .user(user)
            .isPinned(true)
            .dueDate(tomorrow.atTime(18, 0))
            .build();
    Todo savedPinnedCompleted = todoRepository.save(pinnedCompleted);
    savedPinnedCompleted.updateCompleted(true, LocalDateTime.now());
    todoRepository.saveAndFlush(savedPinnedCompleted);

    // 제외 조건 검증 3: 핀 고정 + 비활성 → isActive=true 조건에 걸려 제외
    Todo pinnedInactive =
        Todo.builder()
            .title("비활성핀")
            .user(user)
            .isPinned(true)
            .dueDate(tomorrow.atTime(18, 0))
            .build();
    Todo savedPinnedInactive = todoRepository.save(pinnedInactive);
    savedPinnedInactive.deactivate();
    todoRepository.saveAndFlush(savedPinnedInactive);

    List<Todo> result =
        todoRepository.findPinnedDueBetween(
            tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay());

    assertThat(result).extracting(Todo::getTitle).containsExactly("핀");
  }

  @Test
  @DisplayName("어제 마감 + 미완료 + 리플랜 없음 투두만 찾는다")
  void findFailedBetween() {
    User user = newUser("failed-query@test.com");
    LocalDate yesterday = LocalDate.now().minusDays(1);

    // 조회되어야 하는 투두: 리플랜 없음
    Todo failed = Todo.builder().title("실패").user(user).dueDate(yesterday.atTime(10, 0)).build();
    todoRepository.save(failed);

    // 제외 조건 검증: 같은 날 마감이지만 리플랜이 연결됨 → replan IS NULL 조건에 걸려 제외
    Todo failedWithReplan =
        Todo.builder().title("리플랜있음").user(user).dueDate(yesterday.atTime(10, 0)).build();
    Todo savedDecoy = todoRepository.save(failedWithReplan);
    Replan replan =
        replanRepository.save(Replan.builder().todo(savedDecoy).failureReason1("사유").build());
    savedDecoy.linkReplan(replan);
    todoRepository.saveAndFlush(savedDecoy);

    List<Todo> result =
        todoRepository.findFailedBetween(
            yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay());

    assertThat(result).extracting(Todo::getTitle).containsExactly("실패");
  }
}
