package plana.replan.domain.routine.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.routine.dto.RoutineOverrideCompleteRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideContentRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideOrderRequestDto;
import plana.replan.domain.routine.dto.RoutineOverridePinRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideResponseDto;
import plana.replan.domain.routine.service.RoutineOverrideService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
public class RoutineOverrideController implements RoutineOverrideControllerDocs {

  private final RoutineOverrideService routineOverrideService;

  @Override
  @PatchMapping("/{routineId}/overrides/{date}")
  public ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updateContent(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long routineId,
      @PathVariable LocalDate date,
      @RequestBody RoutineOverrideContentRequestDto request) {
    return ResponseEntity.ok(
        ApiResult.ok(routineOverrideService.updateContent(userId, routineId, date, request)));
  }

  @Override
  @PatchMapping("/{routineId}/overrides/{date}/order")
  public ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updateOrder(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long routineId,
      @PathVariable LocalDate date,
      @Valid @RequestBody RoutineOverrideOrderRequestDto request) {
    return ResponseEntity.ok(
        ApiResult.ok(routineOverrideService.updateOrder(userId, routineId, date, request)));
  }

  @Override
  @PatchMapping("/{routineId}/overrides/{date}/complete")
  public ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updateComplete(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long routineId,
      @PathVariable LocalDate date,
      @Valid @RequestBody RoutineOverrideCompleteRequestDto request) {
    return ResponseEntity.ok(
        ApiResult.ok(routineOverrideService.updateComplete(userId, routineId, date, request)));
  }

  @Override
  @PatchMapping("/{routineId}/overrides/{date}/pin")
  public ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updatePin(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long routineId,
      @PathVariable LocalDate date,
      @Valid @RequestBody RoutineOverridePinRequestDto request) {
    return ResponseEntity.ok(
        ApiResult.ok(routineOverrideService.updatePin(userId, routineId, date, request)));
  }

  @Override
  @DeleteMapping("/{routineId}/overrides/{date}")
  public ResponseEntity<ApiResult<Void>> skip(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long routineId,
      @PathVariable LocalDate date) {
    routineOverrideService.skip(userId, routineId, date);
    return ResponseEntity.ok(ApiResult.ok(null));
  }

  @Override
  @PatchMapping("/{routineId}/overrides/{date}/unskip")
  public ResponseEntity<ApiResult<Void>> unskip(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long routineId,
      @PathVariable LocalDate date) {
    routineOverrideService.unskip(userId, routineId, date);
    return ResponseEntity.ok(ApiResult.ok(null));
  }
}
