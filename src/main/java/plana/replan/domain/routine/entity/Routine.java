package plana.replan.domain.routine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "routine")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Routine extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(name = "due_date")
  private LocalDateTime dueDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "routine_type")
  private RoutineType routineType;

  @Column(name = "routine_date")
  private Integer routineDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "goal_id")
  private Goal goal;

  @Builder
  public Routine(
      String title,
      LocalDateTime dueDate,
      RoutineType routineType,
      Integer routineDate,
      User user,
      Goal goal) {
    this.title = Objects.requireNonNull(title, "제목은 필수입니다.");
    this.user = Objects.requireNonNull(user, "유저는 필수입니다.");
    this.dueDate = dueDate;
    this.routineType = routineType;
    this.routineDate = routineDate;
    this.goal = goal;
  }
}
