package plana.replan.domain.monthlyreport.batch;

import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import plana.replan.domain.monthlyreport.entity.ReportGenerationFailure;
import plana.replan.domain.monthlyreport.repository.ReportGenerationFailureRepository;
import plana.replan.domain.user.entity.User;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class ReportSkipListener implements SkipListener<User, MonthlyReportData> {

  private final ReportGenerationFailureRepository failureRepository;

  @Value("#{T(java.time.YearMonth).parse(jobParameters['targetMonth'])}")
  private YearMonth targetMonth;

  @Override
  public void onSkipInProcess(User user, Throwable t) {
    log.error("통계 생성 실패 - userId={}", user.getId(), t);
    saveFailure(user, t.getMessage());
  }

  @Override
  public void onSkipInRead(Throwable t) {
    log.error("사용자 읽기 실패", t);
  }

  @Override
  public void onSkipInWrite(MonthlyReportData data, Throwable t) {
    log.error("리포트 저장 실패 - userId={}", data.user().getId(), t);
    saveFailure(data.user(), t.getMessage());
  }

  private void saveFailure(User user, String message) {
    try {
      String truncated =
          message != null && message.length() > 500 ? message.substring(0, 500) : message;
      failureRepository
          .findByUserAndTargetMonth(user, targetMonth.atDay(1))
          .ifPresentOrElse(
              f -> f.incrementRetry(truncated),
              () ->
                  failureRepository.save(
                      ReportGenerationFailure.of(user, targetMonth.atDay(1), truncated)));
    } catch (Exception ex) {
      log.error("실패 기록 저장 오류 - userId={}", user.getId(), ex);
    }
  }
}
