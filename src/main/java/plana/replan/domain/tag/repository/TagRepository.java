package plana.replan.domain.tag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.tag.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {}
