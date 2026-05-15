package plana.replan.domain.routine.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
public class RoutineController implements RoutineControllerDocs {

  private final RoutineService routineService;

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<RoutineResponseDto>> createRoutine(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RoutineCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.ok(routineService.createRoutine(userId, request)));
  }
}
