package plana.replan.domain.tag.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.tag.dto.TagCreateRequestDto;
import plana.replan.domain.tag.dto.TagResponseDto;
import plana.replan.domain.tag.dto.TagUpdateRequestDto;
import plana.replan.domain.tag.service.TagService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController implements TagControllerDocs {

  private final TagService tagService;

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<List<TagResponseDto>>> getTags(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(tagService.getTags(userId)));
  }

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<TagResponseDto>> createTag(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody TagCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.ok(tagService.createTag(userId, request)));
  }

  @Override
  @PutMapping("/{tagId}")
  public ResponseEntity<ApiResult<TagResponseDto>> updateTag(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long tagId,
      @Valid @RequestBody TagUpdateRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(tagService.updateTag(userId, tagId, request)));
  }

  @Override
  @DeleteMapping("/{tagId}")
  public ResponseEntity<ApiResult<String>> deleteTag(
      @AuthenticationPrincipal Long userId, @PathVariable Long tagId) {
    tagService.deleteTag(userId, tagId);
    return ResponseEntity.ok(ApiResult.ok("태그가 성공적으로 삭제되었습니다."));
  }
}
