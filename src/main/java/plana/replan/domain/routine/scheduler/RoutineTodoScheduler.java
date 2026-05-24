package plana.replan.domain.routine.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import plana.replan.domain.routine.service.RoutineService;

@Component
@RequiredArgsConstructor
public class RoutineTodoScheduler {

  private final RoutineService routineService;

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void generateDailyTodos() {
    routineService.generateDailyTodos();
  }
}
