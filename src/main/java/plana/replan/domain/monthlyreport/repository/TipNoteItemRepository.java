package plana.replan.domain.monthlyreport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.entity.TipNoteItem;

public interface TipNoteItemRepository extends JpaRepository<TipNoteItem, Long> {

  List<TipNoteItem> findAllByMonthlyReportOrderBySortOrderAscIdAsc(MonthlyReport monthlyReport);

  void deleteAllByMonthlyReport(MonthlyReport monthlyReport);

  /**
   * 카드가 아직 PENDING일 때만 APPLIED로 바꾼다(바뀐 행 수 반환). 같은 카드에 반영 요청이 동시에 두 번 들어와도(더블탭 등) DB가 한 요청만 통과시키므로
   * 투두/루틴이 중복 생성되지 않는다 — 늦은 요청은 0을 돌려받아 거부된다.
   */
  @Modifying
  @Query(
      "UPDATE TipNoteItem t SET t.status = plana.replan.domain.monthlyreport.entity.TipNoteItemStatus.APPLIED"
          + " WHERE t.id = :id AND t.status = plana.replan.domain.monthlyreport.entity.TipNoteItemStatus.PENDING")
  int markAppliedIfPending(@Param("id") Long id);
}
