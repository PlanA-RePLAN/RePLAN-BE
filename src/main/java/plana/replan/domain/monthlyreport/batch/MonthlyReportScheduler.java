package plana.replan.domain.monthlyreport.batch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.monthlyreport.entity.AiInsight;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.entity.ReportGenerationFailure;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.monthlyreport.repository.ReportGenerationFailureRepository;
import plana.replan.domain.monthlyreport.service.CalculatedStats;
import plana.replan.domain.monthlyreport.service.MonthlyReportAiService;
import plana.replan.domain.monthlyreport.service.MonthlyReportCalculator;
import plana.replan.domain.user.entity.User;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {

  private final JobOperator jobOperator;
  private final Job monthlyReportJob;
  private final ReportGenerationFailureRepository failureRepository;
  private final MonthlyReportCalculator calculator;
  private final MonthlyReportAiService aiService;
  private final MonthlyReportRepository monthlyReportRepository;

  @Value("${statistics.batch.gemini-call-delay-ms:2500}")
  private long geminiCallDelayMs;

  @Scheduled(cron = "0 0 0 1 * *")
  public void runMonthlyReportBatch() {
    log.info("월간 리포트 배치 시작 - targetMonth={}", YearMonth.now().minusMonths(1));

    JobParameters params =
        new JobParametersBuilder().addLocalDateTime("runAt", LocalDateTime.now()).toJobParameters();

    try {
      var execution = jobOperator.start(monthlyReportJob, params);
      log.info("월간 리포트 배치 완료 - status={}", execution.getStatus());
    } catch (Exception e) {
      log.error("월간 리포트 배치 실행 실패", e);
    }
  }

  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void retryFailedReports() {
    List<ReportGenerationFailure> failures = failureRepository.findByRetryCountLessThan(3);
    if (failures.isEmpty()) return;

    log.info("실패 리포트 재처리 시작 - count={}", failures.size());

    for (ReportGenerationFailure failure : failures) {
      User user = failure.getUser();
      YearMonth targetMonth = YearMonth.from(failure.getTargetMonth());
      try {
        CalculatedStats stats = calculator.calculate(user, targetMonth);
        AiInsight aiInsight = null;
        if (stats.hasActivity()) {
          aiInsight = aiService.generateInsight(stats, targetMonth);
          Thread.sleep(geminiCallDelayMs);
        }
        upsertReport(user, failure.getTargetMonth(), stats, aiInsight);
        failure.softDelete();
        log.info("재처리 성공 - userId={}", user.getId());
      } catch (Exception e) {
        log.error("재처리 실패 - userId={}", user.getId(), e);
        String msg = e.getMessage();
        failure.incrementRetry(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
      }
    }
  }

  private void upsertReport(
      User user, LocalDate reportMonth, CalculatedStats stats, AiInsight aiInsight) {
    monthlyReportRepository
        .findByUserAndReportMonth(user, reportMonth)
        .ifPresentOrElse(
            report ->
                report.update(
                    stats.totalTodos(),
                    stats.completedTodos(),
                    stats.achievementRate(),
                    stats.prevMonthDiff(),
                    stats.replanCount(),
                    stats.replanAchievementEffect(),
                    stats.analysisData(),
                    aiInsight),
            () ->
                monthlyReportRepository.save(
                    MonthlyReport.builder()
                        .user(user)
                        .reportMonth(reportMonth)
                        .totalTodos(stats.totalTodos())
                        .completedTodos(stats.completedTodos())
                        .achievementRate(stats.achievementRate())
                        .prevMonthDiff(stats.prevMonthDiff())
                        .replanCount(stats.replanCount())
                        .replanAchievementEffect(stats.replanAchievementEffect())
                        .analysisData(stats.analysisData())
                        .aiInsight(aiInsight)
                        .build()));
  }
}
