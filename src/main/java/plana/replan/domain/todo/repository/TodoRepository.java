package plana.replan.domain.todo.repository;

import java.time.LocalDateTime;
import java.util.List;
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

  boolean existsByRoutineAndDueDateBetween(Routine routine, LocalDateTime start, LocalDateTime end);

  @Query("SELECT t FROM Todo t WHERE t.routine IS NOT NULL AND t.dueDate BETWEEN :start AND :end")
  List<Todo> findRoutineTodosByDueDateRange(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query("SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false")
  List<Todo> findActiveTodosForUser(@Param("user") User user);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isPinned = true")
  List<Todo> findPinnedActiveTodosForUser(@Param("user") User user);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false"
          + " AND t.dueDate BETWEEN :start AND :end")
  List<Todo> findActiveTodosByDueDateRange(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = true"
          + " AND t.completedTime BETWEEN :start AND :end")
  List<Todo> findCompletedTodosByCompletedTimeRange(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
