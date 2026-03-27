package plana.replan.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

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
  @Column(nullable = false)
  private Provider provider;

  @Builder
  public User(String email, String password, String nickname, Role role, Provider provider) {
    this.email = email;
    this.password = password;
    this.nickname = nickname;
    this.role = role;
    this.provider = provider;
  }
}
