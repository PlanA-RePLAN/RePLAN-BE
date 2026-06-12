package plana.replan.domain.monthlyreport.service;

import java.math.BigDecimal;
import plana.replan.domain.monthlyreport.entity.AnalysisData;

public record CalculatedStats(
    int totalTodos,
    int completedTodos,
    BigDecimal achievementRate,
    BigDecimal prevMonthDiff,
    int replanCount,
    BigDecimal replanAchievementEffect,
    AnalysisData analysisData,
    boolean hasActivity) {}
