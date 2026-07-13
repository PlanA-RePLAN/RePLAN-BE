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

  // 중복 생성 방지는 시각이 아니라 날짜 단위로 판단한다 (행의 시각은 회차별 변경·전환 등으로 루틴 기본 시간과 다를 수 있음)
  @Query(
      "SELECT COUNT(t) > 0 FROM Todo t WHERE t.routine = :routine AND t.parent IS NULL"
          + " AND t.dueDate >= :start AND t.dueDate < :end AND t.deletedAt IS NULL")
  boolean existsMotherTodoByRoutineOnDay(
      @Param("routine") Routine routine,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT COUNT(t) > 0 FROM Todo t WHERE t.routine = :routine AND t.parent IS NOT NULL"
          + " AND t.dueDate >= :start AND t.dueDate < :end AND t.deletedAt IS NULL")
  boolean existsChildTodoByRoutineOnDay(
      @Param("routine") Routine routine,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  List<Todo> findAllByRoutine(Routine routine);

  @Query(
      "SELECT t FROM Todo t WHERE t.routine = :routine AND t.parent IS NULL"
          + " AND t.dueDate >= :start AND t.dueDate < :end AND t.deletedAt IS NULL")
  Optional<Todo> findMotherTodoByRoutineAndDate(
      @Param("routine") Routine routine,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t WHERE t.routine = :routine AND t.parent IS NOT NULL"
          + " AND t.dueDate >= :start AND t.dueDate < :end AND t.deletedAt IS NULL")
  Optional<Todo> findChildTodoByRoutineAndDate(
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
      "SELECT t FROM Todo t LEFT JOIN FETCH t.tag WHERE t.routine IN :routines AND t.parent IS NULL"
          + " AND t.isCompleted = true AND t.isActive = true ORDER BY t.dueDate DESC")
  List<Todo> findCompletedMotherTodosByRoutines(@Param("routines") List<Routine> routines);

  @Query(
      "SELECT t FROM Todo t LEFT JOIN FETCH t.tag WHERE t.routine IN :routines AND t.parent IS NULL"
          + " AND t.isCompleted = false AND t.isActive = true ORDER BY t.dueDate DESC")
  List<Todo> findIncompleteMotherTodosByRoutines(@Param("routines") List<Routine> routines);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isActive = true AND t.routine IS NULL")
  List<Todo> findActiveTodosForUser(@Param("user") User user);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isActive = true"
          + " AND t.routine IS NULL")
  List<Todo> findAllActiveTodosForUser(@Param("user") User user);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isPinned = true AND t.isActive = true AND t.routine IS NULL")
  List<Todo> findPinnedActiveTodosForUser(@Param("user") User user);

  @Query(
      "SELECT t FROM Todo t WHERE t.user = :user AND t.parent IS NULL"
          + " AND t.dueDate BETWEEN :start AND :end AND t.isActive = true AND t.routine IS NULL")
  List<Todo> findAllTodosByDueDateRange(
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
  // :deletedAt = 부모와 동일한 softDelete 시각. skip/deleteTodo에서 공통 시각으로 삭제된 자식만 반환한다.
  @Query(
      value = "SELECT * FROM todo WHERE parent_id = :parentId AND deleted_at = :deletedAt",
      nativeQuery = true)
  List<Todo> findDeletedChildrenByParentId(
      @Param("parentId") Long parentId, @Param("deletedAt") LocalDateTime deletedAt);

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

  // 아래 마감 임박/실패 조회는 due_date 범위로 거른다.
  // 공통 조건(parent IS NULL, isCompleted=false, isActive=true)에 맞춘 부분 인덱스
  // idx_todo_due_active(V21)로 순차 스캔 대신 인덱스 스캔되도록 지원한다.
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
