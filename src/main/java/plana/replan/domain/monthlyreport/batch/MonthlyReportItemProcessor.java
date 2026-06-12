package plana.replan.domain.monthlyreport.batch;

import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import plana.replan.domain.monthlyreport.entity.AiInsight;
import plana.replan.domain.monthlyreport.service.CalculatedStats;
import plana.replan.domain.monthlyreport.service.MonthlyReportAiService;
import plana.replan.domain.monthlyreport.service.MonthlyReportCalculator;
import plana.replan.domain.user.entity.User;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportItemProcessor implements ItemProcessor<User, MonthlyReportData> {

  private final MonthlyReportCalculator calculator;
  private final MonthlyReportAiService aiService;

  @Value("${statistics.batch.gemini-call-delay-ms:2500}")
  private long geminiCallDelayMs;

  @Override
  public MonthlyReportData process(User user) throws Exception {
    YearMonth targetMonth = YearMonth.now().minusMonths(1);
    log.debug("통계 처리 중 - userId={}, targetMonth={}", user.getId(), targetMonth);

    CalculatedStats stats = calculator.calculate(user, targetMonth);

    AiInsight aiInsight = null;
    if (stats.hasActivity()) {
      aiInsight = aiService.generateInsight(stats, targetMonth);
      Thread.sleep(geminiCallDelayMs);
    }

    return new MonthlyReportData(user, targetMonth.atDay(1), stats, aiInsight);
  }
}
