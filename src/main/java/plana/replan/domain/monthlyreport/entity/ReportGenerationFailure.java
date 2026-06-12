package plana.replan.domain.monthlyreport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "report_generation_failure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class ReportGenerationFailure extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "target_month", nullable = false)
  private LocalDate targetMonth;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  public static ReportGenerationFailure of(User user, LocalDate targetMonth, String errorMessage) {
    ReportGenerationFailure failure = new ReportGenerationFailure();
    failure.user = user;
    failure.targetMonth = targetMonth;
    failure.errorMessage = errorMessage;
    return failure;
  }

  public void incrementRetry(String errorMessage) {
    this.retryCount++;
    this.errorMessage = errorMessage;
  }
}
