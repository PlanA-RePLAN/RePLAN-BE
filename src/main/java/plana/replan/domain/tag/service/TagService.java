package plana.replan.domain.tag.service;

import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.dto.TagCreateRequestDto;
import plana.replan.domain.tag.dto.TagResponseDto;
import plana.replan.domain.tag.dto.TagUpdateRequestDto;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@Service
@RequiredArgsConstructor
public class TagService {

  private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  private static final List<DefaultTag> DEFAULT_TAGS =
      List.of(
          new DefaultTag("Study", "#FFEBE7"),
          new DefaultTag("Project", "#F9ECF8"),
          new DefaultTag("Health", "#E4F5EE"),
          new DefaultTag("Other", "#E5EDFF"));

  private record DefaultTag(String title, String color) {}

  private final TagRepository tagRepository;
  private final UserRepository userRepository;
  private final TodoRepository todoRepository;
  private final RoutineRepository routineRepository;

  @Transactional(readOnly = true)
  public List<TagResponseDto> getTags(Long userId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    return tagRepository.findAllByUserOrderByCreatedAtDescIdDesc(user).stream()
        .map(TagResponseDto::from)
        .toList();
  }

  @Transactional
  public TagResponseDto createTag(Long userId, TagCreateRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    if (request.getTitle() == null || request.getTitle().isBlank()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    validateColor(request.getColor());

    Tag tag = Tag.builder().title(request.getTitle()).color(request.getColor()).user(user).build();
    tagRepository.save(tag);
    return TagResponseDto.from(tag);
  }

  @Transactional
  public TagResponseDto updateTag(Long userId, Long tagId, TagUpdateRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Tag tag =
        tagRepository
            .findById(tagId)
            .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));

    if (!tag.getUser().getId().equals(userId)) {
      throw new CustomException(TagErrorCode.TAG_NOT_FOUND);
    }

    if (request.getTitle() != null && request.getTitle().isBlank()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    validateColor(request.getColor());

    tag.update(request.getTitle(), request.getColor());
    return TagResponseDto.from(tag);
  }

  @Transactional
  public void createDefaultTags(User user) {
    List<Tag> tags =
        DEFAULT_TAGS.stream()
            .map(
                defaultTag ->
                    Tag.builder()
                        .title(defaultTag.title())
                        .color(defaultTag.color())
                        .user(user)
                        .build())
            .toList();
    tagRepository.saveAll(tags);
  }

  private void validateColor(String color) {
    if (color != null && !HEX_COLOR.matcher(color).matches()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  @Transactional
  public void deleteTag(Long userId, Long tagId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Tag tag =
        tagRepository
            .findById(tagId)
            .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));

    if (!tag.getUser().getId().equals(userId)) {
      throw new CustomException(TagErrorCode.TAG_NOT_FOUND);
    }

    todoRepository.clearTagFromTodos(tag);
    routineRepository.clearTagFromRoutines(tag);
    tag.softDelete();
  }
}
