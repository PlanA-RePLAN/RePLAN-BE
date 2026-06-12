package plana.replan.domain.monthlyreport.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.monthlyreport.dto.MonthlyReportResponse;
import plana.replan.domain.monthlyreport.entity.AiInsight;
import plana.replan.domain.monthlyreport.entity.AnalysisData;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.entity.ReportGenerationFailure;
import plana.replan.domain.monthlyreport.exception.MonthlyReportErrorCode;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.monthlyreport.repository.ReportGenerationFailureRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportService {

  private final UserRepository userRepository;
  private final MonthlyReportRepository monthlyReportRepository;
  private final ReportGenerationFailureRepository failureRepository;
  private final TagRepository tagRepository;
  private final MonthlyReportCalculator calculator;
  private final MonthlyReportAiService aiService;

  @Transactional
  public MonthlyReportResponse generateReport(Long userId, int year, int month) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND));

    YearMonth targetMonth = YearMonth.of(year, month);
    CalculatedStats stats = calculator.calculate(user, targetMonth);
    final AiInsight aiInsight =
        stats.hasActivity() ? aiService.generateInsight(stats, targetMonth) : null;

    LocalDate reportMonth = targetMonth.atDay(1);
    MonthlyReport report =
        monthlyReportRepository
            .findByUserAndReportMonth(user, reportMonth)
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
                      aiInsight);
                  return existing;
                })
            .orElseGet(
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

    List<Tag> userTags = tagRepository.findAllByUserOrderByCreatedAtDescIdDesc(user);
    Map<String, String> tagColorMap =
        userTags.stream()
            .collect(
                Collectors.toMap(
                    Tag::getTitle, t -> t.getColor() != null ? t.getColor() : "", (a, b) -> a));

    return toResponse(report, tagColorMap);
  }

  @Transactional(readOnly = true)
  public MonthlyReportResponse getReport(Long userId, int year, int month) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND));

    LocalDate reportMonth = YearMonth.of(year, month).atDay(1);

    MonthlyReport report =
        monthlyReportRepository
            .findByUserAndReportMonth(user, reportMonth)
            .orElseThrow(() -> new CustomException(MonthlyReportErrorCode.REPORT_NOT_FOUND));

    List<Tag> userTags = tagRepository.findAllByUserOrderByCreatedAtDescIdDesc(user);
    Map<String, String> tagColorMap =
        userTags.stream()
            .collect(
                Collectors.toMap(
                    Tag::getTitle, t -> t.getColor() != null ? t.getColor() : "", (a, b) -> a));

    return toResponse(report, tagColorMap);
  }

  /**
   * 실패 리포트 1건을 재처리한다. 성공 시 soft delete, 실패 시 retryCount 증가. Gemini 호출 여부(= hasActivity)를 반환해 호출자가
   * sleep을 제어하게 한다.
   */
  @Transactional
  public boolean retryOneFailure(Long failureId) {
    ReportGenerationFailure failure =
        failureRepository.findById(failureId).orElseThrow(RuntimeException::new);
    User user = failure.getUser();
    YearMonth targetMonth = YearMonth.from(failure.getTargetMonth());

    try {
      CalculatedStats stats = calculator.calculate(user, targetMonth);
      AiInsight aiInsight =
          stats.hasActivity() ? aiService.generateInsight(stats, targetMonth) : null;
      upsertReport(user, failure.getTargetMonth(), stats, aiInsight);
      failure.softDelete();
      log.info("재처리 성공 - userId={}", user.getId());
      return stats.hasActivity();
    } catch (Exception e) {
      log.error("재처리 실패 - userId={}", user.getId(), e);
      String msg = e.getMessage();
      failure.incrementRetry(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
      return false;
    }
  }

  @Transactional
  public void upsertReport(
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

  private MonthlyReportResponse toResponse(MonthlyReport report, Map<String, String> tagColorMap) {
    AnalysisData ad = report.getAnalysisData();
    AiInsight ai = report.getAiInsight();

    MonthlyReportResponse.AnalysisDataResponse adResponse = null;
    if (ad != null) {
      adResponse =
          new MonthlyReportResponse.AnalysisDataResponse(
              ad.topFailureReason(),
              toFailureDistribution(ad.failureDistribution()),
              toTagStat(ad.bestAchievementTag(), tagColorMap),
              toTagStat(ad.worstAchievementTag(), tagColorMap),
              toDayStat(ad.bestAchievementDay()),
              toDayStat(ad.worstAchievementDay()),
              toPatterns(ad.patternCombinations()));
    }

    MonthlyReportResponse.AiInsightResponse aiResponse = null;
    if (ai != null) {
      aiResponse =
          new MonthlyReportResponse.AiInsightResponse(
              ai.insights() == null
                  ? List.of()
                  : ai.insights().stream()
                      .map(
                          i ->
                              new MonthlyReportResponse.InsightItemResponse(
                                  i.summary(), i.detail()))
                      .collect(Collectors.toList()),
              ai.writingTip());
    }

    return new MonthlyReportResponse(
        report.getReportMonth().getYear(),
        report.getReportMonth().getMonthValue(),
        report.getTotalTodos() != null ? report.getTotalTodos() : 0,
        report.getCompletedTodos() != null ? report.getCompletedTodos() : 0,
        report.getAchievementRate() != null ? report.getAchievementRate().doubleValue() : 0.0,
        report.getPrevMonthDiff() != null ? report.getPrevMonthDiff().doubleValue() : null,
        report.getReplanCount() != null ? report.getReplanCount() : 0,
        report.getReplanAchievementEffect() != null
            ? report.getReplanAchievementEffect().doubleValue()
            : null,
        adResponse,
        aiResponse);
  }

  private MonthlyReportResponse.TagStatResponse toTagStat(
      AnalysisData.TagStat tagStat, Map<String, String> colorMap) {
    if (tagStat == null) return null;
    String color = colorMap.get(tagStat.tag());
    return new MonthlyReportResponse.TagStatResponse(
        tagStat.tag(), color != null && !color.isEmpty() ? color : null, tagStat.rate());
  }

  private MonthlyReportResponse.DayStatResponse toDayStat(AnalysisData.DayStat dayStat) {
    if (dayStat == null) return null;
    return new MonthlyReportResponse.DayStatResponse(dayStat.day(), dayStat.rate());
  }

  private List<MonthlyReportResponse.FailureDistributionItem> toFailureDistribution(
      List<AnalysisData.FailureItem> items) {
    if (items == null) return List.of();
    return items.stream()
        .map(
            i -> new MonthlyReportResponse.FailureDistributionItem(i.reason(), i.count(), i.rate()))
        .collect(Collectors.toList());
  }

  private List<MonthlyReportResponse.PatternCombinationResponse> toPatterns(
      List<AnalysisData.PatternCombination> combinations) {
    if (combinations == null) return List.of();
    return combinations.stream()
        .map(
            c ->
                new MonthlyReportResponse.PatternCombinationResponse(
                    c.reason(), c.tag(), c.day(), c.count()))
        .collect(Collectors.toList());
  }
}
