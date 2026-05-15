package plana.replan.domain.todo.repository;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.todo.entity.Todo;

public interface TodoRepository extends JpaRepository<Todo, Long> {

  boolean existsByRoutineAndDueDateBetween(Routine routine, LocalDateTime start, LocalDateTime end);
}
