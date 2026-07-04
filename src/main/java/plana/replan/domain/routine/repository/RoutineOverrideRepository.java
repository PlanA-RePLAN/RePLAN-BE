package plana.replan.domain.routine.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;
import plana.replan.domain.tag.entity.Tag;

public interface RoutineOverrideRepository extends JpaRepository<RoutineOverride, Long> {

  Optional<RoutineOverride> findByRoutineAndOverrideDate(Routine routine, LocalDate overrideDate);

  List<RoutineOverride> findByRoutineIdInAndOverrideDate(
      List<Long> routineIds, LocalDate overrideDate);

  void deleteByRoutineAndOverrideDateGreaterThanEqual(Routine routine, LocalDate date);

  @Query("SELECT o FROM RoutineOverride o LEFT JOIN FETCH o.tag WHERE o.routine IN :routines")
  List<RoutineOverride> findByRoutineIn(@Param("routines") List<Routine> routines);

  @Modifying
  @Query("UPDATE RoutineOverride o SET o.tag = null WHERE o.tag = :tag")
  void clearTagFromOverrides(@Param("tag") Tag tag);
}
