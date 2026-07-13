package plana.replan.domain.routine.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import plana.replan.domain.tag.entity.Tag;

@Entity
@Table(
    name = "routine_override",
    uniqueConstraints = @UniqueConstraint(columnNames = {"routine_id", "override_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutineOverride {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "routine_id", nullable = false)
  private Routine routine;

  @Column(name = "override_date", nullable = false)
  private LocalDate overrideDate;

  @Column(nullable = true)
  private String title;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id")
  private Tag tag;

  @Column(name = "sort_order")
  private Double sortOrder;

  @Column(name = "is_skipped", nullable = false)
  private boolean isSkipped = false;

  @Column(name = "is_pinned")
  private Boolean isPinned;

  @Column(name = "is_completed")
  private Boolean isCompleted;

  @Column(name = "completed_time")
  private LocalDateTime completedTime;

  // 이 날짜 회차만의 마감시간. null이면 루틴 기본 시간(routineTime)을 따른다.
  @Column(name = "override_time")
  private LocalTime overrideTime;

  // 행(Todo)이 아직 없는 이 날짜에 예약해 둔 하위 투두 목록 ({title, isCompleted}).
  // 배치가 그날 행을 만들 때 완료 상태까지 실제 하위 투두로 실체화한 뒤 비운다. null = 예약 없음.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "override_subtodos")
  private List<ReservedSubtodo> overrideSubtodos;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  public RoutineOverride(Routine routine, LocalDate overrideDate) {
    this.routine = routine;
    this.overrideDate = overrideDate;
  }

  public void updateContent(String title, Tag tag, LocalTime overrideTime) {
    this.title = title;
    this.tag = tag;
    this.overrideTime = overrideTime;
  }

  public void updateOrder(double sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void updateComplete(boolean isCompleted, LocalDateTime now) {
    this.isCompleted = isCompleted;
    this.completedTime = isCompleted ? now : null;
  }

  public void updatePin(boolean isPinned) {
    this.isPinned = isPinned;
  }

  public void skip() {
    this.isSkipped = true;
  }

  public void unskip() {
    this.isSkipped = false;
  }

  /** 예약 하위 투두 개수. 배열이 없으면 0. */
  public int reservedSubtodoCount() {
    return overrideSubtodos == null ? 0 : overrideSubtodos.size();
  }

  public void addSubtodo(String title) {
    if (overrideSubtodos == null) {
      overrideSubtodos = new ArrayList<>();
    }
    overrideSubtodos.add(ReservedSubtodo.of(title));
  }

  /** index 범위 검증은 호출부(서비스)에서 한다. */
  public void updateSubtodo(int index, String title) {
    overrideSubtodos.set(index, overrideSubtodos.get(index).withTitle(title));
  }

  /** 예약 하위 완료/미완료. index 범위 검증은 호출부(서비스)에서 한다. */
  public void completeSubtodo(int index, boolean isCompleted) {
    overrideSubtodos.set(index, overrideSubtodos.get(index).withCompleted(isCompleted));
  }

  public void removeSubtodo(int index) {
    overrideSubtodos.remove(index);
    if (overrideSubtodos.isEmpty()) {
      overrideSubtodos = null;
    }
  }

  /** 실체화(행 생성 시 하위 투두로 이관) 후 예약을 비운다. */
  public void clearSubtodos() {
    this.overrideSubtodos = null;
  }
}
