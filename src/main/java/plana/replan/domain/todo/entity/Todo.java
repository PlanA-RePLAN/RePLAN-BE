package plana.replan.domain.todo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "todo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(name = "due_date")
  private LocalDateTime dueDate;

  @Column(name = "is_completed", nullable = false)
  private boolean isCompleted = false;

  @Column(name = "completed_time")
  private LocalDateTime completedTime;

  @Column(name = "sort_order", nullable = false)
  private double sortOrder = 10000.0;

  @Column(name = "is_pinned")
  private Boolean isPinned;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id")
  private Tag tag;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "goal_id")
  private Goal goal;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "routine_id")
  private Routine routine;

  @Builder
  public Todo(
      String title,
      LocalDateTime dueDate,
      double sortOrder,
      Boolean isPinned,
      User user,
      Tag tag,
      Goal goal,
      Routine routine) {
    this.title = Objects.requireNonNull(title, "제목은 필수입니다.");
    this.user = Objects.requireNonNull(user, "유저는 필수입니다.");
    this.dueDate = dueDate;
    this.sortOrder = sortOrder;
    this.isPinned = isPinned;
    this.tag = tag;
    this.goal = goal;
    this.routine = routine;
  }
}
