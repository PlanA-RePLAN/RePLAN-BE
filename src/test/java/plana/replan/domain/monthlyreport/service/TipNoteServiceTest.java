package plana.replan.domain.monthlyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyRequest;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyResponse;
import plana.replan.domain.monthlyreport.dto.TipNoteResponse;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.entity.TipNoteAction;
import plana.replan.domain.monthlyreport.entity.TipNoteItem;
import plana.replan.domain.monthlyreport.entity.TipNoteItemStatus;
import plana.replan.domain.monthlyreport.exception.MonthlyReportErrorCode;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.monthlyreport.repository.TipNoteItemRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineUpdateRequestDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class TipNoteServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private MonthlyReportRepository monthlyReportRepository;
  @Mock private TipNoteItemRepository tipNoteItemRepository;
  @Mock private RoutineRepository routineRepository;
  @Mock private TagRepository tagRepository;
  @Mock private TodoService todoService;
  @Mock private RoutineService routineService;

  private TipNoteService tipNoteService;

  // 지금 = 2026-07-15 12:00 (한국시간)
  private static final Instant NOW = Instant.parse("2026-07-15T03:00:00Z");
  private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 7, 1, 0, 5);

  private User user;
  private MonthlyReport report;

  @BeforeEach
  void setUp() {
    tipNoteService =
        new TipNoteService(
            userRepository,
            monthlyReportRepository,
            tipNoteItemRepository,
            routineRepository,
            tagRepository,
            todoService,
            routineService,
            Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    user =
        User.builder()
            .email("test@test.com")
            .nickname("테스트")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);

    report = MonthlyReport.builder().user(user).reportMonth(LocalDate.of(2026, 6, 1)).build();
    ReflectionTestUtils.setField(report, "id", 17L);
    report.updateTipNoteText("작성 팁");
  }

  private void stubOwnedLatestNote() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findById(17L)).willReturn(Optional.of(report));
    given(
            monthlyReportRepository.findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(
                user))
        .willReturn(Optional.of(report));
  }

  private TipNoteItem addTodoItem(Long id, LocalDateTime dueAt) {
    TipNoteItem item =
        TipNoteItem.builder()
            .monthlyReport(report)
            .action(TipNoteAction.ADD_TODO)
            .title("새 투두")
            .todoDueAt(dueAt)
            .build();
    ReflectionTestUtils.setField(item, "id", id);
    ReflectionTestUtils.setField(item, "createdAt", GENERATED_AT);
    return item;
  }

  private Routine motherRoutine(Long id, LocalDateTime updatedAt) {
    Routine routine =
        Routine.builder().title("아침 운동").routineType(RoutineType.DAILY).user(user).build();
    ReflectionTestUtils.setField(routine, "id", id);
    ReflectionTestUtils.setField(routine, "updatedAt", updatedAt);
    return routine;
  }

  private TipNoteItem modifyRoutineItem(Long id, Routine target) {
    TipNoteItem item =
        TipNoteItem.builder()
            .monthlyReport(report)
            .action(TipNoteAction.MODIFY_ROUTINE)
            .targetRoutine(target)
            .title("저녁 운동")
            .routineType(RoutineType.DAILY)
            .routineTime(LocalTime.of(20, 0))
            .build();
    ReflectionTestUtils.setField(item, "id", id);
    ReflectionTestUtils.setField(item, "createdAt", GENERATED_AT);
    return item;
  }

  // ── 조회 ──────────────────────────────────────────────

  @Test
  @DisplayName("최신 팁노트가 아니면(지난 달) 카드 없이 작성 팁만 내려간다")
  void getTipNote_notLatest_itemsEmpty() {
    MonthlyReport latest =
        MonthlyReport.builder().user(user).reportMonth(LocalDate.of(2026, 7, 1)).build();
    ReflectionTestUtils.setField(latest, "id", 20L);
    latest.updateTipNoteText("최신 팁");

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findByUserAndReportMonth(user, LocalDate.of(2026, 6, 1)))
        .willReturn(Optional.of(report));
    given(
            monthlyReportRepository.findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(
                user))
        .willReturn(Optional.of(latest));

    TipNoteResponse response = tipNoteService.getTipNote(1L, 2026, 6);

    assertThat(response.tip()).isEqualTo("작성 팁");
    assertThat(response.items()).isEmpty();
  }

  @Test
  @DisplayName("기한 지난 카드·처리된 카드는 조회에서 빠지고 남은 카드만 내려간다")
  void getTipNote_filtersExpiredAndHandled() {
    TipNoteItem alive = addTodoItem(1L, LocalDateTime.of(2026, 7, 20, 23, 59));
    TipNoteItem expired = addTodoItem(2L, LocalDateTime.of(2026, 7, 10, 23, 59));
    TipNoteItem applied = addTodoItem(3L, LocalDateTime.of(2026, 7, 20, 23, 59));
    applied.markApplied();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findByUserAndReportMonth(user, LocalDate.of(2026, 6, 1)))
        .willReturn(Optional.of(report));
    given(
            monthlyReportRepository.findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(
                user))
        .willReturn(Optional.of(report));
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(alive, expired, applied));

    TipNoteResponse response = tipNoteService.getTipNote(1L, 2026, 6);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).id()).isEqualTo(1L);
  }

  @Test
  @DisplayName("반복 종료일이 '오늘'인 루틴 카드는 종료일 당일까지는 보인다 (날짜 단위 비교)")
  void getTipNote_routineEndingToday_stillVisible() {
    TipNoteItem endsToday =
        TipNoteItem.builder()
            .monthlyReport(report)
            .action(TipNoteAction.ADD_ROUTINE)
            .title("오늘까지 반복")
            .routineType(RoutineType.DAILY)
            .routineEndAt(LocalDate.of(2026, 7, 15).atStartOfDay()) // 오늘 자정
            .build();
    ReflectionTestUtils.setField(endsToday, "id", 1L);
    ReflectionTestUtils.setField(endsToday, "createdAt", GENERATED_AT);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findByUserAndReportMonth(user, LocalDate.of(2026, 6, 1)))
        .willReturn(Optional.of(report));
    given(
            monthlyReportRepository.findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(
                user))
        .willReturn(Optional.of(report));
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(endsToday));

    TipNoteResponse response = tipNoteService.getTipNote(1L, 2026, 6);

    assertThat(response.items()).hasSize(1);
  }

  @Test
  @DisplayName("팁노트 생성 뒤 유저가 직접 고친 루틴의 수정 카드는 숨긴다")
  void getTipNote_hidesUserEditedRoutineCard() {
    Routine editedAfter = motherRoutine(5L, GENERATED_AT.plusDays(3)); // 생성 후 수정됨
    Routine untouched = motherRoutine(6L, GENERATED_AT.minusDays(1)); // 안 건드림
    TipNoteItem hidden = modifyRoutineItem(1L, editedAfter);
    TipNoteItem visible = modifyRoutineItem(2L, untouched);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findByUserAndReportMonth(user, LocalDate.of(2026, 6, 1)))
        .willReturn(Optional.of(report));
    given(
            monthlyReportRepository.findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(
                user))
        .willReturn(Optional.of(report));
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(hidden, visible));
    given(routineRepository.findById(5L)).willReturn(Optional.of(editedAfter));
    given(routineRepository.findById(6L)).willReturn(Optional.of(untouched));

    TipNoteResponse response = tipNoteService.getTipNote(1L, 2026, 6);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).id()).isEqualTo(2L);
  }

  @Test
  @DisplayName("삭제된 루틴의 수정 카드는 숨긴다")
  void getTipNote_hidesDeletedRoutineCard() {
    Routine deleted = motherRoutine(5L, null);
    TipNoteItem card = modifyRoutineItem(1L, deleted);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findByUserAndReportMonth(user, LocalDate.of(2026, 6, 1)))
        .willReturn(Optional.of(report));
    given(
            monthlyReportRepository.findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(
                user))
        .willReturn(Optional.of(report));
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(card));
    given(routineRepository.findById(5L)).willReturn(Optional.empty()); // 소프트 삭제됨

    TipNoteResponse response = tipNoteService.getTipNote(1L, 2026, 6);

    assertThat(response.items()).isEmpty();
  }

  @Test
  @DisplayName("팁노트가 없는 달은 TIP_NOTE_NOT_FOUND")
  void getTipNote_missing_throwsNotFound() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findByUserAndReportMonth(user, LocalDate.of(2026, 3, 1)))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> tipNoteService.getTipNote(1L, 2026, 3))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MonthlyReportErrorCode.TIP_NOTE_NOT_FOUND);
  }

  // ── 반영 ──────────────────────────────────────────────

  @Test
  @DisplayName("체크한 카드만 반영: 종류별로 기존 서비스를 호출하고 APPLIED로 바꾼다")
  void apply_invokesExistingServicesPerAction() {
    Routine target = motherRoutine(5L, GENERATED_AT.minusDays(1));
    TipNoteItem todoCard = addTodoItem(1L, LocalDateTime.of(2026, 7, 20, 23, 59));
    TipNoteItem modifyCard = modifyRoutineItem(2L, target);
    TipNoteItem unchecked = addTodoItem(3L, LocalDateTime.of(2026, 7, 25, 23, 59));

    stubOwnedLatestNote();
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(todoCard, modifyCard, unchecked));
    given(routineRepository.findById(5L)).willReturn(Optional.of(target));
    given(tipNoteItemRepository.markAppliedIfPending(anyLong())).willReturn(1);

    TipNoteApplyResponse response =
        tipNoteService.apply(1L, 17L, new TipNoteApplyRequest(List.of(1L, 2L)));

    verify(todoService).createTodo(eq(1L), any(TodoCreateRequestDto.class));
    verify(routineService).updateMotherRoutine(eq(1L), eq(5L), any(RoutineUpdateRequestDto.class));
    verify(routineService, never()).createRoutine(anyLong(), any(RoutineCreateRequestDto.class));

    assertThat(response.appliedItems()).hasSize(2);
    assertThat(todoCard.getStatus()).isEqualTo(TipNoteItemStatus.APPLIED);
    assertThat(modifyCard.getStatus()).isEqualTo(TipNoteItemStatus.APPLIED);
    assertThat(unchecked.getStatus()).isEqualTo(TipNoteItemStatus.PENDING);
  }

  @Test
  @DisplayName("같은 카드 동시 반영: DB 조건부 갱신에서 밀린 요청은 거부되고 중복 생성이 없다")
  void apply_concurrentDuplicate_rejected() {
    TipNoteItem card = addTodoItem(1L, LocalDateTime.of(2026, 7, 20, 23, 59));

    stubOwnedLatestNote();
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(card));
    // 다른 요청이 먼저 APPLIED로 바꿔서 이 요청의 조건부 갱신은 0건
    given(tipNoteItemRepository.markAppliedIfPending(1L)).willReturn(0);

    assertThatThrownBy(() -> tipNoteService.apply(1L, 17L, new TipNoteApplyRequest(List.of(1L))))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MonthlyReportErrorCode.TIP_NOTE_ITEM_NOT_APPLICABLE);
    verify(todoService, never()).createTodo(anyLong(), any(TodoCreateRequestDto.class));
  }

  @Test
  @DisplayName("기한이 지나 화면에서 숨겨지는 카드는 반영도 거부한다")
  void apply_expiredCard_rejected() {
    TipNoteItem expired = addTodoItem(1L, LocalDateTime.of(2026, 7, 10, 23, 59));

    stubOwnedLatestNote();
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(expired));

    assertThatThrownBy(() -> tipNoteService.apply(1L, 17L, new TipNoteApplyRequest(List.of(1L))))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MonthlyReportErrorCode.TIP_NOTE_ITEM_NOT_APPLICABLE);
  }

  @Test
  @DisplayName("최신이 아닌 팁노트는 반영할 수 없다 (TIP_NOTE_NOT_LATEST)")
  void apply_notLatestNote_rejected() {
    MonthlyReport latest =
        MonthlyReport.builder().user(user).reportMonth(LocalDate.of(2026, 7, 1)).build();
    ReflectionTestUtils.setField(latest, "id", 20L);
    latest.updateTipNoteText("최신 팁");

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findById(17L)).willReturn(Optional.of(report));
    given(
            monthlyReportRepository.findFirstByUserAndTipNoteTextIsNotNullOrderByReportMonthDesc(
                user))
        .willReturn(Optional.of(latest));

    assertThatThrownBy(() -> tipNoteService.apply(1L, 17L, new TipNoteApplyRequest(List.of(1L))))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MonthlyReportErrorCode.TIP_NOTE_NOT_LATEST);
  }

  @Test
  @DisplayName("남의 팁노트는 반영할 수 없다 (TIP_NOTE_NOT_FOUND)")
  void apply_othersNote_rejected() {
    User other =
        User.builder()
            .email("other@test.com")
            .nickname("남")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(other, "id", 2L);
    MonthlyReport othersReport =
        MonthlyReport.builder().user(other).reportMonth(LocalDate.of(2026, 6, 1)).build();
    ReflectionTestUtils.setField(othersReport, "id", 30L);
    othersReport.updateTipNoteText("남의 팁");

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(monthlyReportRepository.findById(30L)).willReturn(Optional.of(othersReport));

    assertThatThrownBy(() -> tipNoteService.apply(1L, 30L, new TipNoteApplyRequest(List.of(1L))))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MonthlyReportErrorCode.TIP_NOTE_NOT_FOUND);
  }

  // ── 끝내기 ────────────────────────────────────────────

  @Test
  @DisplayName("반영 없이 끝내기: 남은 카드만 DISMISSED, 이미 반영된 카드는 그대로")
  void dismiss_marksPendingOnly() {
    TipNoteItem pending = addTodoItem(1L, LocalDateTime.of(2026, 7, 20, 23, 59));
    TipNoteItem applied = addTodoItem(2L, LocalDateTime.of(2026, 7, 20, 23, 59));
    applied.markApplied();

    stubOwnedLatestNote();
    given(tipNoteItemRepository.findAllByMonthlyReportOrderBySortOrderAscIdAsc(report))
        .willReturn(List.of(pending, applied));

    tipNoteService.dismiss(1L, 17L);

    assertThat(pending.getStatus()).isEqualTo(TipNoteItemStatus.DISMISSED);
    assertThat(applied.getStatus()).isEqualTo(TipNoteItemStatus.APPLIED);
  }
}
