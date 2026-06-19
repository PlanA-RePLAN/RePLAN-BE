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
      "SELECT t FROM Todo t JOIN t.routine r"
          + " WHERE t.parent IS NULL"
          + " AND t.dueDate BETWEEN :start AND :end"
          + " AND r.deletedAt IS NULL")
  List<Todo> findMotherRoutineTodosForRollover(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

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
