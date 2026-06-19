package plana.replan.domain.notification.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NotificationCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private NotificationType type;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", length = 16)
  private TargetType targetType;

  @Column(name = "target_id")
  private Long targetId;

  @Column(name = "is_read", nullable = false)
  private boolean isRead = false;

  @Builder
  public Notification(
      User user,
      NotificationType type,
      String title,
      String body,
      TargetType targetType,
      Long targetId) {
    this.user = user;
    this.type = Objects.requireNonNull(type, "알림 종류는 필수입니다.");
    this.category = type.getCategory();
    this.title = Objects.requireNonNull(title, "제목은 필수입니다.");
    this.body = Objects.requireNonNull(body, "내용은 필수입니다.");
    this.targetType = targetType;
    this.targetId = targetId;
  }

  public void markRead() {
    this.isRead = true;
  }
}
