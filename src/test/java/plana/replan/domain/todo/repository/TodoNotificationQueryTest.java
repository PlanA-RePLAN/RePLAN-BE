package plana.replan.domain.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;

@SpringBootTest
@Transactional
class TodoNotificationQueryTest {

  @MockitoBean private FirebaseApp firebaseApp;
  @MockitoBean private FirebaseMessaging firebaseMessaging;

  @Autowired private TodoRepository todoRepository;
  @Autowired private UserRepository userRepository;

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
  @DisplayName("핀 고정 + 미완료 + 내일 마감 투두만 찾는다")
  void findPinnedDueBetween() {
    User user = newUser("pinned-query@test.com");
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    Todo pinnedDueTomorrow =
        Todo.builder().title("핀").user(user).isPinned(true).dueDate(tomorrow.atTime(18, 0)).build();
    Todo notPinned = Todo.builder().title("일반").user(user).dueDate(tomorrow.atTime(18, 0)).build();
    todoRepository.save(pinnedDueTomorrow);
    todoRepository.save(notPinned);

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
    Todo failed = Todo.builder().title("실패").user(user).dueDate(yesterday.atTime(10, 0)).build();
    todoRepository.save(failed);

    List<Todo> result =
        todoRepository.findFailedBetween(
            yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay());

    assertThat(result).extracting(Todo::getTitle).containsExactly("실패");
  }
}
