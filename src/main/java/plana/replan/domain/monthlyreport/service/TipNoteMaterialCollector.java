package plana.replan.domain.monthlyreport.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.ReplanRecord;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.RoutineSnapshot;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.TagOption;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.UncompletedTodo;
import plana.replan.domain.replan.entity.FailureReasonCode;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.routine.util.RoutineDays;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;

/** 팁노트 생성 재료를 모은다. 지난달 데이터는 리포트 통계와 같은 (해당 월 1일 ~ 다음 달 1일) 범위를 쓴다. */
@Service
@RequiredArgsConstructor
public class TipNoteMaterialCollector {

  private final TodoRepository todoRepository;
  private final ReplanRepository replanRepository;
  private final RoutineRepository routineRepository;
  private final TagRepository tagRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public TipNoteMaterials collect(User user, YearMonth targetMonth) {
    LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
    LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();

    List<UncompletedTodo> uncompleted =
        todoRepository.findMonthlyTodos(user, start, end).stream()
            .filter(t -> !t.isCompleted())
            .map(
                t ->
                    new UncompletedTodo(
                        t.getTitle(),
                        t.getDueDate(),
                        t.getTag() != null ? t.getTag().getTitle() : null,
                        t.getRoutine() != null))
            .toList();

    List<ReplanRecord> replans =
        replanRepository.findByUserAndCreatedAtBetween(user, start, end).stream()
            .map(r -> new ReplanRecord(titleOf(r), reasonLabels(r)))
            .toList();

    // 수정 제안 대상은 "지금 살아있고 아직 안 끝난" 엄마 루틴만 — 종료일 지난 루틴을 고치라는 제안을 막는다.
    // 종료일은 자정(00:00)으로 저장되지만 "그 날짜까지 반복"이라는 뜻이라 날짜 단위로 비교한다(종료일 당일은 포함).
    LocalDate today = LocalDate.now(clock);
    List<RoutineSnapshot> routines =
        routineRepository.findAllActiveMotherRoutinesByUser(user.getId()).stream()
            .filter(r -> r.getDueDate() == null || !r.getDueDate().toLocalDate().isBefore(today))
            .map(
                r ->
                    new RoutineSnapshot(
                        r.getId(),
                        r.getTitle(),
                        r.getRoutineType(),
                        RoutineDays.toDays(r.getRoutineType(), r.getRoutineDate()),
                        r.getRoutineTime(),
                        r.getDueDate(),
                        r.getTag() != null ? r.getTag().getId() : null,
                        r.getTag() != null ? r.getTag().getTitle() : null))
            .toList();

    List<TagOption> tags =
        tagRepository.findAllByUserId(user.getId()).stream()
            .map(t -> new TagOption(t.getId(), t.getTitle()))
            .toList();

    return new TipNoteMaterials(uncompleted, replans, routines, tags);
  }

  private String titleOf(Replan replan) {
    return replan.getTodo() != null ? replan.getTodo().getTitle() : null;
  }

  /** 실패 이유 코드(1~3)를 한글 라벨로 바꾼다. enum에 없는 코드(직접입력)는 원문 그대로. */
  private List<String> reasonLabels(Replan replan) {
    List<String> labels = new ArrayList<>();
    for (String code :
        new String[] {
          replan.getFailureReason1(), replan.getFailureReason2(), replan.getFailureReason3()
        }) {
      if (code == null || code.isBlank()) {
        continue;
      }
      try {
        labels.add(FailureReasonCode.valueOf(code).getLabel());
      } catch (IllegalArgumentException e) {
        labels.add(code);
      }
    }
    return labels;
  }
}
