package plana.replan.domain.monthlyreport.batch;

import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import plana.replan.domain.monthlyreport.service.CalculatedStats;
import plana.replan.domain.monthlyreport.service.MonthlyAiResult;
import plana.replan.domain.monthlyreport.service.MonthlyReportAiService;
import plana.replan.domain.monthlyreport.service.MonthlyReportCalculator;
import plana.replan.domain.monthlyreport.service.TipNoteMaterialCollector;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials;
import plana.replan.domain.user.entity.User;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class MonthlyReportItemProcessor implements ItemProcessor<User, MonthlyReportData> {

  private final MonthlyReportCalculator calculator;
  private final MonthlyReportAiService aiService;
  private final TipNoteMaterialCollector materialCollector;
  private final GeminiThrottleChunkListener throttleListener;

  @Value("#{T(java.time.YearMonth).parse(jobParameters['targetMonth'])}")
  private YearMonth targetMonth;

  @Override
  public MonthlyReportData process(User user) throws Exception {
    log.debug("통계 처리 중 - userId={}, targetMonth={}", user.getId(), targetMonth);

    CalculatedStats stats = calculator.calculate(user, targetMonth);

    if (!stats.hasActivity()) {
      log.debug("활동 데이터 부족으로 리포트 건너뜀 - userId={}", user.getId());
      return null; // null 반환 시 Spring Batch가 writer 호출 생략
    }

    // 한 번의 Gemini 호출로 인사이트와 팁노트를 함께 생성한다 (호출 횟수는 기존과 동일).
    TipNoteMaterials materials = materialCollector.collect(user, targetMonth);
    MonthlyAiResult result = aiService.generate(stats, targetMonth, materials);
    throttleListener.markAiCalled(); // sleep은 트랜잭션 밖 afterChunk()에서 실행

    return new MonthlyReportData(
        user, targetMonth.atDay(1), stats, result.aiInsight(), result.tipNote());
  }
}
