package plana.replan.domain.routine.scheduler;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.routine.service.RoutineService;

@ExtendWith(MockitoExtension.class)
class RoutineTodoSchedulerTest {

  @Mock private RoutineService routineService;

  @InjectMocks private RoutineTodoScheduler scheduler;

  @Test
  void 자정_스케줄러_실행시_generateDailyTodos_호출됨() {
    scheduler.generateDailyTodos();

    verify(routineService).generateDailyTodos();
  }
}
