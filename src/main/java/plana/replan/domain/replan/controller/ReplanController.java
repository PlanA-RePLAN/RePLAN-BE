package plana.replan.domain.replan.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.domain.replan.service.ReplanService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/replans")
@RequiredArgsConstructor
public class ReplanController implements ReplanControllerDocs {

  private final ReplanService replanService;

  @Override
  @PostMapping("/recommend")
  public ResponseEntity<ApiResult<ReplanRecommendResponse>> recommend(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ReplanRecommendRequest request) {
    return ResponseEntity.ok(ApiResult.ok(replanService.recommend(userId, request)));
  }

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<Void>> save(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ReplanSaveRequest request) {
    replanService.save(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }
}
