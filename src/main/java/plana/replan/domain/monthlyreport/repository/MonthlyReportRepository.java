package plana.replan.domain.monthlyreport.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.user.entity.User;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

  Optional<MonthlyReport> findByUserAndReportMonth(User user, LocalDate reportMonth);

  /** 유저의 가장 최근 팁노트(작성 팁이 있는 리포트). 추천 카드는 이 최신 노트에서만 보여주고 반영할 수 있다. */
  Optional<MonthlyReport> findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(User user);
}
