package plana.replan.global.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.global.common.ApiResult;

/** local/dev 환경 전용 내부 테스트용 컨트롤러. prod에서는 빈 등록 안 됨. */
@RestController
@RequestMapping("/internal/admin")
@RequiredArgsConstructor
@Profile("!prod")
public class AdminController {

  private final RoutineService routineService;

  @PostMapping("/batch/daily-todos")
  public ResponseEntity<ApiResult<String>> runDailyTodoBatch() {
    routineService.generateDailyTodos();
    return ResponseEntity.ok(ApiResult.ok("generateDailyTodos 완료"));
  }
}
