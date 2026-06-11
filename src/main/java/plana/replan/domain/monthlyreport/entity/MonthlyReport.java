package plana.replan.domain.monthlyreport.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "monthly_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class MonthlyReport extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "report_month", nullable = false)
  private LocalDate reportMonth;

  @Column(name = "total_todos")
  private Integer totalTodos;

  @Column(name = "completed_todos")
  private Integer completedTodos;

  @Column(name = "achievement_rate", precision = 5, scale = 2)
  private BigDecimal achievementRate;

  @Column(name = "prev_month_diff", precision = 5, scale = 2)
  private BigDecimal prevMonthDiff;

  @Column(name = "replan_count")
  private Integer replanCount;

  @Column(name = "replan_achievement_effect", precision = 5, scale = 2)
  private BigDecimal replanAchievementEffect;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "analysis_data")
  private AnalysisData analysisData;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ai_insight")
  private AiInsight aiInsight;

  @Builder
  public MonthlyReport(
      User user,
      LocalDate reportMonth,
      Integer totalTodos,
      Integer completedTodos,
      BigDecimal achievementRate,
      BigDecimal prevMonthDiff,
      Integer replanCount,
      BigDecimal replanAchievementEffect,
      AnalysisData analysisData,
      AiInsight aiInsight) {
    this.user = Objects.requireNonNull(user, "유저는 필수입니다.");
    this.reportMonth = Objects.requireNonNull(reportMonth, "리포트 월은 필수입니다.");
    this.totalTodos = totalTodos;
    this.completedTodos = completedTodos;
    this.achievementRate = achievementRate;
    this.prevMonthDiff = prevMonthDiff;
    this.replanCount = replanCount;
    this.replanAchievementEffect = replanAchievementEffect;
    this.analysisData = analysisData;
    this.aiInsight = aiInsight;
  }
}
