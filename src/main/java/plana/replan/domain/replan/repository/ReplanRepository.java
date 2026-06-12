package plana.replan.domain.replan.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.user.entity.User;

public interface ReplanRepository extends JpaRepository<Replan, Long> {

  @Query(
      "SELECT r FROM Replan r WHERE r.todo.user = :user"
          + " AND r.createdAt BETWEEN :start AND :end")
  List<Replan> findByUserAndCreatedAtBetween(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
