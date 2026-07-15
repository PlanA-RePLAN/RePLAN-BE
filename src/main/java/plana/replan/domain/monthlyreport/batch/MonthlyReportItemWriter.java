package plana.replan.domain.monthlyreport.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.monthlyreport.service.CalculatedStats;
import plana.replan.domain.monthlyreport.service.TipNoteStore;
import plana.replan.domain.notification.event.MonthlyReportCreatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportItemWriter implements ItemWriter<MonthlyReportData> {

  private final MonthlyReportRepository monthlyReportRepository;
  private final TipNoteStore tipNoteStore;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void write(Chunk<? extends MonthlyReportData> chunk) {
    for (MonthlyReportData data : chunk) {
      CalculatedStats stats = data.stats();
      MonthlyReport report =
          monthlyReportRepository
              .findByUserAndReportMonth(data.user(), data.reportMonth())
              .map(
                  existing -> {
                    existing.update(
                        stats.totalTodos(),
                        stats.completedTodos(),
                        stats.achievementRate(),
                        stats.prevMonthDiff(),
                        stats.replanCount(),
                        stats.replanAchievementEffect(),
                        stats.analysisData(),
                        data.aiInsight());
                    return existing;
                  })
              .orElseGet(
                  () -> {
                    MonthlyReport saved =
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
                                .build());
                    // 주의: 이 이벤트 리스너는 동기로 같은 청크 트랜잭션에서 실행된다. FCM 푸시는 커밋 전에 발송되므로
                    // 청크가 롤백되면 푸시는 취소되지 않는다(v1 허용, 추후
                    // @TransactionalEventListener(AFTER_COMMIT)+@Async 검토).
                    eventPublisher.publishEvent(
                        new MonthlyReportCreatedEvent(
                            data.user().getId(),
                            saved.getId(),
                            data.reportMonth().getMonthValue()));
                    return saved;
                  });

      tipNoteStore.replace(report, data.tipNote());
      log.debug("리포트 저장 완료 - userId={}", data.user().getId());
    }
  }
}
