package plana.replan.domain.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.entity.TagColor;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.SubTodoUpdateRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoDetailResponseDto;
import plana.replan.domain.todo.dto.TodoListResponseDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.dto.TodoUpdateRequestDto;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.exception.TodoErrorCode;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

  @Mock private TodoRepository todoRepository;
  @Mock private UserRepository userRepository;
  @Mock private TagRepository tagRepository;
  @Mock private RoutineRepository routineRepository;

  @InjectMocks private TodoService todoService;

  private User testUser() {
    User user =
        User.builder()
            .email("test@test.com")
            .nickname("테스트")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    return user;
  }

  private Tag testTag(Long id) {
    Tag tag = Tag.builder().title("업무").color(TagColor.BLUE).user(testUser()).build();
    ReflectionTestUtils.setField(tag, "id", id);
    return tag;
  }

  private TodoCreateRequestDto request(String title, LocalDateTime dueDate, Long tagId) {
    TodoCreateRequestDto dto = new TodoCreateRequestDto();
    ReflectionTestUtils.setField(dto, "title", title);
    ReflectionTestUtils.setField(dto, "dueDate", dueDate);
    ReflectionTestUtils.setField(dto, "tagId", tagId);
    return dto;
  }

  @Test
  @DisplayName("userId null: USER_NOT_FOUND 예외, save 미호출")
  void createTodo_nullUserId_throws() {
    assertThatThrownBy(() -> todoService.createTodo(null, request("제목", null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(todoRepository, never()).save(any());
  }

  @Test
  @DisplayName("userId가 DB에 없음: USER_NOT_FOUND 예외, save 미호출")
  void createTodo_userNotFound_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.createTodo(1L, request("제목", null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(todoRepository, never()).save(any());
  }

  @Test
  @DisplayName("tagId가 DB에 없음: TAG_NOT_FOUND 예외, save 미호출")
  void createTodo_tagNotFound_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(tagRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.createTodo(1L, request("제목", null, 99L)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));

    verify(todoRepository, never()).save(any());
  }

  @Test
  @DisplayName("성공 (tagId 없음): 올바른 DTO 반환, isPinned=false, tagId=null, tagRepository 미호출")
  void createTodo_success_withoutTag() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

    TodoResponseDto result = todoService.createTodo(1L, request("제목", null, null));

    assertThat(result.getTitle()).isEqualTo("제목");
    assertThat(result.getDueDate()).isNull();
    assertThat(result.getTagId()).isNull();

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).save(captor.capture());
    assertThat(ReflectionTestUtils.getField(captor.getValue(), "isPinned")).isEqualTo(false);

    verify(tagRepository, never()).findById(any());
  }

  @Test
  @DisplayName("성공 (tagId 있음): 올바른 DTO 반환, dueDate 포함, tagId 포함")
  void createTodo_success_withTag() {
    LocalDateTime dueDate = LocalDateTime.of(2026, 5, 10, 12, 30);
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(tagRepository.findById(5L)).willReturn(Optional.of(testTag(5L)));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

    TodoResponseDto result = todoService.createTodo(1L, request("제목", dueDate, 5L));

    assertThat(result.getTitle()).isEqualTo("제목");
    assertThat(result.getDueDate()).isEqualTo(dueDate);
    assertThat(result.getTagId()).isEqualTo(5L);
  }

  private SubTodoCreateRequestDto subRequest(String title) {
    SubTodoCreateRequestDto dto = new SubTodoCreateRequestDto();
    ReflectionTestUtils.setField(dto, "title", title);
    return dto;
  }

  private Todo testTodo(Long id, User user) {
    Todo todo = Todo.builder().title("부모 투두").user(user).isPinned(false).build();
    ReflectionTestUtils.setField(todo, "id", id);
    return todo;
  }

  @Test
  @DisplayName("userId null: USER_NOT_FOUND 예외, save 미호출")
  void createSubTodo_nullUserId_throws() {
    assertThatThrownBy(() -> todoService.createSubTodo(null, 1L, subRequest("하위 투두")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(todoRepository, never()).save(any());
  }

  @Test
  @DisplayName("userId가 DB에 없음: USER_NOT_FOUND 예외, save 미호출")
  void createSubTodo_userNotFound_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.createSubTodo(1L, 10L, subRequest("하위 투두")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(todoRepository, never()).save(any());
  }

  @Test
  @DisplayName("parentId가 DB에 없음: TODO_NOT_FOUND 예외, save 미호출")
  void createSubTodo_parentNotFound_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(todoRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.createSubTodo(1L, 99L, subRequest("하위 투두")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));

    verify(todoRepository, never()).save(any());
  }

  @Test
  @DisplayName("parent 투두가 다른 유저 소유: TODO_NOT_FOUND 예외, save 미호출")
  void createSubTodo_parentOwnedByOtherUser_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    Todo parentOwnedByOther = testTodo(10L, otherUser);
    given(todoRepository.findById(10L)).willReturn(Optional.of(parentOwnedByOther));

    assertThatThrownBy(() -> todoService.createSubTodo(1L, 10L, subRequest("하위 투두")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));

    verify(todoRepository, never()).save(any());
  }

  @Test
  @DisplayName("성공: 하위 투두 생성, parentId 포함된 DTO 반환")
  void createSubTodo_success() {
    User user = testUser();
    Todo parent = testTodo(10L, user);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findById(10L)).willReturn(Optional.of(parent));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

    TodoResponseDto result = todoService.createSubTodo(1L, 10L, subRequest("하위 투두"));

    assertThat(result.getTitle()).isEqualTo("하위 투두");
    assertThat(result.getParentId()).isEqualTo(10L);
    assertThat(result.getTagId()).isNull();
  }

  private SubTodoUpdateRequestDto updateRequest(String title) {
    SubTodoUpdateRequestDto dto = new SubTodoUpdateRequestDto();
    ReflectionTestUtils.setField(dto, "title", title);
    return dto;
  }

  @Test
  @DisplayName("성공: 저장되는 엔티티에 parent 연결, isPinned=false, tag/goal/routine null")
  void createSubTodo_success_entityProperties() {
    User user = testUser();
    Todo parent = testTodo(10L, user);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findById(10L)).willReturn(Optional.of(parent));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

    todoService.createSubTodo(1L, 10L, subRequest("하위 투두"));

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).save(captor.capture());
    Todo saved = captor.getValue();

    assertThat(ReflectionTestUtils.getField(saved, "isPinned")).isEqualTo(false);
    assertThat(ReflectionTestUtils.getField(saved, "parent")).isSameAs(parent);
    assertThat(ReflectionTestUtils.getField(saved, "tag")).isNull();
    assertThat(ReflectionTestUtils.getField(saved, "goal")).isNull();
    assertThat(ReflectionTestUtils.getField(saved, "routine")).isNull();
  }

  // ── updateSubTodo ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("updateSubTodo - userId null: USER_NOT_FOUND 예외")
  void updateSubTodo_nullUserId_throws() {
    assertThatThrownBy(() -> todoService.updateSubTodo(null, 10L, 43L, updateRequest("수정 제목")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("updateSubTodo - subTodoId가 DB에 없음: TODO_NOT_FOUND 예외")
  void updateSubTodo_subTodoNotFound_throws() {
    given(todoRepository.findById(43L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.updateSubTodo(1L, 10L, 43L, updateRequest("수정 제목")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("updateSubTodo - 다른 유저 소유 하위 투두: TODO_NOT_FOUND 예외")
  void updateSubTodo_ownedByOtherUser_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    User owner = testUser();
    Todo parent = testTodo(10L, owner);
    Todo subTodo =
        Todo.builder().title("하위 투두").user(otherUser).parent(parent).isPinned(false).build();
    ReflectionTestUtils.setField(subTodo, "id", 43L);

    given(todoRepository.findById(43L)).willReturn(Optional.of(subTodo));

    assertThatThrownBy(() -> todoService.updateSubTodo(1L, 10L, 43L, updateRequest("수정 제목")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("updateSubTodo - parentId 불일치: TODO_NOT_FOUND 예외")
  void updateSubTodo_parentMismatch_throws() {
    User user = testUser();
    Todo parent = testTodo(10L, user);
    Todo subTodo = Todo.builder().title("하위 투두").user(user).parent(parent).isPinned(false).build();
    ReflectionTestUtils.setField(subTodo, "id", 43L);

    given(todoRepository.findById(43L)).willReturn(Optional.of(subTodo));

    assertThatThrownBy(() -> todoService.updateSubTodo(1L, 99L, 43L, updateRequest("수정 제목")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("updateSubTodo - 성공: 수정된 제목 포함 DTO 반환")
  void updateSubTodo_success() {
    User user = testUser();
    Todo parent = testTodo(10L, user);
    Todo subTodo = Todo.builder().title("원래 제목").user(user).parent(parent).isPinned(false).build();
    ReflectionTestUtils.setField(subTodo, "id", 43L);

    given(todoRepository.findById(43L)).willReturn(Optional.of(subTodo));

    TodoResponseDto result = todoService.updateSubTodo(1L, 10L, 43L, updateRequest("수정 제목"));

    assertThat(result.getTitle()).isEqualTo("수정 제목");
    assertThat(result.getParentId()).isEqualTo(10L);
  }

  // ── deleteSubTodo ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("deleteSubTodo - userId null: USER_NOT_FOUND 예외")
  void deleteSubTodo_nullUserId_throws() {
    assertThatThrownBy(() -> todoService.deleteSubTodo(null, 10L, 43L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteSubTodo - subTodoId가 DB에 없음: TODO_NOT_FOUND 예외")
  void deleteSubTodo_subTodoNotFound_throws() {
    given(todoRepository.findById(43L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.deleteSubTodo(1L, 10L, 43L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteSubTodo - 다른 유저 소유 하위 투두: TODO_NOT_FOUND 예외")
  void deleteSubTodo_ownedByOtherUser_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    User owner = testUser();
    Todo parent = testTodo(10L, owner);
    Todo subTodo =
        Todo.builder().title("하위 투두").user(otherUser).parent(parent).isPinned(false).build();
    ReflectionTestUtils.setField(subTodo, "id", 43L);

    given(todoRepository.findById(43L)).willReturn(Optional.of(subTodo));

    assertThatThrownBy(() -> todoService.deleteSubTodo(1L, 10L, 43L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteSubTodo - parentId 불일치: TODO_NOT_FOUND 예외")
  void deleteSubTodo_parentMismatch_throws() {
    User user = testUser();
    Todo parent = testTodo(10L, user);
    Todo subTodo = Todo.builder().title("하위 투두").user(user).parent(parent).isPinned(false).build();
    ReflectionTestUtils.setField(subTodo, "id", 43L);

    given(todoRepository.findById(43L)).willReturn(Optional.of(subTodo));

    assertThatThrownBy(() -> todoService.deleteSubTodo(1L, 99L, 43L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteSubTodo - 성공: soft delete 처리 (deletedAt 설정)")
  void deleteSubTodo_success() {
    User user = testUser();
    Todo parent = testTodo(10L, user);
    Todo subTodo = Todo.builder().title("하위 투두").user(user).parent(parent).isPinned(false).build();
    ReflectionTestUtils.setField(subTodo, "id", 43L);

    given(todoRepository.findById(43L)).willReturn(Optional.of(subTodo));

    todoService.deleteSubTodo(1L, 10L, 43L);

    assertThat(ReflectionTestUtils.getField(subTodo, "deletedAt")).isNotNull();
  }

  // ── getTodos ──────────────────────────────────────────────────────────────

  private Todo activeTodo(Long id, User user) {
    Todo todo = Todo.builder().title("투두 " + id).user(user).isPinned(false).build();
    ReflectionTestUtils.setField(todo, "id", id);
    return todo;
  }

  private Todo activeTodoWithSort(Long id, User user, boolean isPinned, double sortOrder) {
    Todo todo =
        Todo.builder().title("투두 " + id).user(user).isPinned(isPinned).sortOrder(sortOrder).build();
    ReflectionTestUtils.setField(todo, "id", id);
    return todo;
  }

  private Todo activeTodoWithDueDate(Long id, User user, boolean isPinned, LocalDateTime dueDate) {
    Todo todo =
        Todo.builder().title("투두 " + id).user(user).isPinned(isPinned).dueDate(dueDate).build();
    ReflectionTestUtils.setField(todo, "id", id);
    return todo;
  }

  private Todo completedTodo(Long id, User user) {
    Todo todo = Todo.builder().title("완료 투두 " + id).user(user).isPinned(false).build();
    ReflectionTestUtils.setField(todo, "id", id);
    ReflectionTestUtils.setField(todo, "isCompleted", true);
    ReflectionTestUtils.setField(todo, "completedTime", LocalDateTime.now());
    return todo;
  }

  @Test
  @DisplayName("getTodos - userId null: USER_NOT_FOUND 예외")
  void getTodos_nullUserId_throws() {
    assertThatThrownBy(() -> todoService.getTodos(null, "all", "priority"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("getTodos - filter null: INVALID_FILTER 예외")
  void getTodos_nullFilter_throws() {
    assertThatThrownBy(() -> todoService.getTodos(1L, null, "priority"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.INVALID_FILTER));
  }

  @Test
  @DisplayName("getTodos - sort null: INVALID_SORT 예외")
  void getTodos_nullSort_throws() {
    assertThatThrownBy(() -> todoService.getTodos(1L, "all", null))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.INVALID_SORT));
  }

  @Test
  @DisplayName("getTodos - userId가 DB에 없음: USER_NOT_FOUND 예외")
  void getTodos_userNotFound_throws() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.getTodos(99L, "all", "priority"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("getTodos - all 필터: 미완료 투두 전체 반환")
  void getTodos_all_returnsActiveTodos() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosForUser(user))
        .willReturn(List.of(activeTodo(1L, user), activeTodo(2L, user)));

    List<TodoListResponseDto> result = todoService.getTodos(1L, "all", "priority");

    assertThat(result).hasSize(2);
    assertThat(result).extracting("isCompleted").containsOnly(false);
  }

  @Test
  @DisplayName("getTodos - all 필터: 결과 없으면 빈 리스트")
  void getTodos_all_emptyList() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosForUser(user)).willReturn(List.of());

    assertThat(todoService.getTodos(1L, "all", "priority")).isEmpty();
  }

  @Test
  @DisplayName("getTodos - day 필터: 미완료 투두가 완료 투두보다 먼저")
  void getTodos_day_activeTodosBeforeCompleted() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosByDueDateRange(any(), any(), any()))
        .willReturn(List.of(activeTodo(1L, user)));
    given(todoRepository.findCompletedTodosByCompletedTimeRange(any(), any(), any()))
        .willReturn(List.of(completedTodo(2L, user)));

    List<TodoListResponseDto> result = todoService.getTodos(1L, "day", "priority");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).isCompleted()).isFalse();
    assertThat(result.get(1).isCompleted()).isTrue();
  }

  @Test
  @DisplayName("getTodos - week 필터: 완료 투두 조회 미호출")
  void getTodos_week_completedTodosNotQueried() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosByDueDateRange(any(), any(), any()))
        .willReturn(List.of(activeTodo(1L, user)));

    todoService.getTodos(1L, "week", "priority");

    verify(todoRepository, never()).findCompletedTodosByCompletedTimeRange(any(), any(), any());
  }

  @Test
  @DisplayName("getTodos - month 필터: 완료 투두 조회 미호출")
  void getTodos_month_completedTodosNotQueried() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosByDueDateRange(any(), any(), any()))
        .willReturn(List.of(activeTodo(1L, user)));

    todoService.getTodos(1L, "month", "priority");

    verify(todoRepository, never()).findCompletedTodosByCompletedTimeRange(any(), any(), any());
  }

  @Test
  @DisplayName("getTodos - 유효하지 않은 filter: INVALID_FILTER 예외")
  void getTodos_invalidFilter_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(() -> todoService.getTodos(1L, "invalid", "priority"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.INVALID_FILTER));
  }

  @Test
  @DisplayName("getTodos - priority 정렬: sortOrder 오름차순 (pin 여부 무관)")
  void getTodos_prioritySort_bySortOrder() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Todo t1 = activeTodoWithSort(1L, user, false, 1000.0);
    Todo t2 = activeTodoWithSort(2L, user, false, 500.0);
    Todo t3 = activeTodoWithSort(3L, user, true, 9999.0);

    given(todoRepository.findActiveTodosForUser(user)).willReturn(List.of(t1, t3, t2));

    List<TodoListResponseDto> result = todoService.getTodos(1L, "all", "priority");

    assertThat(result).extracting("todoId").containsExactly(2L, 1L, 3L);
  }

  @Test
  @DisplayName("getTodos - dueDate 정렬: 마감일 오름차순, null은 마지막 (pin 여부 무관)")
  void getTodos_dueDateSort_byDueDate_nullsLast() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    LocalDateTime early = LocalDateTime.of(2026, 1, 1, 0, 0);
    LocalDateTime mid = LocalDateTime.of(2026, 6, 1, 0, 0);
    LocalDateTime late = LocalDateTime.of(2026, 12, 31, 0, 0);

    Todo noDate = activeTodoWithDueDate(1L, user, false, null);
    Todo lateDate = activeTodoWithDueDate(2L, user, false, late);
    Todo earlyDate = activeTodoWithDueDate(3L, user, true, early);
    Todo midDate = activeTodoWithDueDate(4L, user, false, mid);

    given(todoRepository.findActiveTodosForUser(user))
        .willReturn(List.of(noDate, lateDate, earlyDate, midDate));

    List<TodoListResponseDto> result = todoService.getTodos(1L, "all", "dueDate");

    assertThat(result).extracting("todoId").containsExactly(3L, 4L, 2L, 1L);
  }

  @Test
  @DisplayName("getTodos - 유효하지 않은 sort: INVALID_SORT 예외")
  void getTodos_invalidSort_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(() -> todoService.getTodos(1L, "all", "invalid"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.INVALID_SORT));
  }

  @Test
  @DisplayName("getTodos - filter 대소문자 무관 처리 (sort는 정확한 값 필요)")
  void getTodos_filterCaseInsensitive() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosForUser(any())).willReturn(List.of());

    assertThatCode(() -> todoService.getTodos(1L, "ALL", "priority")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("getTodos - sort 대소문자 불일치: INVALID_SORT 예외")
  void getTodos_sortCaseMismatch_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(() -> todoService.getTodos(1L, "all", "PRIORITY"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.INVALID_SORT));
  }

  @Test
  @DisplayName("getTodos - 마감일이 지난 미완료 투두: isOverdue=true")
  void getTodos_overdueTodo_isOverdueTrue() {
    User user = testUser();
    Todo overdue = activeTodoWithDueDate(1L, user, false, LocalDateTime.of(2020, 1, 1, 0, 0));

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosForUser(any())).willReturn(List.of(overdue));

    List<TodoListResponseDto> result = todoService.getTodos(1L, "all", "priority");

    assertThat(result.get(0).isOverdue()).isTrue();
  }

  @Test
  @DisplayName("getTodos - 완료된 투두: isOverdue=false")
  void getTodos_completedTodo_isOverdueFalse() {
    User user = testUser();
    Todo completed = completedTodo(1L, user);
    ReflectionTestUtils.setField(completed, "dueDate", LocalDateTime.of(2020, 1, 1, 0, 0));

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosByDueDateRange(any(), any(), any())).willReturn(List.of());
    given(todoRepository.findCompletedTodosByCompletedTimeRange(any(), any(), any()))
        .willReturn(List.of(completed));

    List<TodoListResponseDto> result = todoService.getTodos(1L, "day", "priority");

    assertThat(result.get(0).isOverdue()).isFalse();
  }

  @Test
  @DisplayName("getTodos - dueDate 없는 투두: isOverdue=false")
  void getTodos_noDueDate_isOverdueFalse() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosForUser(any())).willReturn(List.of(activeTodo(1L, user)));

    List<TodoListResponseDto> result = todoService.getTodos(1L, "all", "priority");

    assertThat(result.get(0).isOverdue()).isFalse();
  }

  @Test
  @DisplayName("getTodos - 루틴·태그 정보가 DTO에 올바르게 매핑됨")
  void getTodos_routineAndTagMappedCorrectly() {
    User user = testUser();
    Tag tag = testTag(5L);
    Routine routine =
        Routine.builder().title("루틴").routineType(RoutineType.WEEKLY).user(user).build();
    ReflectionTestUtils.setField(routine, "id", 1L);

    Todo todo =
        Todo.builder().title("투두").user(user).isPinned(false).tag(tag).routine(routine).build();
    ReflectionTestUtils.setField(todo, "id", 1L);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(todoRepository.findActiveTodosForUser(any())).willReturn(List.of(todo));

    TodoListResponseDto dto = todoService.getTodos(1L, "all", "priority").get(0);

    assertThat(dto.getTagId()).isEqualTo(5L);
    assertThat(dto.getTagTitle()).isEqualTo("업무");
    assertThat(dto.getTagColor()).isEqualTo("BLUE");
    assertThat(dto.getRoutineType()).isEqualTo("WEEKLY");
  }

  // ── getTodoDetail ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("존재하지 않는 todoId: TODO_NOT_FOUND 예외")
  void getTodoDetail_notFound_throws() {
    given(todoRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.getTodoDetail(1L, 99L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("다른 유저 소유 투두: TODO_NOT_FOUND 예외")
  void getTodoDetail_otherUserTodo_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    Todo todo = testTodo(1L, otherUser);
    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    assertThatThrownBy(() -> todoService.getTodoDetail(1L, 1L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("성공 (태그 없음, 반복 없음, 하위 투두 없음): 기본 필드 검증")
  void getTodoDetail_success_noTagNoRoutineNoSubTodos() {
    User user = testUser();
    Todo todo = testTodo(1L, user);
    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    plana.replan.domain.todo.dto.TodoDetailResponseDto result = todoService.getTodoDetail(1L, 1L);

    assertThat(result.getTodoId()).isEqualTo(1L);
    assertThat(result.getTitle()).isEqualTo("부모 투두");
    assertThat(result.getTagId()).isNull();
    assertThat(result.getTagTitle()).isNull();
    assertThat(result.getTagColor()).isNull();
    assertThat(result.getRoutineType()).isNull();
    assertThat(result.getSubTodos()).isEmpty();
  }

  @Test
  @DisplayName("성공 (태그, 루틴, 하위 투두 있음): 모든 필드 검증")
  void getTodoDetail_success_withTagRoutineSubTodos() {
    User user = testUser();
    Todo parent = testTodo(1L, user);

    Tag tag = testTag(5L);
    ReflectionTestUtils.setField(parent, "tag", tag);

    Routine routine =
        Routine.builder().title("루틴").routineType(RoutineType.DAILY).user(user).build();
    ReflectionTestUtils.setField(parent, "routine", routine);

    Todo child = testTodo(10L, user);
    ReflectionTestUtils.setField(parent, "children", List.of(child));

    given(todoRepository.findById(1L)).willReturn(Optional.of(parent));

    plana.replan.domain.todo.dto.TodoDetailResponseDto result = todoService.getTodoDetail(1L, 1L);

    assertThat(result.getTagId()).isEqualTo(5L);
    assertThat(result.getTagTitle()).isEqualTo("업무");
    assertThat(result.getTagColor()).isEqualTo("BLUE");
    assertThat(result.getRoutineType()).isEqualTo("DAILY");
    assertThat(result.getSubTodos()).hasSize(1);
    assertThat(result.getSubTodos().get(0).getTodoId()).isEqualTo(10L);
    assertThat(result.getSubTodos().get(0).getTitle()).isEqualTo("부모 투두");
  }

  // ── deleteTodo ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("deleteTodo - userId null: USER_NOT_FOUND 예외")
  void deleteTodo_nullUserId_throws() {
    assertThatThrownBy(() -> todoService.deleteTodo(null, 1L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteTodo - todoId DB에 없음: TODO_NOT_FOUND 예외")
  void deleteTodo_todoNotFound_throws() {
    given(todoRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.deleteTodo(1L, 99L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteTodo - 다른 유저 소유 투두: TODO_NOT_FOUND 예외")
  void deleteTodo_otherUserTodo_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    given(todoRepository.findById(1L)).willReturn(Optional.of(testTodo(1L, otherUser)));

    assertThatThrownBy(() -> todoService.deleteTodo(1L, 1L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteTodo - 하위 투두 ID 직접 전달: TODO_NOT_FOUND 예외")
  void deleteTodo_subTodoId_throws() {
    User user = testUser();
    Todo parent = testTodo(10L, user);
    Todo subTodo = Todo.builder().title("하위 투두").user(user).parent(parent).isPinned(false).build();
    ReflectionTestUtils.setField(subTodo, "id", 43L);

    given(todoRepository.findById(43L)).willReturn(Optional.of(subTodo));

    assertThatThrownBy(() -> todoService.deleteTodo(1L, 43L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteTodo - 성공 (하위 투두 없음): 투두 soft delete")
  void deleteTodo_success_noChildren() {
    User user = testUser();
    Todo todo = testTodo(1L, user);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    todoService.deleteTodo(1L, 1L);

    assertThat(ReflectionTestUtils.getField(todo, "deletedAt")).isNotNull();
  }

  @Test
  @DisplayName("deleteTodo - 성공 (하위 투두 있음): 투두 및 하위 투두 모두 soft delete")
  void deleteTodo_success_withChildren() {
    User user = testUser();
    Todo parent = testTodo(1L, user);
    Todo child1 = testTodo(10L, user);
    Todo child2 = testTodo(11L, user);
    ReflectionTestUtils.setField(parent, "children", List.of(child1, child2));

    given(todoRepository.findById(1L)).willReturn(Optional.of(parent));

    todoService.deleteTodo(1L, 1L);

    assertThat(ReflectionTestUtils.getField(parent, "deletedAt")).isNotNull();
    assertThat(ReflectionTestUtils.getField(child1, "deletedAt")).isNotNull();
    assertThat(ReflectionTestUtils.getField(child2, "deletedAt")).isNotNull();
  }

  @Test
  @DisplayName("deleteTodo - 성공 (루틴 있음): 루틴은 삭제하지 않음")
  void deleteTodo_success_routineNotDeleted() {
    User user = testUser();
    Todo todo = testTodo(1L, user);
    Routine routine = testRoutine(user, RoutineType.DAILY);
    ReflectionTestUtils.setField(todo, "routine", routine);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    todoService.deleteTodo(1L, 1L);

    assertThat(ReflectionTestUtils.getField(todo, "deletedAt")).isNotNull();
    assertThat(ReflectionTestUtils.getField(routine, "deletedAt")).isNull();
  }

  // ── updateTodo ──────────────────────────────────────────────────────────────

  private TodoUpdateRequestDto updateTodoRequest(
      String title,
      LocalDateTime dueDate,
      Long tagId,
      RoutineType routineType,
      Integer routineDate) {
    TodoUpdateRequestDto dto = new TodoUpdateRequestDto();
    ReflectionTestUtils.setField(dto, "title", title);
    ReflectionTestUtils.setField(dto, "dueDate", dueDate);
    ReflectionTestUtils.setField(dto, "tagId", tagId);
    ReflectionTestUtils.setField(dto, "routineType", routineType);
    ReflectionTestUtils.setField(dto, "routineDate", routineDate);
    return dto;
  }

  private Routine testRoutine(User user, RoutineType type) {
    Routine routine = Routine.builder().title("루틴").routineType(type).user(user).build();
    ReflectionTestUtils.setField(routine, "id", 1L);
    return routine;
  }

  @Test
  @DisplayName("updateTodo - userId null: USER_NOT_FOUND 예외")
  void updateTodo_nullUserId_throws() {
    assertThatThrownBy(
            () -> todoService.updateTodo(null, 1L, updateTodoRequest("제목", null, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("updateTodo - todoId DB에 없음: TODO_NOT_FOUND 예외")
  void updateTodo_todoNotFound_throws() {
    given(todoRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> todoService.updateTodo(1L, 99L, updateTodoRequest("제목", null, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("updateTodo - 다른 유저 소유 투두: TODO_NOT_FOUND 예외")
  void updateTodo_otherUserTodo_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    Todo todo = testTodo(1L, otherUser);
    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    assertThatThrownBy(
            () -> todoService.updateTodo(1L, 1L, updateTodoRequest("제목", null, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TodoErrorCode.TODO_NOT_FOUND));
  }

  @Test
  @DisplayName("updateTodo - 존재하지 않는 tagId: TAG_NOT_FOUND 예외")
  void updateTodo_tagNotFound_throws() {
    User user = testUser();
    given(todoRepository.findById(1L)).willReturn(Optional.of(testTodo(1L, user)));
    given(tagRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> todoService.updateTodo(1L, 1L, updateTodoRequest("제목", null, 99L, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));
  }

  @Test
  @DisplayName("updateTodo - WEEKLY + routineDate > 127: ROUTINE_INVALID_DATE 예외")
  void updateTodo_weeklyInvalidDate_throws() {
    User user = testUser();
    given(todoRepository.findById(1L)).willReturn(Optional.of(testTodo(1L, user)));

    assertThatThrownBy(
            () ->
                todoService.updateTodo(
                    1L, 1L, updateTodoRequest("제목", null, null, RoutineType.WEEKLY, 128)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  @Test
  @DisplayName("updateTodo - MONTHLY + routineDate = 0: ROUTINE_INVALID_DATE 예외")
  void updateTodo_monthlyInvalidDate_throws() {
    User user = testUser();
    given(todoRepository.findById(1L)).willReturn(Optional.of(testTodo(1L, user)));

    assertThatThrownBy(
            () ->
                todoService.updateTodo(
                    1L, 1L, updateTodoRequest("제목", null, null, RoutineType.MONTHLY, 0)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  @Test
  @DisplayName("updateTodo - WEEKLY + routineDate null: ROUTINE_INVALID_DATE 예외")
  void updateTodo_weeklyNullDate_throws() {
    User user = testUser();
    given(todoRepository.findById(1L)).willReturn(Optional.of(testTodo(1L, user)));

    assertThatThrownBy(
            () ->
                todoService.updateTodo(
                    1L, 1L, updateTodoRequest("제목", null, null, RoutineType.WEEKLY, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  @Test
  @DisplayName("updateTodo - title 빈 문자열: INVALID_INPUT 예외")
  void updateTodo_blankTitle_throws() {
    User user = testUser();
    given(todoRepository.findById(1L)).willReturn(Optional.of(testTodo(1L, user)));

    assertThatThrownBy(
            () -> todoService.updateTodo(1L, 1L, updateTodoRequest("", null, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  @DisplayName("updateTodo - 성공: title, dueDate, tag 수정 후 반환")
  void updateTodo_success_basic() {
    User user = testUser();
    Todo todo = testTodo(1L, user);
    LocalDateTime newDueDate = LocalDateTime.of(2026, 12, 31, 23, 59);
    Tag tag = testTag(5L);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));
    given(tagRepository.findById(5L)).willReturn(Optional.of(tag));

    TodoDetailResponseDto result =
        todoService.updateTodo(1L, 1L, updateTodoRequest("수정된 제목", newDueDate, 5L, null, null));

    assertThat(result.getTodoId()).isEqualTo(1L);
    assertThat(result.getTitle()).isEqualTo("수정된 제목");
    assertThat(result.getDueDate()).isEqualTo(newDueDate);
    assertThat(result.getTagId()).isEqualTo(5L);
    assertThat(result.getRoutineType()).isNull();
  }

  @Test
  @DisplayName("updateTodo - title null: 기존 제목 유지")
  void updateTodo_success_nullTitleKeepsExisting() {
    User user = testUser();
    Todo todo = testTodo(1L, user);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    TodoDetailResponseDto result =
        todoService.updateTodo(1L, 1L, updateTodoRequest(null, null, null, null, null));

    assertThat(result.getTitle()).isEqualTo("부모 투두");
  }

  @Test
  @DisplayName("updateTodo - 성공: dueDate=null, tagId=null → 마감일·태그 제거")
  void updateTodo_success_clearDueDateAndTag() {
    User user = testUser();
    Todo todo = testTodo(1L, user);
    ReflectionTestUtils.setField(todo, "dueDate", LocalDateTime.of(2026, 1, 1, 0, 0));
    ReflectionTestUtils.setField(todo, "tag", testTag(3L));

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    TodoDetailResponseDto result =
        todoService.updateTodo(1L, 1L, updateTodoRequest("제목", null, null, null, null));

    assertThat(result.getDueDate()).isNull();
    assertThat(result.getTagId()).isNull();
  }

  @Test
  @DisplayName("updateTodo - 루틴 있음 + routineType=null: 루틴 soft delete, todo.routine=null")
  void updateTodo_success_removeRoutine() {
    User user = testUser();
    Todo todo = testTodo(1L, user);
    Routine routine = testRoutine(user, RoutineType.DAILY);
    ReflectionTestUtils.setField(todo, "routine", routine);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    TodoDetailResponseDto result =
        todoService.updateTodo(1L, 1L, updateTodoRequest("제목", null, null, null, null));

    assertThat(ReflectionTestUtils.getField(routine, "deletedAt")).isNotNull();
    assertThat(result.getRoutineType()).isNull();
  }

  @Test
  @DisplayName("updateTodo - 루틴 있음 + 유형 변경 (DAILY→WEEKLY): 기존 루틴 업데이트")
  void updateTodo_success_changeRoutineType() {
    User user = testUser();
    Todo todo = testTodo(1L, user);
    Routine routine = testRoutine(user, RoutineType.DAILY);
    ReflectionTestUtils.setField(todo, "routine", routine);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    TodoDetailResponseDto result =
        todoService.updateTodo(
            1L, 1L, updateTodoRequest("수정된 제목", null, null, RoutineType.WEEKLY, 5));

    assertThat(routine.getRoutineType()).isEqualTo(RoutineType.WEEKLY);
    assertThat(routine.getRoutineDate()).isEqualTo(5);
    assertThat(routine.getTitle()).isEqualTo("수정된 제목");
    assertThat(result.getRoutineType()).isEqualTo("WEEKLY");
    verify(routineRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateTodo - 루틴 없음 + routineType=DAILY: 새 루틴 생성 및 투두에 연결")
  void updateTodo_success_addRoutine() {
    User user = testUser();
    Todo todo = testTodo(1L, user);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    TodoDetailResponseDto result =
        todoService.updateTodo(
            1L, 1L, updateTodoRequest("제목", null, null, RoutineType.DAILY, null));

    ArgumentCaptor<Routine> captor = ArgumentCaptor.forClass(Routine.class);
    verify(routineRepository).save(captor.capture());
    assertThat(captor.getValue().getRoutineType()).isEqualTo(RoutineType.DAILY);
    assertThat(captor.getValue().getTitle()).isEqualTo("제목");
    assertThat(result.getRoutineType()).isEqualTo("DAILY");
  }

  @Test
  @DisplayName("updateTodo - 루틴 없음 + routineType=null: 루틴 관련 동작 없음")
  void updateTodo_success_noRoutineNoChange() {
    User user = testUser();
    Todo todo = testTodo(1L, user);

    given(todoRepository.findById(1L)).willReturn(Optional.of(todo));

    todoService.updateTodo(1L, 1L, updateTodoRequest("제목", null, null, null, null));

    verify(routineRepository, never()).save(any());
  }
}
