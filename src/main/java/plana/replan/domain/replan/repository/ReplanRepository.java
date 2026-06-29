package plana.replan.domain.replan.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.user.entity.User;

public interface ReplanRepository extends JpaRepository<Replan, Long> {

  @Query(
      "SELECT r FROM Replan r WHERE r.todo.user = :user"
          + " AND r.createdAt >= :start AND r.createdAt < :end")
  List<Replan> findByUserAndCreatedAtBetween(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 특정 투두를 가리키는 리플랜(메모)을 모두 찾는다. 같은 투두를 한 달에 여러 번 리플랜하면 그 투두에 리플랜이 여러 개 달릴 수 있으므로, 그 투두를 소프트 삭제(통계
   * 제외)하기 전에 달려 있던 리플랜을 빠짐없이 살아있는 새 투두로 옮기는 데 쓴다.
   */
  List<Replan> findByTodo(Todo todo);
}
