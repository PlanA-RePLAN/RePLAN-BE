package plana.replan.domain.goal.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.user.entity.User;

public interface GoalRepository extends JpaRepository<Goal, Long> {

  List<Goal> findByUserOrderByCreatedAtDescIdAsc(User user);

  @Query(
      "SELECT g FROM Goal g WHERE g.user = :user AND EXTRACT(YEAR FROM g.createdAt) = :year ORDER BY g.createdAt DESC, g.id ASC")
  List<Goal> findByUserAndCreatedAtYear(@Param("user") User user, @Param("year") int year);

  @Query(
      "SELECT g FROM Goal g WHERE g.user = :user AND EXTRACT(YEAR FROM g.createdAt) = :year AND EXTRACT(MONTH FROM g.createdAt) = :month ORDER BY g.createdAt DESC, g.id ASC")
  List<Goal> findByUserAndCreatedAtYearAndMonth(
      @Param("user") User user, @Param("year") int year, @Param("month") int month);
}
