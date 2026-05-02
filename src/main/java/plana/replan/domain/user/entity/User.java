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
}
