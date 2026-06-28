package plana.replan.domain.user.entity;

import jakarta.persistence.*;
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

  @Column(name = "notify_todo_due", nullable = false)
  private boolean notifyTodoDue = true;

  @Column(name = "notify_todo_failed", nullable = false)
  private boolean notifyTodoFailed = true;

  @Column(name = "notify_report", nullable = false)
  private boolean notifyReport = true;

  @Builder
  public User(
      String email,
      String password,
      String nickname,
      Role role,
      Provider provider,
      String profileImage) {
    this.email = Objects.requireNonNull(email, "이메일은 필수입니다.");
    this.nickname = Objects.requireNonNull(nickname, "닉네임은 필수입니다.");
    this.role = Objects.requireNonNull(role, "역할은 필수입니다.");
    this.provider = Objects.requireNonNull(provider, "제공자는 필수입니다.");
    this.password = password;
    this.profileImage = profileImage;
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
  public void updateNotificationSettings(Boolean todoDue, Boolean todoFailed, Boolean report) {
    if (todoDue != null) {
      this.notifyTodoDue = todoDue;
    }
    if (todoFailed != null) {
      this.notifyTodoFailed = todoFailed;
    }
    if (report != null) {
      this.notifyReport = report;
    }
  }

  public void withdraw() {
    this.email = "deleted_" + this.id + "@deleted.local";
    this.nickname = "deleted_" + this.id;
    this.password = null;
    this.profileImage = null;
    softDelete();
  }
}
