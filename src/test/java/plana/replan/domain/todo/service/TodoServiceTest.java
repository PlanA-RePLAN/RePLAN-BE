package plana.replan.domain.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.entity.TagColor;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.SubTodoUpdateRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.exception.TodoErrorCode;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

  @Mock private TodoRepository todoRepository;
  @Mock private UserRepository userRepository;
  @Mock private TagRepository tagRepository;

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
}
