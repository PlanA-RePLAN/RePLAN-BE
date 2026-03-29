package plana.replan.domain.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

  // 이메일로 유저를 조회할
  Optional<User> findByEmail(String email);

  // 회원가입할 때 이미 가입된 이메일인지 체크하는 용도예요
  boolean existsByEmail(String email);
}
