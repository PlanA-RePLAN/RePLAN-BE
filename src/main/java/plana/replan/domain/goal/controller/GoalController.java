package plana.replan.domain.goal.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import plana.replan.domain.goal.dto.GoalCreateRequest;
import plana.replan.domain.goal.dto.GoalPageResponse;
import plana.replan.domain.goal.dto.GoalResponse;
import plana.replan.domain.goal.service.GoalService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController implements GoalControllerDocs {

  private final GoalService goalService;

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<GoalResponse>> createGoal(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GoalCreateRequest request) {
    return ResponseEntity.ok(ApiResult.ok(goalService.createGoal(userId, request)));
  }

  @Override
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResult<Void>> deleteGoal(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    goalService.deleteGoal(userId, id);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<GoalPageResponse>> getGoals(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) Integer year) {
    return ResponseEntity.ok(ApiResult.ok(goalService.getGoals(userId, cursor, size, year)));
  }
}
