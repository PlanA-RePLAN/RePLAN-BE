package plana.replan.domain.monthlyreport.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.global.entity.BaseTimeEntity;

/**
 * 팁노트 추천 카드 1장. 수정 카드(MODIFY_ROUTINE)는 안 바뀐 필드까지 "수정 후 최종 상태 전체"를 담는다 — 반영 시 그대로 루틴 수정 서비스에 넘기기 위함.
 * changedFields는 화면 diff 표시 전용이다.
 */
@Entity
@Table(name = "tip_note_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class TipNoteItem extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "monthly_report_id", nullable = false)
  private MonthlyReport monthlyReport;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private TipNoteAction action;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_routine_id")
  private Routine targetRoutine;

  @Column(nullable = false)
  private String title;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id")
  private Tag tag;

  @Column(name = "todo_due_at")
  private LocalDateTime todoDueAt;

  @Column(name = "routine_end_at")
  private LocalDateTime routineEndAt;

  @Column(name = "routine_time")
  private LocalTime routineTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "routine_type", length = 16)
  private RoutineType routineType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "routine_days")
  private List<Integer> routineDays;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "changed_fields")
  private List<TipNoteChangedField> changedFields;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TipNoteItemStatus status = TipNoteItemStatus.PENDING;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;

  public void markApplied() {
    this.status = TipNoteItemStatus.APPLIED;
  }

  public void markDismissed() {
    this.status = TipNoteItemStatus.DISMISSED;
  }

  @Builder
  public TipNoteItem(
      MonthlyReport monthlyReport,
      TipNoteAction action,
      Routine targetRoutine,
      String title,
      Tag tag,
      LocalDateTime todoDueAt,
      LocalDateTime routineEndAt,
      LocalTime routineTime,
      RoutineType routineType,
      List<Integer> routineDays,
      List<TipNoteChangedField> changedFields,
      Integer sortOrder) {
    this.monthlyReport = Objects.requireNonNull(monthlyReport, "리포트는 필수입니다.");
    this.action = Objects.requireNonNull(action, "action은 필수입니다.");
    this.title = Objects.requireNonNull(title, "제목은 필수입니다.");
    this.targetRoutine = targetRoutine;
    this.tag = tag;
    this.todoDueAt = todoDueAt;
    this.routineEndAt = routineEndAt;
    this.routineTime = routineTime;
    this.routineType = routineType;
    this.routineDays = routineDays;
    this.changedFields = changedFields;
    this.sortOrder = sortOrder != null ? sortOrder : 0;
    this.status = TipNoteItemStatus.PENDING;
  }
}
