package plana.replan.domain.routine.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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
}
