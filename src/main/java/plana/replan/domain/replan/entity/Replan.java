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

  /**
   * 리플랜이 가리키는 투두를 바꾼다. 리플랜이 "수정"한 원본 투두를 소프트 삭제(통계 제외)할 때, 삭제된 투두는
   * {@code @SQLRestriction(deleted_at IS NULL)}으로 모든 조회에서 빠지므로 이 리플랜 자체가 월간 통계 집계에서 사라진다. 그래서 살아있는
   * 새 투두로 옮겨 달아 리플랜이 정상 집계되게 한다.
   */
  public void relinkTodo(Todo todo) {
    this.todo = Objects.requireNonNull(todo, "투두는 필수입니다.");
  }
}
