package plana.replan.domain.tag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.tag.dto.TagCreateRequestDto;
import plana.replan.domain.tag.dto.TagResponseDto;
import plana.replan.domain.tag.dto.TagUpdateRequestDto;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@Service
@RequiredArgsConstructor
public class TagService {

  private final TagRepository tagRepository;
  private final UserRepository userRepository;

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

    tag.update(request.getTitle(), request.getColor());
    return TagResponseDto.from(tag);
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

    tag.softDelete();
  }
}
