package plana.replan.domain.tag.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.user.entity.User;

public interface TagRepository extends JpaRepository<Tag, Long> {

  List<Tag> findAllByUserOrderByCreatedAtDescIdDesc(User user);
}
