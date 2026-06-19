package plana.replan.domain.goal.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import plana.replan.domain.goal.dto.common.GoalSingleResponse;
import plana.replan.domain.goal.dto.create.GoalCreateRequest;
import plana.replan.domain.goal.dto.create.GoalWithTodosCreateRequest;
import plana.replan.domain.goal.dto.create.GoalWithTodosCreateResponse;
import plana.replan.domain.goal.dto.list.GoalsByDateResponse;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationRequest;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationResponse;
import plana.replan.domain.goal.dto.explore.GoalExploreRequest;
import plana.replan.domain.goal.dto.explore.GoalExploreResponse;
import plana.replan.domain.goal.dto.refine.GoalRefinementRequest;
import plana.replan.domain.goal.dto.refine.GoalRefinementResponse;
import plana.replan.domain.goal.service.GoalAiService;
import plana.replan.domain.goal.service.GoalService;
import plana.replan.domain.goal.service.GoalWithTodosService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController implements GoalControllerDocs {

  private final GoalService goalService;
  private final GoalAiService goalAiService;
  private final GoalWithTodosService goalWithTodosService;

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<GoalSingleResponse>> createGoal(
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
  public ResponseEntity<ApiResult<List<GoalsByDateResponse>>> getGoals(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    return ResponseEntity.ok(ApiResult.ok(goalService.getGoals(userId, year, month)));
  }

  @Override
  @PostMapping("/with-todos")
  public ResponseEntity<ApiResult<GoalWithTodosCreateResponse>> createGoalWithTodos(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody GoalWithTodosCreateRequest request) {
    return ResponseEntity.ok(ApiResult.ok(goalWithTodosService.create(userId, request)));
  }

  @Override
  @PostMapping("/ai/refine")
  public ResponseEntity<ApiResult<GoalRefinementResponse>> refineGoal(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GoalRefinementRequest request) {
    return ResponseEntity.ok(ApiResult.ok(goalAiService.refineGoal(request)));
  }

  @Override
  @PostMapping("/ai/todo-recommendations")
  public ResponseEntity<ApiResult<TodoRecommendationResponse>> recommendTodos(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody TodoRecommendationRequest request) {
    return ResponseEntity.ok(ApiResult.ok(goalAiService.recommendTodos(request)));
  }

  @Override
  @PostMapping("/ai/explore")
  public ResponseEntity<ApiResult<GoalExploreResponse>> exploreGoal(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GoalExploreRequest request) {
    return ResponseEntity.ok(ApiResult.ok(goalAiService.exploreGoal(request)));
  }
}
