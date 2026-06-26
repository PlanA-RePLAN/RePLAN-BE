package plana.replan.domain.notification.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.user.entity.User;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
  Optional<DeviceToken> findByToken(String token);

  Optional<DeviceToken> findByUserAndToken(User user, String token);

  List<DeviceToken> findAllByUser(User user);

  // 회원 탈퇴 시 그 사람의 기기 토큰을 모두 진짜 삭제한다.
  @Modifying
  @Query("DELETE FROM DeviceToken d WHERE d.user.id = :userId")
  void deleteAllByUserId(@Param("userId") Long userId);
}
