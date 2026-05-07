package plana.replan.domain.goal.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.user.entity.User;

public interface GoalRepository extends JpaRepository<Goal, Long> {

  List<Goal> findByUserOrderByIdDesc(User user, Pageable pageable);

  List<Goal> findByUserAndIdLessThanOrderByIdDesc(User user, Long cursor, Pageable pageable);

  @Query(
      "SELECT g FROM Goal g WHERE g.user = :user AND FUNCTION('YEAR', g.dueDate) = :year ORDER BY g.id DESC")
  List<Goal> findByUserAndYear(
      @Param("user") User user, @Param("year") int year, Pageable pageable);

  @Query(
      "SELECT g FROM Goal g WHERE g.user = :user AND FUNCTION('YEAR', g.dueDate) = :year AND g.id < :cursor ORDER BY g.id DESC")
  List<Goal> findByUserAndYearAndIdLessThan(
      @Param("user") User user,
      @Param("year") int year,
      @Param("cursor") Long cursor,
      Pageable pageable);
}
