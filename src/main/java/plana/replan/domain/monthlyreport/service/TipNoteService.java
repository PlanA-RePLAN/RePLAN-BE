package plana.replan.domain.monthlyreport.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyRequest;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyResponse;
import plana.replan.domain.monthlyreport.dto.TipNoteResponse;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.entity.TipNoteItem;
import plana.replan.domain.monthlyreport.entity.TipNoteItemStatus;
import plana.replan.domain.monthlyreport.exception.MonthlyReportErrorCode;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.monthlyreport.repository.TipNoteItemRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineUpdateRequestDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

/**
 * 팁노트 조회·반영·끝내기. 반영은 새 로직 없이 사용자가 직접 투두/루틴을 만들고 고칠 때 타는 기존 서비스(TodoService.createTodo,
 * RoutineService.createRoutine/updateMotherRoutine)를 그대로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class TipNoteService {

  private final UserRepository userRepository;
  private final MonthlyReportRepository monthlyReportRepository;
  private final TipNoteItemRepository tipNoteItemRepository;
  private final RoutineRepository routineRepository;
  private final TagRepository tagRepository;
  private final TodoService todoService;
  private final RoutineService routineService;
  private final Clock clock;

  @Transactional(readOnly = true)
  public TipNoteResponse getTipNote(Long userId, int year, int month) {
    User user = findUser(userId);
    MonthlyReport report =
        monthlyReportRepository
            .findByUserAndReportMonth(user, YearMonth.of(year, month).atDay(1))
            .filter(r -> r.getTipNoteText() != null)
            .orElseThrow(() -> new CustomException(MonthlyReportErrorCode.TIP_NOTE_NOT_FOUND));

    // 추천 카드는 최신 팁노트에서만 보여준다. 지난 달들은 작성 팁 카드만 (PM 결정).
    List<TipNoteResponse.TipNoteItemResponse> items = List.of();
    if (isLatest(user, report)) {
      LocalDateTime now = LocalDateTime.now(clock);
      items =
          tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report).stream()
              .filter(item -> isVisible(item, now))
              .map(this::toItemResponse)
              .toList();
    }

    return new TipNoteResponse(
        report.getId(),
        report.getReportMonth().getYear(),
        report.getReportMonth().getMonthValue(),
        report.getTipNoteText(),
        items);
  }

  @Transactional
  public TipNoteApplyResponse apply(Long userId, Long noteId, TipNoteApplyRequest request) {
    MonthlyReport report = findOwnedLatestNote(userId, noteId);

    Map<Long, TipNoteItem> itemsById = new LinkedHashMap<>();
    for (TipNoteItem item :
        tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report)) {
      itemsById.put(item.getId(), item);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    List<TipNoteResponse.TipNoteItemResponse> applied = new ArrayList<>();
    for (Long itemId : request.itemIds()) {
      TipNoteItem item = itemsById.get(itemId);
      if (item == null) {
        throw new CustomException(MonthlyReportErrorCode.TIP_NOTE_ITEM_NOT_FOUND);
      }
      // 화면에서 숨겨지는 카드(처리됨/기한 지남/루틴 삭제·직접 수정)는 반영도 거부한다.
      if (!isVisible(item, now)) {
        throw new CustomException(MonthlyReportErrorCode.TIP_NOTE_ITEM_NOT_APPLICABLE);
      }
      applyItem(userId, item);
      item.markApplied();
      applied.add(toItemResponse(item));
    }
    return new TipNoteApplyResponse(applied);
  }

  @Transactional
  public void dismiss(Long userId, Long noteId) {
    MonthlyReport report = findOwnedLatestNote(userId, noteId);
    tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report).stream()
        .filter(item -> item.getStatus() == TipNoteItemStatus.PENDING)
        .forEach(TipNoteItem::markDismissed);
  }

  /** 카드 1장을 실제 투두/루틴에 반영한다 — 기존 생성·수정 서비스를 그대로 호출. */
  private void applyItem(Long userId, TipNoteItem item) {
    Long tagId = aliveTagId(item);
    switch (item.getAction()) {
      case ADD_TODO -> todoService.createTodo(
          userId, new TodoCreateRequestDto(item.getTitle(), item.getTodoDueAt(), tagId, null));
      case ADD_ROUTINE -> routineService.createRoutine(
          userId,
          new RoutineCreateRequestDto(
              item.getTitle(),
              item.getRoutineEndAt(),
              item.getRoutineTime(),
              item.getRoutineType(),
              item.getRoutineDays(),
              tagId,
              null));
      case MODIFY_ROUTINE -> routineService.updateMotherRoutine(
          userId,
          item.getTargetRoutine().getId(),
          new RoutineUpdateRequestDto(
              item.getTitle(),
              item.getRoutineEndAt(),
              item.getRoutineTime(),
              item.getRoutineType(),
              item.getRoutineDays(),
              tagId));
    }
  }

  /**
   * 카드가 화면에 보여도 되는지 판정한다. 조회와 반영이 같은 규칙을 쓴다.
   *
   * <ol>
   *   <li>처리 안 된 카드(PENDING)만
   *   <li>기한이 안 지났을 것 — 새 투두는 마감일시, 루틴 카드는 반복 종료일(무기한 null은 통과)
   *   <li>수정 카드는 대상 루틴이 살아있을 것 (유저가 지웠으면 숨김)
   *   <li>수정 카드는 팁노트 생성 뒤 유저가 그 루틴을 직접 고치지 않았을 것 — 낡은 제안으로 유저 수정을 덮는 사고 방지
   * </ol>
   */
  private boolean isVisible(TipNoteItem item, LocalDateTime now) {
    if (item.getStatus() != TipNoteItemStatus.PENDING) {
      return false;
    }
    return switch (item.getAction()) {
      case ADD_TODO -> item.getTodoDueAt() == null || !item.getTodoDueAt().isBefore(now);
      case ADD_ROUTINE -> item.getRoutineEndAt() == null || !item.getRoutineEndAt().isBefore(now);
      case MODIFY_ROUTINE -> {
        if (item.getRoutineEndAt() != null && item.getRoutineEndAt().isBefore(now)) {
          yield false;
        }
        Routine routine = aliveTargetRoutine(item);
        if (routine == null) {
          yield false;
        }
        // 루틴의 마지막 수정 시각이 카드 생성 시각(=팁노트 생성 시각)보다 나중이면 유저가 직접 고친 것.
        yield routine.getUpdatedAt() == null
            || !routine.getUpdatedAt().isAfter(item.getCreatedAt());
      }
    };
  }

  /** 수정 대상 루틴이 아직 살아있으면(삭제 안 됨 + 활성 + 엄마 루틴) 반환, 아니면 null. */
  private Routine aliveTargetRoutine(TipNoteItem item) {
    if (item.getTargetRoutine() == null) {
      return null;
    }
    return routineRepository
        .findById(item.getTargetRoutine().getId())
        .filter(Routine::isMother)
        .filter(Routine::isActive)
        .orElse(null);
  }

  /** 카드의 태그가 그새 삭제됐으면 태그 없이 반영한다 (반영 전체가 실패하지 않도록). */
  private Long aliveTagId(TipNoteItem item) {
    Tag tag = aliveTag(item);
    return tag != null ? tag.getId() : null;
  }

  /**
   * 카드의 태그를 안전하게 가져온다. 소프트 삭제된 태그는 연관관계로 바로 접근하면 로딩 예외가 나므로, 리포지토리 조회(삭제 행 제외)로 확인해 없으면 null 처리한다.
   */
  private Tag aliveTag(TipNoteItem item) {
    if (item.getTag() == null) {
      return null;
    }
    return tagRepository.findById(item.getTag().getId()).orElse(null);
  }

  /** 본인 소유이면서 최신인 팁노트만 반영/끝내기 대상이 된다. */
  private MonthlyReport findOwnedLatestNote(Long userId, Long noteId) {
    User user = findUser(userId);
    MonthlyReport report =
        monthlyReportRepository
            .findById(noteId)
            .filter(r -> r.getUser().getId().equals(userId))
            .filter(r -> r.getTipNoteText() != null)
            .orElseThrow(() -> new CustomException(MonthlyReportErrorCode.TIP_NOTE_NOT_FOUND));
    if (!isLatest(user, report)) {
      throw new CustomException(MonthlyReportErrorCode.TIP_NOTE_NOT_LATEST);
    }
    return report;
  }

  private boolean isLatest(User user, MonthlyReport report) {
    return monthlyReportRepository
        .findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(user)
        .map(latest -> latest.getId().equals(report.getId()))
        .orElse(false);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND));
  }

  private TipNoteResponse.TipNoteItemResponse toItemResponse(TipNoteItem item) {
    Tag tag = aliveTag(item);
    return new TipNoteResponse.TipNoteItemResponse(
        item.getId(),
        item.getAction().name(),
        item.getTitle(),
        tag != null ? tag.getTitle() : null,
        tag != null ? tag.getColor() : null,
        item.getTodoDueAt(),
        item.getRoutineEndAt(),
        item.getRoutineTime(),
        item.getRoutineType() != null ? item.getRoutineType().name() : null,
        item.getRoutineDays(),
        item.getChangedFields() == null
            ? List.of()
            : item.getChangedFields().stream()
                .map(
                    cf ->
                        new TipNoteResponse.ChangedFieldResponse(
                            cf.field(), cf.before(), cf.after()))
                .toList());
  }
}
