package plana.replan.domain.tag.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.user.entity.User;

public interface TagRepository extends JpaRepository<Tag, Long> {

  List<Tag> findAllByUserOrderByCreatedAtDescIdDesc(User user);

  @Modifying
  @Query("UPDATE Tag t SET t.deletedAt = :now WHERE t.user.id = :userId AND t.deletedAt IS NULL")
  void softDeleteAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
