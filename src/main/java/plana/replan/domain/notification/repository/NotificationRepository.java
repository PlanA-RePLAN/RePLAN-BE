package plana.replan.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationCategory;
import plana.replan.domain.user.entity.User;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Optional<Notification> findByIdAndUser(Long id, User user);

  long countByUserAndIsReadFalse(User user);

  // 회원 탈퇴 시 그 사람의 알림을 모두 soft delete 한다(다른 사용자 데이터와 동일 방식).
  @Modifying
  @Query(
      "UPDATE Notification n SET n.deletedAt = :now WHERE n.user.id = :userId AND n.deletedAt IS NULL")
  void softDeleteAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

  // 전체 목록 (cursor = 직전 응답의 마지막 id, 없으면 Long.MAX_VALUE 전달)
  @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.id < :cursor ORDER BY n.id DESC")
  List<Notification> findPage(
      @Param("user") User user, @Param("cursor") Long cursor, Pageable pageable);

  // 카테고리(탭) 필터 목록
  @Query(
      "SELECT n FROM Notification n WHERE n.user = :user AND n.category = :category"
          + " AND n.id < :cursor ORDER BY n.id DESC")
  List<Notification> findPageByCategory(
      @Param("user") User user,
      @Param("category") NotificationCategory category,
      @Param("cursor") Long cursor,
      Pageable pageable);
}
