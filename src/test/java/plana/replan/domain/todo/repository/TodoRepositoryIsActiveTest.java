package plana.replan.domain.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.config.TestFirebaseConfig;

// 이 프로젝트 최초의 repository 통합 테스트.
// test/resources/application.yaml 이 Flyway를 끄고 H2 create-drop을 쓰도록 이미 설정돼 있다.
@SpringBootTest
@Transactional
@Import(TestFirebaseConfig.class)
class TodoRepositoryIsActiveTest {

  @Autowired private TodoRepository todoRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void 전체조회는_비활성_투두를_제외한다() {
    User user =
        userRepository.save(
            User.builder()
                .email("isactive-list@test.com")
                .nickname("n")
                .role(Role.ROLE_USER)
                .provider(Provider.LOCAL)
                .build());

    todoRepository.save(Todo.builder().title("활성").user(user).build());
    Todo inactive = todoRepository.save(Todo.builder().title("비활성").user(user).build());
    inactive.deactivate();
    todoRepository.saveAndFlush(inactive);

    List<Todo> result = todoRepository.findActiveTodosForUser(user);

    assertThat(result).extracting(Todo::getTitle).containsExactly("활성");
  }

  @Test
  void 통계조회는_비활성_투두를_포함한다() {
    User user =
        userRepository.save(
            User.builder()
                .email("isactive-stats@test.com")
                .nickname("m")
                .role(Role.ROLE_USER)
                .provider(Provider.LOCAL)
                .build());

    Todo overdueInactive =
        todoRepository.save(
            Todo.builder()
                .title("실패원본")
                .dueDate(LocalDateTime.of(2026, 6, 10, 10, 0))
                .user(user)
                .build());
    overdueInactive.deactivate();
    todoRepository.saveAndFlush(overdueInactive);

    List<Todo> result =
        todoRepository.findMonthlyTodos(
            user, LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));

    assertThat(result).extracting(Todo::getTitle).contains("실패원본");
  }
}
