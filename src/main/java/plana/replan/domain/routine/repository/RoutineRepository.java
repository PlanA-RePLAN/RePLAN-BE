package plana.replan.domain.routine.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.tag.entity.Tag;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

  @Modifying
  @Query("UPDATE Routine r SET r.tag = null WHERE r.tag = :tag AND r.deletedAt IS NULL")
  void clearTagFromRoutines(@Param("tag") Tag tag);

  @EntityGraph(attributePaths = "children")
  Optional<Routine> findWithChildrenById(Long id);
}
