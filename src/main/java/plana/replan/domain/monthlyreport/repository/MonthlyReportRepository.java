package plana.replan.domain.monthlyreport.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.user.entity.User;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

  Optional<MonthlyReport> findByUserAndReportMonth(User user, LocalDate reportMonth);
}
