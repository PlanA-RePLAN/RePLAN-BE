package plana.replan.domain.monthlyreport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.entity.TipNoteItem;

public interface TipNoteItemRepository extends JpaRepository<TipNoteItem, Long> {

  List<TipNoteItem> findAllByMonthlyReportOrderBySortOrderAscIdAsc(MonthlyReport monthlyReport);

  void deleteAllByMonthlyReport(MonthlyReport monthlyReport);
}
