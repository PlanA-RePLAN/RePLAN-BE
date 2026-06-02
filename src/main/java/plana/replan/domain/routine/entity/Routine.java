package plana.replan.domain.routine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.tag.entity.Tag;
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
  @JoinColumn(name = "tag_id")
  private Tag tag;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "goal_id")
  private Goal goal;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Routine parent;

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
  private List<Routine> children = new ArrayList<>();

  public void update(String title, RoutineType routineType, Integer routineDate, Tag tag) {
    this.title = requireNonBlank(title);
    this.routineType = routineType;
    this.routineDate = routineDate;
    this.tag = tag;
  }

  public void updateTitle(String title) {
    this.title = requireNonBlank(title);
  }

  public boolean isMother() {
    return parent == null;
  }

  public boolean isChild() {
    return parent != null;
  }

  @Builder
  public Routine(
      String title,
      LocalDateTime dueDate,
      RoutineType routineType,
      Integer routineDate,
      User user,
      Tag tag,
      Goal goal,
      Routine parent) {
    this.title = requireNonBlank(title);
    this.user = Objects.requireNonNull(user, "유저는 필수입니다.");
    if (parent != null) {
      if (parent.isChild()) {
        throw new IllegalArgumentException("하위 루틴 아래에 또 하위 루틴을 만들 수 없습니다.");
      }
      this.parent = parent;
      // 하위 루틴은 모든 필드를 부모에 의존. title과 user만 자기 것.
      this.dueDate = null;
      this.routineType = null;
      this.routineDate = null;
      this.tag = null;
      this.goal = null;
    } else {
      this.dueDate = dueDate;
      this.routineType = routineType;
      this.routineDate = routineDate;
      this.tag = tag;
      this.goal = goal;
    }
  }

  private static String requireNonBlank(String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("제목은 필수입니다.");
    }
    return title;
  }
}
