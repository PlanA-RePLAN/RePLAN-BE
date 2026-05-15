package plana.replan.domain.routine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.routine.entity.Routine;

public interface RoutineRepository extends JpaRepository<Routine, Long> {}
