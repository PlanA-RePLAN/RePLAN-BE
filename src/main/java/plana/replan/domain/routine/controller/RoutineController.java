package plana.replan.domain.routine.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.dto.RoutineUpdateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineCreateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineResponseDto;
import plana.replan.domain.routine.dto.SubRoutineUpdateRequestDto;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
public class RoutineController implements RoutineControllerDocs {

  private final RoutineService routineService;

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<Map<String, List<RoutineResponseDto>>>> getRoutinesByFilter(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "day") String filter,
      @RequestParam LocalDate date) {
    return ResponseEntity.ok(
        ApiResult.ok(routineService.getRoutinesByFilter(userId, filter, date)));
  }

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<RoutineResponseDto>> createRoutine(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RoutineCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.ok(routineService.createRoutine(userId, request)));
  }

  @Override
  @PostMapping("/{parentId}/children")
  public ResponseEntity<ApiResult<SubRoutineResponseDto>> createChildRoutine(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long parentId,
      @Valid @RequestBody SubRoutineCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.ok(routineService.createChildRoutine(userId, parentId, request)));
  }

  @Override
  @PutMapping("/{id}")
  public ResponseEntity<ApiResult<RoutineResponseDto>> updateMotherRoutine(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody RoutineUpdateRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(routineService.updateMotherRoutine(userId, id, request)));
  }

  @Override
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResult<Void>> deleteMotherRoutine(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    routineService.deleteMotherRoutine(userId, id);
    return ResponseEntity.ok(ApiResult.ok(null));
  }

  @Override
  @DeleteMapping("/children/{id}")
  public ResponseEntity<ApiResult<Void>> deleteChildRoutine(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    routineService.deleteChildRoutine(userId, id);
    return ResponseEntity.ok(ApiResult.ok(null));
  }

  @Override
  @PatchMapping("/children/{id}")
  public ResponseEntity<ApiResult<SubRoutineResponseDto>> updateChildRoutine(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody SubRoutineUpdateRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(routineService.updateChildRoutine(userId, id, request)));
  }
}
