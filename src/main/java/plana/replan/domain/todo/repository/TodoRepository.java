package plana.replan.domain.todo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.user.entity.User;

public interface TodoRepository extends JpaRepository<Todo, Long> {

  @Modifying
  @Query("UPDATE Todo t SET t.tag = null WHERE t.tag = :tag AND t.deletedAt IS NULL")
  void clearTagFromTodos(@Param("tag") Tag tag);

  @Modifying
  @Query("UPDATE Todo t SET t.deletedAt = :now WHERE t.user.id = :userId AND t.deletedAt IS NULL")
  void softDeleteAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

  boolean existsByRoutineAndDueDate(Routine routine, LocalDateTime dueDate);

  List<Todo> findAllByRoutine(Routine routine);

  @Query(
      "SELECT t FROM Todo t WHERE t.routine = :routine AND t.parent IS NULL"
          + " AND t.dueDate >= :start AND t.dueDate < :end AND t.deletedAt IS NULL")
  Optional<Todo> findMotherTodoByRoutineAndDate(
      @Param("routine") Routine routine,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t"
          + " WHERE t.routine = :routine"
          + " AND t.parent IS NULL"
          + " AND t.dueDate >= :fromDate"
          + " ORDER BY t.dueDate ASC")
  List<Todo> findUpcomingMotherTodosByRoutine(
      @Param("routine") Routine routine,
      @Param("fromDate") LocalDateTime fromDate,
      Pageable pageable);

  default Optional<Todo> findFirstUpcomingMotherTodoByRoutine(
      Routine routine, LocalDateTime fromDate) {
    return findUpcomingMotherTodosByRoutine(routine, fromDate, PageRequest.of(0, 1)).stream()
        .findFirst();
  }

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isActive = true")
  List<Todo> findActiveTodosForUser(@Param("user") User user);

  @Query("SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isActive = true")
  List<Todo> findAllActiveTodosForUser(@Param("user") User user);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isPinned = true AND t.isActive = true")
  List<Todo> findPinnedActiveTodosForUser(@Param("user") User user);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false"
          + " AND t.dueDate BETWEEN :start AND :end AND t.isActive = true")
  List<Todo> findActiveTodosByDueDateRange(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL"
          + " AND t.dueDate BETWEEN :start AND :end AND t.isActive = true")
  List<Todo> findAllTodosByDueDateRange(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = true"
          + " AND t.completedTime BETWEEN :start AND :end AND t.isActive = true")
  List<Todo> findCompletedTodosByCompletedTimeRange(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL"
          + " AND ((t.dueDate >= :start AND t.dueDate < :end)"
          + " OR (t.dueDate IS NULL AND t.completedTime >= :start AND t.completedTime < :end))")
  List<Todo> findMonthlyTodos(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT MAX(t.sortOrder) FROM Todo t WHERE t.user = :user AND t.parent IS NULL"
          + " AND t.deletedAt IS NULL")
  Optional<Double> findMaxSortOrderByUser(@Param("user") User user);

  // native query: @SQLRestriction("deleted_at IS NULL") 우회 — 삭제된 행 조회용
  @Query(value = "SELECT * FROM todo WHERE id = :id AND deleted_at IS NOT NULL", nativeQuery = true)
  Optional<Todo> findDeletedById(@Param("id") Long id);

  // native query: @SQLRestriction("deleted_at IS NULL") 우회 — 삭제된 행 조회용
  // :since = 부모의 deletedAt - 1초. 부모 삭제 시점에 함께 삭제된 자식만 반환해 독립 삭제 자식 부활을 방지한다.
  @Query(
      value = "SELECT * FROM todo WHERE parent_id = :parentId AND deleted_at >= :since",
      nativeQuery = true)
  List<Todo> findDeletedChildrenByParentId(
      @Param("parentId") Long parentId, @Param("since") LocalDateTime since);

  // native query: @SQLRestriction("deleted_at IS NULL") 우회 — 삭제된 행 조회용
  @Query(
      value =
          "SELECT * FROM todo WHERE routine_id = :routineId AND parent_id IS NULL"
              + " AND due_date >= :start AND due_date < :end AND deleted_at IS NOT NULL",
      nativeQuery = true)
  Optional<Todo> findDeletedMotherTodoByRoutineAndDate(
      @Param("routineId") Long routineId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.replan IS NOT NULL"
          + " AND ((t.dueDate >= :start AND t.dueDate < :end)"
          + " OR (t.dueDate IS NULL AND t.completedTime >= :start AND t.completedTime < :end))")
  List<Todo> findReplanDerivedMonthlyTodos(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t JOIN FETCH t.user WHERE t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isPinned = true AND t.isActive = true"
          + " AND t.dueDate >= :start AND t.dueDate < :end")
  List<Todo> findPinnedDueBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t JOIN FETCH t.user WHERE t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isActive = true AND t.replan IS NULL"
          + " AND t.dueDate >= :start AND t.dueDate < :end")
  List<Todo> findFailedBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
