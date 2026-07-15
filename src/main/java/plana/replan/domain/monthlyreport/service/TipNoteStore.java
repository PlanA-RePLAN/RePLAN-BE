package plana.replan.domain.monthlyreport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.entity.TipNoteAction;
import plana.replan.domain.monthlyreport.entity.TipNoteItem;
import plana.replan.domain.monthlyreport.repository.TipNoteItemRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.repository.TagRepository;

/**
 * 팁노트 초안을 리포트에 저장한다. 같은 달을 다시 생성(재시도·Dev 재생성)해도 카드가 불어나지 않도록 기존 카드를 전부 지우고 새로 쓴다. 호출자는 트랜잭션 안이어야
 * 한다(배치 청크 / 서비스 @Transactional).
 */
@Service
@RequiredArgsConstructor
public class TipNoteStore {

  private final TipNoteItemRepository tipNoteItemRepository;
  private final RoutineRepository routineRepository;
  private final TagRepository tagRepository;

  public void replace(MonthlyReport report, TipNoteDraft draft) {
    report.updateTipNoteText(draft != null ? draft.tip() : null);
    tipNoteItemRepository.deleteAllByMonthlyReport(report);
    if (draft == null) {
      return;
    }
    int order = 0;
    for (TipNoteDraft.Item item : draft.items()) {
      Routine targetRoutine =
          item.targetRoutineId() != null
              ? routineRepository.findById(item.targetRoutineId()).orElse(null)
              : null;
      // 파서 검증 후 저장 사이에 루틴이 사라진 극단 케이스 — 수정 카드만 조용히 건너뛴다.
      if (item.action() == TipNoteAction.MODIFY_ROUTINE && targetRoutine == null) {
        continue;
      }
      Tag tag = item.tagId() != null ? tagRepository.findById(item.tagId()).orElse(null) : null;
      tipNoteItemRepository.save(
          TipNoteItem.builder()
              .monthlyReport(report)
              .action(item.action())
              .targetRoutine(targetRoutine)
              .title(item.title())
              .tag(tag)
              .todoDueAt(item.todoDueAt())
              .routineEndAt(item.routineEndAt())
              .routineTime(item.routineTime())
              .routineType(item.routineType())
              .routineDays(item.routineDays())
              .changedFields(item.changedFields())
              .sortOrder(order++)
              .build());
    }
  }
}
