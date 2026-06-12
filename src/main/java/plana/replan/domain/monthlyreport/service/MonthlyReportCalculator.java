package plana.replan.domain.monthlyreport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.monthlyreport.entity.AnalysisData;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.replan.entity.FailureReasonCode;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;

@Service
@RequiredArgsConstructor
public class MonthlyReportCalculator {

  private static final Map<DayOfWeek, String> DAY_NAMES =
      Map.of(
          DayOfWeek.MONDAY, "월요일",
          DayOfWeek.TUESDAY, "화요일",
          DayOfWeek.WEDNESDAY, "수요일",
          DayOfWeek.THURSDAY, "목요일",
          DayOfWeek.FRIDAY, "금요일",
          DayOfWeek.SATURDAY, "토요일",
          DayOfWeek.SUNDAY, "일요일");

  private final TodoRepository todoRepository;
  private final ReplanRepository replanRepository;
  private final MonthlyReportRepository monthlyReportRepository;

  @Transactional(readOnly = true)
  public CalculatedStats calculate(User user, YearMonth targetMonth) {
    LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
    LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();

    List<Todo> todos = todoRepository.findMonthlyTodos(user, start, end);
    int totalTodos = todos.size();
    int completedTodos = (int) todos.stream().filter(Todo::isCompleted).count();

    BigDecimal achievementRate =
        totalTodos == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(completedTodos * 100.0 / totalTodos)
                .setScale(2, RoundingMode.HALF_UP);

    BigDecimal prevMonthDiff = calcPrevMonthDiff(user, targetMonth, achievementRate);

    List<Replan> replans = replanRepository.findByUserAndCreatedAtBetween(user, start, end);
    int replanCount = replans.size();

    BigDecimal replanAchievementEffect = null;
    if (replanCount > 0) {
      List<Todo> replanDerived = todoRepository.findReplanDerivedMonthlyTodos(user, start, end);
      long completedDerived = replanDerived.stream().filter(Todo::isCompleted).count();
      replanAchievementEffect =
          BigDecimal.valueOf(completedDerived * 100.0 / replanCount)
              .setScale(2, RoundingMode.HALF_UP);
    }

    AnalysisData analysisData = buildAnalysisData(todos, replans);

    boolean hasActivity = false;
    if (totalTodos > 0) {
      // 첫 투두 생성일이 대상 월 말일 기준 14일 이상 이전이어야 의미 있는 통계로 인정
      LocalDateTime endOfMonth = targetMonth.atEndOfMonth().atTime(23, 59, 59);
      hasActivity =
          todos.stream()
              .map(t -> t.getCreatedAt())
              .filter(Objects::nonNull)
              .min(Comparator.naturalOrder())
              .map(first -> ChronoUnit.DAYS.between(first, endOfMonth) >= 14)
              .orElse(false);
    }

    return new CalculatedStats(
        totalTodos,
        completedTodos,
        achievementRate,
        prevMonthDiff,
        replanCount,
        replanAchievementEffect,
        analysisData,
        hasActivity);
  }

  private BigDecimal calcPrevMonthDiff(
      User user, YearMonth targetMonth, BigDecimal achievementRate) {
    YearMonth prevMonth = targetMonth.minusMonths(1);
    Optional<MonthlyReport> prevReport =
        monthlyReportRepository.findByUserAndReportMonth(user, prevMonth.atDay(1));

    if (prevReport.isPresent() && prevReport.get().getAchievementRate() != null) {
      return achievementRate.subtract(prevReport.get().getAchievementRate());
    }

    LocalDateTime prevStart = prevMonth.atDay(1).atStartOfDay();
    LocalDateTime prevEnd = targetMonth.atDay(1).atStartOfDay();
    List<Todo> prevTodos = todoRepository.findMonthlyTodos(user, prevStart, prevEnd);

    if (prevTodos.isEmpty()) return null;

    long prevCompleted = prevTodos.stream().filter(Todo::isCompleted).count();
    BigDecimal prevRate =
        BigDecimal.valueOf(prevCompleted * 100.0 / prevTodos.size())
            .setScale(2, RoundingMode.HALF_UP);
    return achievementRate.subtract(prevRate);
  }

  private AnalysisData buildAnalysisData(List<Todo> todos, List<Replan> replans) {
    AnalysisData.TagStat bestTag = null;
    AnalysisData.TagStat worstTag = null;
    AnalysisData.DayStat bestDay = null;
    AnalysisData.DayStat worstDay = null;

    if (!todos.isEmpty()) {
      Map<String, long[]> tagStats = new HashMap<>();
      Map<DayOfWeek, long[]> dayStats = new EnumMap<>(DayOfWeek.class);

      for (Todo t : todos) {
        if (t.getTag() != null) {
          String tagName = t.getTag().getTitle();
          tagStats.computeIfAbsent(tagName, k -> new long[2]);
          tagStats.get(tagName)[0]++;
          if (t.isCompleted()) tagStats.get(tagName)[1]++;
        }
        if (t.getDueDate() != null) {
          DayOfWeek day = t.getDueDate().getDayOfWeek();
          dayStats.computeIfAbsent(day, k -> new long[2]);
          dayStats.get(day)[0]++;
          if (t.isCompleted()) dayStats.get(day)[1]++;
        }
      }

      Map<String, Double> tagRates =
          tagStats.entrySet().stream()
              .filter(e -> e.getValue()[0] >= 2)
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, e -> e.getValue()[1] * 100.0 / e.getValue()[0]));

