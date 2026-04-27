package plana.replan.domain.todo.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "failure_reason")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailureReason {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(columnDefinition = "TEXT")
  private String content;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "todo_id", nullable = false)
  private Todo todo;

  @Builder
  public FailureReason(String content, Todo todo) {
    this.todo = Objects.requireNonNull(todo, "투두는 필수입니다.");
    this.content = content;
  }
}
