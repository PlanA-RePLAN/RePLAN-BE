package plana.replan.domain.routine.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;

public interface RoutineOverrideRepository extends JpaRepository<RoutineOverride, Long> {

  Optional<RoutineOverride> findByRoutineAndOverrideDate(Routine routine, LocalDate overrideDate);

  List<RoutineOverride> findByRoutineIdInAndOverrideDate(
      List<Long> routineIds, LocalDate overrideDate);

  void deleteByRoutineAndOverrideDateGreaterThanEqual(Routine routine, LocalDate date);
}
