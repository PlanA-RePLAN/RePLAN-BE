package plana.replan.domain.goal.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import plana.replan.domain.goal.dto.GoalCreateRequestDto;
import plana.replan.domain.goal.dto.GoalSingleResponseDto;
import plana.replan.domain.goal.dto.GoalsByDateResponseDto;
import plana.replan.domain.goal.service.GoalService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController implements GoalControllerDocs {

  private final GoalService goalService;

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<GoalSingleResponseDto>> createGoal(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GoalCreateRequestDto request) {
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
  public ResponseEntity<ApiResult<List<GoalsByDateResponseDto>>> getGoals(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    return ResponseEntity.ok(ApiResult.ok(goalService.getGoals(userId, year, month)));
  }
}
