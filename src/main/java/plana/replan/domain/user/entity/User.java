package plana.replan.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column private String password;

  @Column(nullable = false)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "login_type", nullable = false)
  private Provider provider;

  @Column(name = "profile_image", columnDefinition = "TEXT")
  private String profileImage;

  // 알림 카테고리별 수신 설정 (투두 / 통계 / 공지)
  @Column(name = "notify_todo", nullable = false)
  private boolean notifyTodo = true;

  @Column(name = "notify_stats", nullable = false)
  private boolean notifyStats = true;

  @Column(name = "notify_notice", nullable = false)
  private boolean notifyNotice = true;

  // 마케팅 정보 수신 동의(선택 약관). 단순 토글이 아니라 '동의'라서 마지막으로 동의/철회한 시각을 남긴다.
  // 광고성 알림은 2년마다 수신 동의를 다시 확인해야 하는데, 그 기준일이 이 시각이다.
  @Column(name = "marketing_agreed", nullable = false)
  private boolean marketingAgreed = false;

  @Column(name = "marketing_agreed_at")
  private LocalDateTime marketingAgreedAt;

  // 애플 고유 식별번호(sub). 애플 회원만 값이 있고, 재로그인 시 이메일 대신 이 값으로 사용자를 찾는다.
  // 값이 있는 경우 유일해야 하며, 그 제약은 마이그레이션(V22 ux_users_apple_sub)에서 관리한다.
  @Column(name = "apple_sub")
  private String appleSub;

  @Builder
  public User(
      String email,
      String password,
      String nickname,
      Role role,
      Provider provider,
      String profileImage,
      String appleSub) {
    this.email = Objects.requireNonNull(email, "이메일은 필수입니다.");
    this.nickname = Objects.requireNonNull(nickname, "닉네임은 필수입니다.");
    this.role = Objects.requireNonNull(role, "역할은 필수입니다.");
    this.provider = Objects.requireNonNull(provider, "제공자는 필수입니다.");
    this.password = password;
    this.profileImage = profileImage;
    this.appleSub = appleSub;
  }

  /** 애플 재로그인 식별용 고유번호(sub)를 연결한다. 최초 저장·기존 회원 이관에 사용. */
  public void linkAppleSub(String appleSub) {
    this.appleSub = appleSub;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  public void updateProfileImage(String profileImage) {
    this.profileImage = profileImage;
  }

  /**
   * 회원 탈퇴 처리. 개인정보(이메일·닉네임·비밀번호·프로필이미지)를 파기하고 soft delete 한다. 이메일/닉네임은 다른 회원과 겹치지 않도록 id를 붙인 익명값으로
   * 바꾼다. (id가 유일하므로 전역 유니크 제약과도 충돌하지 않는다.)
   */
  public void updateNotificationSettings(Boolean todo, Boolean stats, Boolean notice) {
    if (todo != null) {
      this.notifyTodo = todo;
    }
    if (stats != null) {
      this.notifyStats = stats;
    }
    if (notice != null) {
      this.notifyNotice = notice;
    }
  }

  /** 마케팅 정보 수신 동의를 갱신한다. 값이 실제로 바뀔 때만 동의/철회 시각을 기록한다. */
  public void updateMarketingAgreed(Boolean agreed) {
    if (agreed != null && agreed != this.marketingAgreed) {
      this.marketingAgreed = agreed;
      this.marketingAgreedAt = LocalDateTime.now();
    }
  }

  public void withdraw() {
    this.email = "deleted_" + this.id + "@deleted.local";
    this.nickname = "deleted_" + this.id;
    this.password = null;
    this.profileImage = null;
    // 애플 sub를 비워, 같은 애플 계정으로 재가입할 때 중복 인덱스(ux_users_apple_sub)와 충돌하지 않게 한다.
    this.appleSub = null;
    softDelete();
  }
}
