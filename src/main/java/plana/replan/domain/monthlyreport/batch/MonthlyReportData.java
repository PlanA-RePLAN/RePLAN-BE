package plana.replan.domain.monthlyreport.batch;

import java.time.LocalDate;
import plana.replan.domain.monthlyreport.entity.AiInsight;
import plana.replan.domain.monthlyreport.service.CalculatedStats;
import plana.replan.domain.monthlyreport.service.TipNoteDraft;
import plana.replan.domain.user.entity.User;

record MonthlyReportData(
    User user,
    LocalDate reportMonth,
    CalculatedStats stats,
    AiInsight aiInsight,
    TipNoteDraft tipNote) {}
