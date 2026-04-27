package plana.replan.domain.goal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "goal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goal extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(name = "due_date")
  private LocalDateTime dueDate;

  @Column(columnDefinition = "TEXT")
  private String reference;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Builder
  public Goal(String title, LocalDateTime dueDate, String reference, User user) {
    this.title = Objects.requireNonNull(title, "제목은 필수입니다.");
    this.user = Objects.requireNonNull(user, "유저는 필수입니다.");
    this.dueDate = dueDate;
    this.reference = reference;
  }
}
