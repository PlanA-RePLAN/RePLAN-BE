package plana.replan.domain.goal.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.user.entity.User;

public interface GoalRepository extends JpaRepository<Goal, Long> {

  List<Goal> findByUserOrderByIdAsc(User user, Pageable pageable);

  List<Goal> findByUserAndIdGreaterThanOrderByIdAsc(User user, Long cursor, Pageable pageable);

  @Query(
      "SELECT g FROM Goal g WHERE g.user = :user AND EXTRACT(YEAR FROM g.dueDate) = :year ORDER BY g.id ASC")
  List<Goal> findByUserAndYear(
      @Param("user") User user, @Param("year") int year, Pageable pageable);

  @Query(
      "SELECT g FROM Goal g WHERE g.user = :user AND EXTRACT(YEAR FROM g.dueDate) = :year AND g.id > :cursor ORDER BY g.id ASC")
  List<Goal> findByUserAndYearAndIdGreaterThan(
      @Param("user") User user,
      @Param("year") int year,
      @Param("cursor") Long cursor,
      Pageable pageable);
}
