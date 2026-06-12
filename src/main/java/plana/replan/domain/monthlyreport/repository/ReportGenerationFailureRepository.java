package plana.replan.domain.monthlyreport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.monthlyreport.entity.ReportGenerationFailure;

public interface ReportGenerationFailureRepository
    extends JpaRepository<ReportGenerationFailure, Long> {

  List<ReportGenerationFailure> findByRetryCountLessThan(int maxRetryCount);
}
