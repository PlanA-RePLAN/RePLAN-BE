package plana.replan.domain.notification.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

// 기기 토큰은 죽은 토큰·회원 탈퇴 시 모두 '진짜 삭제'한다(라우팅 정보일 뿐이라 보관하지 않고, 유니크 토큰 값도 풀어줌).
@Entity
@Table(name = "device_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // FCM 토큰. 같은 토큰 중복 저장 금지(DB 유니크는 V14 마이그레이션에서 관리).
  @Column(nullable = false, columnDefinition = "TEXT")
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Platform platform;

  @Builder
  public DeviceToken(User user, String token, Platform platform) {
    this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
    this.token = Objects.requireNonNull(token, "토큰은 필수입니다.");
    this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다.");
  }

  public void updatePlatform(Platform platform) {
    this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다.");
  }

  public void changeOwner(User user) {
    this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
  }
}
