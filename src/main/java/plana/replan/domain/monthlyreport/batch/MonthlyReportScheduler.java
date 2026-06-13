package plana.replan.domain.monthlyreport.batch;

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
import plana.replan.domain.monthlyreport.repository.ReportGenerationFailureRepository;
import plana.replan.domain.monthlyreport.service.MonthlyReportService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {

  private final JobOperator jobOperator;
  private final Job monthlyReportJob;
  private final ReportGenerationFailureRepository failureRepository;
  private final MonthlyReportService monthlyReportService;

  @Value("${statistics.batch.gemini-call-delay-ms:2500}")
  private long geminiCallDelayMs;

  @Value("${statistics.batch.max-retry-count:3}")
  private int maxRetryCount;

  @Scheduled(cron = "0 0 0 1 * *")
  public void runMonthlyReportBatch() {
    YearMonth targetMonth = YearMonth.now().minusMonths(1);
    log.info("월간 리포트 배치 시작 - targetMonth={}", targetMonth);

    JobParameters params =
        new JobParametersBuilder()
            .addLocalDateTime("runAt", LocalDateTime.now())
            .addString("targetMonth", targetMonth.toString())
            .toJobParameters();

    try {
      var execution = jobOperator.start(monthlyReportJob, params);
      if (execution.getStatus().isUnsuccessful()) {
        log.error("월간 리포트 배치 실패 - status={}", execution.getStatus());
      } else {
        log.info("월간 리포트 배치 완료 - status={}", execution.getStatus());
      }
    } catch (Exception e) {
      log.error("월간 리포트 배치 실행 실패", e);
    }
  }

  @Scheduled(cron = "0 0 3 * * *")
  public void retryFailedReports() {
    List<Long> failureIds =
        failureRepository.findByRetryCountLessThan(maxRetryCount).stream()
            .map(f -> f.getId())
            .toList();

    if (failureIds.isEmpty()) return;
    log.info("실패 리포트 재처리 시작 - count={}", failureIds.size());

    for (Long failureId : failureIds) {
      try {
        boolean hadActivity = monthlyReportService.retryOneFailure(failureId);
        // Gemini를 호출한 경우에만 RPM 제한 대응 지연
        if (hadActivity) Thread.sleep(geminiCallDelayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("재처리 인터럽트 - 종료");
        break;
      } catch (Exception e) {
        log.error("재처리 중 예외 발생 - failureId={}", failureId, e);
      }
    }
  }
}
