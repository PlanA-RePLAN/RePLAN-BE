package plana.replan.domain.monthlyreport.batch;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
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
  private final ReportSkipListener reportSkipListener;
  private final GeminiThrottleChunkListener throttleListener;

  @Value("${statistics.batch.skip-limit:100}")
  private int skipLimit;

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
        .skipPolicy(
            (t, skipCount) -> {
              // NPE·IllegalStateException은 버그 지표이므로 스킵 금지
              if (t instanceof NullPointerException || t instanceof IllegalStateException) {
                return false;
              }
              return t instanceof RuntimeException && skipCount < skipLimit;
            })
        .listener(reportSkipListener)
        .listener(throttleListener)
        .build();
  }

  @Bean
  @StepScope
  public JpaCursorItemReader<User> userItemReader() {
    return new JpaCursorItemReaderBuilder<User>()
        .name("userItemReader")
        .entityManagerFactory(entityManagerFactory)
        .queryString("SELECT u FROM User u WHERE u.deletedAt IS NULL ORDER BY u.id ASC")
        .build();
  }
}
