package plana.replan.domain.monthlyreport.batch;

import jakarta.persistence.EntityManagerFactory;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import plana.replan.domain.monthlyreport.entity.ReportGenerationFailure;
import plana.replan.domain.monthlyreport.repository.ReportGenerationFailureRepository;
import plana.replan.domain.user.entity.User;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MonthlyReportJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final EntityManagerFactory entityManagerFactory;
  private final MonthlyReportItemProcessor processor;
  private final MonthlyReportItemWriter writer;
  private final ReportGenerationFailureRepository failureRepository;

  @Bean
  public Job monthlyReportJob(Step monthlyReportStep) {
    return new JobBuilder("monthlyReportJob", jobRepository).start(monthlyReportStep).build();
  }

  @Bean
  public Step monthlyReportStep() {
    return new StepBuilder("monthlyReportStep", jobRepository)
        .<User, MonthlyReportData>chunk(1)
        .transactionManager(transactionManager)
        .reader(userItemReader())
        .processor(processor)
        .writer(writer)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(Integer.MAX_VALUE)
        .listener(reportSkipListener())
        .build();
  }

  @Bean
  public JpaCursorItemReader<User> userItemReader() {
    return new JpaCursorItemReaderBuilder<User>()
        .name("userItemReader")
        .entityManagerFactory(entityManagerFactory)
        .queryString("SELECT u FROM User u WHERE u.deletedAt IS NULL ORDER BY u.id ASC")
        .build();
  }

  @Bean
  public SkipListener<User, MonthlyReportData> reportSkipListener() {
    return new SkipListener<>() {
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
          failureRepository.save(
              ReportGenerationFailure.of(user, YearMonth.now().minusMonths(1).atDay(1), truncated));
        } catch (Exception ex) {
          log.error("실패 기록 저장 오류 - userId={}", user.getId(), ex);
        }
      }
    };
  }
}
