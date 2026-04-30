package plana.replan.domain.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<User> findByEmailAndProvider(String email, Provider provider);

  boolean existsByNickname(String nickname);
}
