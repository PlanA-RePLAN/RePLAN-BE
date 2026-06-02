package plana.replan.domain.replan.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "replan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Replan extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "todo_id", nullable = false)
  private Todo todo;

  @Column(name = "failure_reason_1", nullable = false, length = 128)
  private String failureReason1;

  @Column(name = "failure_reason_2", length = 128)
  private String failureReason2;

  @Column(name = "failure_reason_3", length = 128)
  private String failureReason3;

  @Builder
  public Replan(Todo todo, String failureReason1, String failureReason2, String failureReason3) {
    this.todo = Objects.requireNonNull(todo, "투두는 필수입니다.");
    this.failureReason1 = Objects.requireNonNull(failureReason1, "실패 사유 1은 필수입니다.");
    this.failureReason2 = failureReason2;
    this.failureReason3 = failureReason3;
  }
}
