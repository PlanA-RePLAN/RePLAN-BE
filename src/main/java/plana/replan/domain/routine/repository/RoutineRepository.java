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
          SELECT r.id                                                  AS routineId,
                 COALESCE(td.title, COALESCE(ro.title, r.title))      AS title,
                 r.due_date                                            AS repeatEndDate,
                 COALESCE(td.due_date,
                   CAST(CAST(:targetDate AS date) + COALESCE(r.routine_time, TIME '23:59:59') AS timestamp)
                 )                                                     AS dueDate,
                 r.routine_time                                        AS routineTime,
                 r.routine_type                                        AS routineType,
                 r.routine_date                                        AS routineDate,
                 CASE WHEN td.id IS NOT NULL THEN td.tag_id
                      ELSE COALESCE(ro.tag_id, r.tag_id)
                 END                                                   AS tagId,
                 t.title                                               AS tagTitle,
                 t.color                                               AS tagColor,
                 r.goal_id                                             AS goalId,
                 td.id                                                 AS todoId,
                 COALESCE(td.sort_order,
                   COALESCE(ro.sort_order, r.default_sort_order))      AS sortOrder,
                 (ro.id IS NOT NULL)                                   AS hasOverride,
                 CASE WHEN td.id IS NOT NULL THEN td.is_pinned
                      ELSE COALESCE(ro.is_pinned, FALSE)
                 END                                                   AS isPinned,
                 CASE WHEN td.id IS NOT NULL THEN td.is_completed
                      ELSE COALESCE(ro.is_completed, FALSE)
                 END                                                   AS isCompleted,
                 ro.override_date                                      AS overrideDate,
                 (COALESCE(td.due_date,
                    CAST(CAST(:targetDate AS date) + COALESCE(r.routine_time, TIME '23:59:59') AS timestamp)
                  ) < NOW() AT TIME ZONE 'Asia/Seoul'
                  AND CASE WHEN td.id IS NOT NULL THEN td.is_completed
                           ELSE COALESCE(ro.is_completed, FALSE)
                      END = FALSE)                                     AS isOverdue
          FROM routine r
          LEFT JOIN routine_override ro
                 ON ro.routine_id = r.id AND ro.override_date = :targetDate
          LEFT JOIN todo td ON td.routine_id = r.id
                            AND td.deleted_at IS NULL
                            AND td.parent_id IS NULL
                            AND CAST(td.due_date AS date) = :targetDate
          LEFT JOIN tag t ON (
                            CASE WHEN td.id IS NOT NULL THEN td.tag_id
                                 ELSE COALESCE(ro.tag_id, r.tag_id)
                            END
                          ) = t.id AND t.deleted_at IS NULL
          WHERE r.user_id = :userId
            AND r.deleted_at IS NULL
            AND r.parent_id IS NULL
            AND (
              r.routine_type = 'DAILY'
              OR (r.routine_type = 'WEEKLY'  AND (r.routine_date & :dayBit) != 0)
              OR (r.routine_type = 'MONTHLY' AND (r.routine_date & :monthDayBit) != 0)
            )
            AND COALESCE(ro.is_skipped, FALSE) = FALSE
          """)
  List<RoutineDateProjection> findMotherRoutinesByDate(
      @Param("userId") Long userId,
      @Param("dayBit") int dayBit,
      @Param("monthDayBit") int monthDayBit,
      @Param("targetDate") LocalDate targetDate);

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT r.id                                                  AS routineId,
                 COALESCE(td.title, COALESCE(ro.title, r.title))      AS title,
                 r.due_date                                            AS repeatEndDate,
                 COALESCE(td.due_date,
                   CAST(ro.override_date + COALESCE(r.routine_time, TIME '23:59:59') AS timestamp)
                 )                                                     AS dueDate,
                 r.routine_time                                        AS routineTime,
                 r.routine_type                                        AS routineType,
                 r.routine_date                                        AS routineDate,
                 CASE WHEN td.id IS NOT NULL THEN td.tag_id
                      ELSE COALESCE(ro.tag_id, r.tag_id)
                 END                                                   AS tagId,
                 t.title                                               AS tagTitle,
                 t.color                                               AS tagColor,
                 r.goal_id                                             AS goalId,
                 td.id                                                 AS todoId,
                 COALESCE(td.sort_order,
                   COALESCE(ro.sort_order, r.default_sort_order))      AS sortOrder,
                 TRUE                                                  AS hasOverride,
                 CASE WHEN td.id IS NOT NULL THEN td.is_pinned
                      ELSE COALESCE(ro.is_pinned, FALSE)
                 END                                                   AS isPinned,
                 CASE WHEN td.id IS NOT NULL THEN td.is_completed
                      ELSE COALESCE(ro.is_completed, FALSE)
                 END                                                   AS isCompleted,
                 ro.override_date                                      AS overrideDate,
                 (COALESCE(td.due_date,
                    CAST(ro.override_date + COALESCE(r.routine_time, TIME '23:59:59') AS timestamp)
                  ) < NOW() AT TIME ZONE 'Asia/Seoul'
                  AND CASE WHEN td.id IS NOT NULL THEN td.is_completed
                           ELSE COALESCE(ro.is_completed, FALSE)
                      END = FALSE)                                     AS isOverdue
          FROM routine_override ro
          INNER JOIN routine r ON r.id = ro.routine_id
          LEFT JOIN todo td ON td.routine_id = r.id
                            AND td.deleted_at IS NULL
                            AND td.parent_id IS NULL
                            AND CAST(td.due_date AS date) = ro.override_date
          LEFT JOIN tag t ON (
                            CASE WHEN td.id IS NOT NULL THEN td.tag_id
                                 ELSE COALESCE(ro.tag_id, r.tag_id)
                            END
                          ) = t.id AND t.deleted_at IS NULL
          WHERE r.user_id = :userId
            AND r.deleted_at IS NULL
            AND r.parent_id IS NULL
            AND CASE WHEN td.id IS NOT NULL THEN td.is_pinned
                     ELSE COALESCE(ro.is_pinned, FALSE)
                END = TRUE
            AND COALESCE(ro.is_skipped, FALSE) = FALSE
          ORDER BY ro.override_date ASC,
                   COALESCE(td.sort_order, COALESCE(ro.sort_order, r.default_sort_order)) ASC
          """)
  List<RoutineDateProjection> findPinnedMotherRoutines(@Param("userId") Long userId);
}
