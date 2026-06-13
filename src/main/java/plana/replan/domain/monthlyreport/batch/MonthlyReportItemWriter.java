package plana.replan.domain.monthlyreport.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.monthlyreport.service.CalculatedStats;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportItemWriter implements ItemWriter<MonthlyReportData> {

  private final MonthlyReportRepository monthlyReportRepository;

  @Override
  public void write(Chunk<? extends MonthlyReportData> chunk) {
    for (MonthlyReportData data : chunk) {
      CalculatedStats stats = data.stats();
      monthlyReportRepository
          .findByUserAndReportMonth(data.user(), data.reportMonth())
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
                      data.aiInsight()),
              () ->
                  monthlyReportRepository.save(
                      MonthlyReport.builder()
                          .user(data.user())
                          .reportMonth(data.reportMonth())
                          .totalTodos(stats.totalTodos())
                          .completedTodos(stats.completedTodos())
                          .achievementRate(stats.achievementRate())
                          .prevMonthDiff(stats.prevMonthDiff())
                          .replanCount(stats.replanCount())
                          .replanAchievementEffect(stats.replanAchievementEffect())
                          .analysisData(stats.analysisData())
                          .aiInsight(data.aiInsight())
                          .build()));

      log.debug("리포트 저장 완료 - userId={}", data.user().getId());
    }
  }
}
