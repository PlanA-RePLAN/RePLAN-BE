package plana.replan.domain.routine.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.routine.dto.RoutineDateProjection;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.tag.entity.Tag;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

  @Modifying
  @Query("UPDATE Routine r SET r.tag = null WHERE r.tag = :tag AND r.deletedAt IS NULL")
  void clearTagFromRoutines(@Param("tag") Tag tag);

  @Modifying
  @Query(
      "UPDATE Routine r SET r.deletedAt = :now WHERE r.user.id = :userId AND r.deletedAt IS NULL")
  void softDeleteAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

  @EntityGraph(attributePaths = "children")
  Optional<Routine> findWithChildrenById(Long id);

  @Query("SELECT r FROM Routine r WHERE r.parent IS NULL AND r.deletedAt IS NULL")
  List<Routine> findAllActiveMotherRoutines();

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT r.id                                    AS routineId,
                 COALESCE(ro.title, r.title)             AS title,
                 r.due_date                              AS dueDate,
                 r.routine_time                          AS routineTime,
                 r.routine_type                          AS routineType,
                 r.routine_date                          AS routineDate,
                 COALESCE(ro.tag_id, r.tag_id)           AS tagId,
                 t.title                                 AS tagTitle,
                 t.color                                 AS tagColor,
                 r.goal_id                               AS goalId,
                 td.id                                   AS todoId,
                 ro.sort_order                           AS overrideSortOrder,
                 r.default_sort_order                    AS defaultSortOrder,
                 COALESCE(ro.is_skipped,   FALSE)        AS isSkipped,
                 COALESCE(ro.is_pinned,    FALSE)        AS isPinned,
                 COALESCE(ro.is_completed, FALSE)        AS isCompleted,
                 (ro.id IS NOT NULL)                     AS hasOverride
          FROM routine r
          LEFT JOIN routine_override ro
                 ON ro.routine_id = r.id AND ro.override_date = :targetDate
          LEFT JOIN tag t ON COALESCE(ro.tag_id, r.tag_id) = t.id AND t.deleted_at IS NULL
          LEFT JOIN todo td ON td.routine_id = r.id
                            AND td.deleted_at IS NULL
                            AND td.parent_id IS NULL
                            AND CAST(td.due_date AS date) = :targetDate
          WHERE r.user_id = :userId
            AND r.deleted_at IS NULL
            AND r.parent_id IS NULL
            AND (
              r.routine_type = 'DAILY'
              OR (r.routine_type = 'WEEKLY'  AND (r.routine_date & :dayBit) != 0)
              OR (r.routine_type = 'MONTHLY' AND (r.routine_date & :monthDayBit) != 0)
            )
          """)
  List<RoutineDateProjection> findMotherRoutinesByDate(
      @Param("userId") Long userId,
      @Param("dayBit") int dayBit,
      @Param("monthDayBit") int monthDayBit,
      @Param("targetDate") LocalDate targetDate);
}
