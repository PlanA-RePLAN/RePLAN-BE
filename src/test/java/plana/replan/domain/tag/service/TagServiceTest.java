package plana.replan.domain.tag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.dto.TagCreateRequestDto;
import plana.replan.domain.tag.dto.TagResponseDto;
import plana.replan.domain.tag.dto.TagUpdateRequestDto;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.entity.TagColor;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

  @Mock private TagRepository tagRepository;
  @Mock private UserRepository userRepository;
  @Mock private TodoRepository todoRepository;
  @Mock private RoutineRepository routineRepository;

  @InjectMocks private TagService tagService;

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

  private Tag testTag(Long id, User user) {
    Tag tag = Tag.builder().title("영어").color(TagColor.BLUE).user(user).build();
    ReflectionTestUtils.setField(tag, "id", id);
    return tag;
  }

  private TagCreateRequestDto createRequest(String title, TagColor color) {
    TagCreateRequestDto dto = new TagCreateRequestDto();
    ReflectionTestUtils.setField(dto, "title", title);
    ReflectionTestUtils.setField(dto, "color", color);
    return dto;
  }

  private TagUpdateRequestDto updateRequest(String title, TagColor color) {
    TagUpdateRequestDto dto = new TagUpdateRequestDto();
    ReflectionTestUtils.setField(dto, "title", title);
    ReflectionTestUtils.setField(dto, "color", color);
    return dto;
  }

  // ── createTag ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("createTag - userId null: USER_NOT_FOUND 예외")
  void createTag_nullUserId_throws() {
    assertThatThrownBy(() -> tagService.createTag(null, createRequest("영어", TagColor.BLUE)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(tagRepository, never()).save(any());
  }

  @Test
  @DisplayName("createTag - userId DB에 없음: USER_NOT_FOUND 예외")
  void createTag_userNotFound_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> tagService.createTag(1L, createRequest("영어", TagColor.BLUE)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(tagRepository, never()).save(any());
  }

  @Test
  @DisplayName("createTag - 성공 (색상 포함): tagId, title, color 반환")
  void createTag_success_withColor() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(tagRepository.save(any(Tag.class))).willAnswer(inv -> inv.getArgument(0));

    TagResponseDto result = tagService.createTag(1L, createRequest("영어", TagColor.BLUE));

    assertThat(result.getTitle()).isEqualTo("영어");
    assertThat(result.getColor()).isEqualTo("BLUE");
  }

  @Test
  @DisplayName("createTag - 성공 (색상 없음): color=null 반환")
  void createTag_success_withoutColor() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(tagRepository.save(any(Tag.class))).willAnswer(inv -> inv.getArgument(0));

    TagResponseDto result = tagService.createTag(1L, createRequest("독서", null));

    assertThat(result.getTitle()).isEqualTo("독서");
    assertThat(result.getColor()).isNull();
  }

  @Test
  @DisplayName("createTag - 성공: save 호출 시 올바른 필드 확인")
  void createTag_success_entityFields() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(tagRepository.save(any(Tag.class))).willAnswer(inv -> inv.getArgument(0));

    tagService.createTag(1L, createRequest("영어", TagColor.GREEN));

    ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
    verify(tagRepository).save(captor.capture());
    assertThat(captor.getValue().getTitle()).isEqualTo("영어");
    assertThat(captor.getValue().getColor()).isEqualTo(TagColor.GREEN);
  }

  // ── updateTag ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("updateTag - userId null: USER_NOT_FOUND 예외")
  void updateTag_nullUserId_throws() {
    assertThatThrownBy(() -> tagService.updateTag(null, 1L, updateRequest("업무", TagColor.RED)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("updateTag - tagId DB에 없음: TAG_NOT_FOUND 예외")
  void updateTag_tagNotFound_throws() {
    given(tagRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> tagService.updateTag(1L, 99L, updateRequest("업무", TagColor.RED)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));
  }

  @Test
  @DisplayName("updateTag - 다른 유저 소유 태그: TAG_NOT_FOUND 예외")
  void updateTag_otherUserTag_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    given(tagRepository.findById(1L)).willReturn(Optional.of(testTag(1L, otherUser)));

    assertThatThrownBy(() -> tagService.updateTag(1L, 1L, updateRequest("업무", TagColor.RED)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));
  }

  @Test
  @DisplayName("updateTag - title 빈 문자열: INVALID_INPUT 예외")
  void updateTag_blankTitle_throws() {
    User user = testUser();
    given(tagRepository.findById(1L)).willReturn(Optional.of(testTag(1L, user)));

    assertThatThrownBy(() -> tagService.updateTag(1L, 1L, updateRequest("", TagColor.RED)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  @DisplayName("updateTag - 성공: title과 color 변경")
  void updateTag_success_updateTitleAndColor() {
    User user = testUser();
    given(tagRepository.findById(1L)).willReturn(Optional.of(testTag(1L, user)));

    TagResponseDto result = tagService.updateTag(1L, 1L, updateRequest("업무", TagColor.RED));

    assertThat(result.getTitle()).isEqualTo("업무");
    assertThat(result.getColor()).isEqualTo("RED");
  }

  @Test
  @DisplayName("updateTag - title null: 기존 title 유지")
  void updateTag_nullTitle_keepExisting() {
    User user = testUser();
    given(tagRepository.findById(1L)).willReturn(Optional.of(testTag(1L, user)));

    TagResponseDto result = tagService.updateTag(1L, 1L, updateRequest(null, TagColor.RED));

    assertThat(result.getTitle()).isEqualTo("영어");
    assertThat(result.getColor()).isEqualTo("RED");
  }

  @Test
  @DisplayName("updateTag - color null: 색상 제거")
  void updateTag_nullColor_removeColor() {
    User user = testUser();
    given(tagRepository.findById(1L)).willReturn(Optional.of(testTag(1L, user)));

    TagResponseDto result = tagService.updateTag(1L, 1L, updateRequest("업무", null));

    assertThat(result.getColor()).isNull();
  }

  // ── deleteTag ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("deleteTag - userId null: USER_NOT_FOUND 예외")
  void deleteTag_nullUserId_throws() {
    assertThatThrownBy(() -> tagService.deleteTag(null, 1L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteTag - tagId DB에 없음: TAG_NOT_FOUND 예외")
  void deleteTag_tagNotFound_throws() {
    given(tagRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> tagService.deleteTag(1L, 99L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteTag - 다른 유저 소유 태그: TAG_NOT_FOUND 예외")
  void deleteTag_otherUserTag_throws() {
    User otherUser =
        User.builder()
            .email("other@test.com")
            .nickname("타인")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(otherUser, "id", 2L);

    given(tagRepository.findById(1L)).willReturn(Optional.of(testTag(1L, otherUser)));

    assertThatThrownBy(() -> tagService.deleteTag(1L, 1L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));
  }

  @Test
  @DisplayName("deleteTag - 성공: 연관 todo·루틴 tag null 처리 후 soft delete")
  void deleteTag_success() {
    User user = testUser();
    Tag tag = testTag(1L, user);
    given(tagRepository.findById(1L)).willReturn(Optional.of(tag));

    tagService.deleteTag(1L, 1L);

    verify(todoRepository).clearTagFromTodos(tag);
    verify(routineRepository).clearTagFromRoutines(tag);
    assertThat(ReflectionTestUtils.getField(tag, "deletedAt")).isNotNull();
  }
}