      if (!tagRates.isEmpty()) {
        String best = Collections.max(tagRates.entrySet(), Map.Entry.comparingByValue()).getKey();
        String worst = Collections.min(tagRates.entrySet(), Map.Entry.comparingByValue()).getKey();
        bestTag = new AnalysisData.TagStat(best, tagRates.get(best));
        worstTag = new AnalysisData.TagStat(worst, tagRates.get(worst));
      }

      Map<DayOfWeek, Double> dayRates =
          dayStats.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, e -> e.getValue()[1] * 100.0 / e.getValue()[0]));

      if (!dayRates.isEmpty()) {
        DayOfWeek bestDow =
            Collections.max(dayRates.entrySet(), Map.Entry.comparingByValue()).getKey();
        DayOfWeek worstDow =
            Collections.min(dayRates.entrySet(), Map.Entry.comparingByValue()).getKey();
        bestDay = new AnalysisData.DayStat(DAY_NAMES.get(bestDow), dayRates.get(bestDow));
        worstDay = new AnalysisData.DayStat(DAY_NAMES.get(worstDow), dayRates.get(worstDow));
      }
    }

    String topFailureReason = null;
    List<AnalysisData.FailureItem> failureDistribution = List.of();
    List<AnalysisData.PatternCombination> patterns = List.of();

    if (!replans.isEmpty()) {
      Map<String, Integer> reasonCount = new HashMap<>();
      for (Replan r : replans) {
        addReasonCount(reasonCount, r.getFailureReason1());
        addReasonCount(reasonCount, r.getFailureReason2());
        addReasonCount(reasonCount, r.getFailureReason3());
      }

      int total = reasonCount.values().stream().mapToInt(Integer::intValue).sum();
      failureDistribution =
          reasonCount.entrySet().stream()
              .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
              .map(
                  e ->
                      new AnalysisData.FailureItem(
                          e.getKey(), e.getValue(), total > 0 ? e.getValue() * 100.0 / total : 0.0))
              .collect(Collectors.toList());

      topFailureReason = failureDistribution.isEmpty() ? null : failureDistribution.get(0).reason();
      patterns = buildPatterns(replans);
    }

    return new AnalysisData(
        topFailureReason, failureDistribution, bestTag, worstTag, bestDay, worstDay, patterns);
  }

  private void addReasonCount(Map<String, Integer> counts, String code) {
    if (code == null) return;
    counts.merge(toDepth1Label(code), 1, Integer::sum);
  }

  private String toDepth1Label(String code) {
    try {
      FailureReasonCode fc = FailureReasonCode.valueOf(code);
      while (fc.getParent() != null) {
        fc = fc.getParent();
      }
      return fc.getLabel();
    } catch (IllegalArgumentException e) {
      return code;
    }
  }

  private List<AnalysisData.PatternCombination> buildPatterns(List<Replan> replans) {
    Map<String, Integer> tagReasonCount = new HashMap<>();
    Map<String, Integer> dayReasonCount = new HashMap<>();

    for (Replan r : replans) {
      Todo todo = r.getTodo();
      String tagName = todo.getTag() != null ? todo.getTag().getTitle() : null;
      String dayName =
          todo.getDueDate() != null ? DAY_NAMES.get(todo.getDueDate().getDayOfWeek()) : null;

      Stream.of(r.getFailureReason1(), r.getFailureReason2(), r.getFailureReason3())
          .filter(Objects::nonNull)
          .map(this::toDepth1Label)
          .forEach(
              reason -> {
                if (tagName != null) tagReasonCount.merge(reason + "||" + tagName, 1, Integer::sum);
                if (dayName != null) dayReasonCount.merge(reason + "||" + dayName, 1, Integer::sum);
              });
    }

    List<AnalysisData.PatternCombination> result = new ArrayList<>();

    tagReasonCount.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(2)
        .forEach(
            e -> {
              String[] parts = e.getKey().split("\\|\\|");
              result.add(
                  new AnalysisData.PatternCombination(parts[0], parts[1], null, e.getValue()));
            });

    dayReasonCount.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(2)
        .forEach(
            e -> {
              String[] parts = e.getKey().split("\\|\\|");
              result.add(
                  new AnalysisData.PatternCombination(parts[0], null, parts[1], e.getValue()));
            });

    return result;
  }
}
