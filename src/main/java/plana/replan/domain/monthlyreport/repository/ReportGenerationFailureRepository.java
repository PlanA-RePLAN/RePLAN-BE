package plana.replan.domain.monthlyreport.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.monthlyreport.entity.ReportGenerationFailure;
import plana.replan.domain.user.entity.User;

public interface ReportGenerationFailureRepository
    extends JpaRepository<ReportGenerationFailure, Long> {

  // @SQLRestriction("deleted_at IS NULL")이 적용되어 있으므로 소프트 삭제된 레코드는 자동 제외
  List<ReportGenerationFailure> findByRetryCountLessThan(int maxRetryCount);

  Optional<ReportGenerationFailure> findByUserAndTargetMonth(User user, LocalDate targetMonth);
}
