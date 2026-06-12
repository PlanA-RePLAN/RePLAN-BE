package plana.replan.domain.monthlyreport.batch;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaCursorItemReaderBuilder;
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
        .listener(reportSkipListener)
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
}
