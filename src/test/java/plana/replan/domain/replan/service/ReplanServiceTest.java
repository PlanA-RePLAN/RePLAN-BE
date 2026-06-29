package plana.replan.domain.replan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.replan.dto.RecommendInput;
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanAnswer;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.exception.ReplanErrorCode;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.repository.RoutineOverrideRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class ReplanServiceTest {

  @Mock private TodoRepository todoRepository;
  @Mock private ReplanRepository replanRepository;
  @Mock private ReplanAiService aiService;
  @Mock private TagRepository tagRepository;
  @Mock private RoutineRepository routineRepository;
  @Mock private RoutineService routineService;
  @Mock private RoutineOverrideRepository routineOverrideRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-18T00:00:00Z"), ZoneId.of("UTC"));

  private ReplanService replanService;

  @BeforeEach
  void setUp() {
    replanService =
        new ReplanService(
            todoRepository,
            replanRepository,
            aiService,
            tagRepository,
            clock,
            routineRepository,
            routineService,
            routineOverrideRepository);
  }

  private Todo ownedTodo(Long todoId, Long userId) {
    User user = org.mockito.Mockito.mock(User.class);
    given(user.getId()).willReturn(userId);
    Todo todo = org.mockito.Mockito.mock(Todo.class);
    given(todo.getUser()).willReturn(user);
    org.mockito.Mockito.lenient().when(todo.getTitle()).thenReturn("데이터 분석 공부하기");
    return todo;
  }

  @Test
  void 추가질문_필요한_사유는_질문을_반환하고_AI를_부르지_않는다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getId()).willReturn(42L);
    given(todo.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0));
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));

    // GOAL_NO_PRIORITY는 우선순위 투두 선택 질문이 필요한 사유
    ReplanRecommendRequest req =
        new ReplanRecommendRequest(42L, List.of("GOAL_NO_PRIORITY"), null, null);

    ReplanRecommendResponse res = replanService.recommend(1L, req);

    assertThat(res.needsMoreInfo()).isTrue();
    assertThat(res.questions()).hasSize(1);
    assertThat(res.questions().get(0).key()).isEqualTo("priority_targets");
    // 질문 단계에는 reasonLabels를 내려보내지 않는다(최종 추천 화면 전용)
    assertThat(res.reasonLabels()).isNull();
    // "기존 투두 수정 사항" 카드용으로 앵커 투두의 기존 정보를 함께 내려준다
    assertThat(res.anchorTodo()).isNotNull();
    assertThat(res.anchorTodo().todoId()).isEqualTo(42L);
    assertThat(res.anchorTodo().title()).isEqualTo("데이터 분석 공부하기");
    assertThat(res.anchorTodo().dueDate()).isEqualTo(LocalDateTime.of(2026, 6, 25, 11, 0));
    then(aiService).should(never()).generateRecommend(any());
  }

  @Test
  void 남의_투두면_거부() {
    Todo todo = ownedTodo(42L, 999L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));

    ReplanRecommendRequest req =
        new ReplanRecommendRequest(42L, List.of("GOAL_NO_PRIORITY"), null, null);

    assertThatThrownBy(() -> replanService.recommend(1L, req)).isInstanceOf(CustomException.class);
  }

  @Test
  void 질문_불필요한_사유는_바로_추천을_생성한다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 7, 10, 0));
    given(todo.getTag()).willReturn(org.mockito.Mockito.mock(Tag.class));
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(aiService.generateRecommend(any())).willReturn(List.of());

    // INTERRUPT_SUDDEN(돌발 상황)은 추가 질문 없이 바로 추천
    ReplanRecommendRequest req =
        new ReplanRecommendRequest(42L, List.of("INTERRUPT_SUDDEN"), null, null);

    ReplanRecommendResponse res = replanService.recommend(1L, req);

    assertThat(res.needsMoreInfo()).isFalse();
    // 선택 사유의 상위→하위 라벨이 함께 내려간다
    assertThat(res.reasonLabels()).containsExactly("예상치 못한 방해 발생", "돌발 상황이 발생했어요");
    // 추천 단계에는 질문용 앵커 정보를 내려보내지 않는다
    assertThat(res.anchorTodo()).isNull();
  }

  @Test
  void 답변이_있으면_질문단계를_건너뛰고_추천을_생성한다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(aiService.generateRecommend(any())).willReturn(List.of());

    // GOAL_NO_PRIORITY는 원래 질문이 필요하지만, 답변이 있으면 곧바로 추천
    ReplanRecommendRequest req =
        new ReplanRecommendRequest(
            42L,
            List.of("GOAL_NO_PRIORITY"),
            List.of(new ReplanAnswer("priority_targets", null, List.of(11L))),
            null);

    ReplanRecommendResponse res = replanService.recommend(1L, req);

    assertThat(res.needsMoreInfo()).isFalse();
    then(aiService).should(times(1)).generateRecommend(any());
  }

  @Test
  void 필요한_질문중_일부만_답하면_남은_질문을_다시_묻는다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getId()).willReturn(42L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));

    // GOAL_NO_PRIORITY(priority_targets)와 CONDITION_PAIN(pain_area) 둘 다 질문이 필요한데,
    // priority_targets만 답하면 pain_area는 아직 안 왔으므로 질문 단계로 되돌려야 한다.
    ReplanRecommendRequest req =
        new ReplanRecommendRequest(
            42L,
            List.of("GOAL_NO_PRIORITY", "CONDITION_PAIN"),
            List.of(new ReplanAnswer("priority_targets", null, List.of(11L))),
            null);

    ReplanRecommendResponse res = replanService.recommend(1L, req);

    assertThat(res.needsMoreInfo()).isTrue();
    assertThat(res.questions()).extracting(q -> q.key()).containsExactly("pain_area");
    then(aiService).should(never()).generateRecommend(any());
  }

  @Test
  void 추가없이_끝내기여도_실패사유는_저장된다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));

    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of());

    replanService.save(1L, req);

    then(replanRepository).should(times(1)).save(any(Replan.class));
    then(todoRepository).should(never()).save(any(Todo.class));
  }

  @Test
  void ADD작업은_새_투두를_만든다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation add =
        new ReplanOperation(
            ReplanAction.ADD,
            null,
            "데이터 분석 3~4강",
            "2026-06-09",
            "23:59",
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(add));

    replanService.save(1L, req);

    then(todoRepository).should(times(1)).save(any(Todo.class));
  }

  @Test
  void MODIFY_TODO는_기존투두를_치우고_새투두를_만든다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    // 마감 전(2026-06-25) → 실패 전 → softDelete
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0));
    given(anchor.getChildren()).willReturn(List.of());
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "데이터 분석 1~2강",
            "2026-07-02",
            "23:59",
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify));

    replanService.save(1L, req);

    then(anchor).should().softDelete(); // 실패 전이므로 삭제
    then(anchor).should(never()).deactivate();
    // 원본 앵커가 아니라 "새로 만든" 투두를 저장해야 한다(같은 인스턴스를 다시 저장하면 안 됨)
    org.mockito.ArgumentCaptor<Todo> savedTodo = org.mockito.ArgumentCaptor.forClass(Todo.class);
    then(todoRepository).should().save(savedTodo.capture());
    assertThat(savedTodo.getValue()).isNotSameAs(anchor);
  }

  @Test
  void MODIFY_TODO_실패전_앵커를_치우면_리플랜은_새_투두를_가리킨다() {
    // 앵커를 소프트 삭제하면 리플랜이 통계 조회에서 사라지므로, 리플랜은 새 투두를 가리켜야 한다.
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전
    given(anchor.getChildren()).willReturn(List.of());
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));
    org.mockito.ArgumentCaptor<Replan> replanCaptor =
        org.mockito.ArgumentCaptor.forClass(Replan.class);
    org.mockito.ArgumentCaptor<Todo> todoCaptor = org.mockito.ArgumentCaptor.forClass(Todo.class);

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "데이터 분석 1~2강",
            "2026-07-02",
            null,
            null,
            null,
            null,
            List.of());
    replanService.save(
        1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify)));

    then(replanRepository).should().save(replanCaptor.capture());
    then(todoRepository).should().save(todoCaptor.capture());
    assertThat(replanCaptor.getValue().getTodo()).isSameAs(todoCaptor.getValue());
  }

  @Test
  void MODIFY_TODO_같은_투두를_여러_번_리플랜하면_이전_리플랜도_새_투두로_옮긴다() {
    // 같은 투두를 한 달에 두 번 리플랜하면 그 투두에 리플랜이 여러 개 달릴 수 있다.
    // 두 번째 리플랜으로 그 투두를 소프트 삭제할 때 첫 번째 리플랜까지 새 투두로 옮기지 않으면,
    // 첫 번째 리플랜이 통계 조회에서 사라져 한 달에 두 번 한 리플랜이 한 번으로 잡힌다.
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전 → 소프트 삭제
    given(anchor.getChildren()).willReturn(List.of());
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));
    // 같은 투두에 이전부터 달려 있던 리플랜(첫 번째 리플랜)
    Replan previousReplan = org.mockito.Mockito.mock(Replan.class);
    given(replanRepository.findByTodo(anchor))
        .willReturn(new java.util.ArrayList<>(List.of(previousReplan)));

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO, 42L, "데이터 분석 다시", null, null, null, null, null, List.of());
    replanService.save(
        1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify)));

    then(anchor).should().softDelete();
    // 이전 리플랜도 새 투두(원본 앵커가 아닌)로 옮겨져야 한다.
    org.mockito.ArgumentCaptor<Todo> movedTo = org.mockito.ArgumentCaptor.forClass(Todo.class);
    then(previousReplan).should().relinkTodo(movedTo.capture());
    assertThat(movedTo.getValue()).isNotSameAs(anchor);
  }

  @Test
  void 우선순위_다른_투두도_같은사용자면_치우고_새로만든다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));

    User sameUser = org.mockito.Mockito.mock(User.class);
    given(sameUser.getId()).willReturn(1L);
    Todo target = org.mockito.Mockito.mock(Todo.class);
    given(target.getUser()).willReturn(sameUser);
    given(target.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전
    given(target.getChildren()).willReturn(List.of());
    given(todoRepository.findById(99L)).willReturn(Optional.of(target));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modifyOp =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            99L,
            "[1] 데이터 분석 1~2강",
            null,
            null,
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modifyOp));

    replanService.save(1L, req);

    then(target).should().softDelete();
    // 원본 대상이 아니라 새로 만든 투두를 저장해야 한다
    org.mockito.ArgumentCaptor<Todo> savedTodo = org.mockito.ArgumentCaptor.forClass(Todo.class);
    then(todoRepository).should().save(savedTodo.capture());
    assertThat(savedTodo.getValue()).isNotSameAs(target);
  }

  @Test
  void MODIFY_TODO_마감_지난_대상은_비활성화된다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 1, 10, 0)); // 과거(실패 후)
    given(anchor.getTitle()).willReturn("데이터 분석");
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO, 42L, null, "2026-07-02", null, null, null, null, List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify));

    replanService.save(1L, req);

    then(anchor).should().deactivate();
    then(anchor).should(never()).softDelete();
    then(todoRepository).should().save(any(Todo.class));
  }

  @Test
  void 우선순위_타깃이_남의_투두면_거부된다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));

    User otherUser = org.mockito.Mockito.mock(User.class);
    given(otherUser.getId()).willReturn(999L);
    Todo target = org.mockito.Mockito.mock(Todo.class);
    given(target.getUser()).willReturn(otherUser);
    given(todoRepository.findById(99L)).willReturn(Optional.of(target));

    ReplanOperation modifyOp =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            99L,
            "[1] 데이터 분석 1~2강",
            "2026-06-20",
            "23:59",
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modifyOp));

    assertThatThrownBy(() -> replanService.save(1L, req)).isInstanceOf(CustomException.class);
  }

  @Test
  void MODIFY_ROUTINE은_규칙수정_미래override폐기_회차치우기를_한다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전
    given(anchor.getChildren()).willReturn(List.of());
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(anchor.getRoutine()).willReturn(routine);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(routineService.willCreateUpcomingOccurrence(routine))
        .willReturn(false); // 종료일 경과 → 다음 회차 안 만들어짐

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE,
            42L,
            "영단어 50개",
            null,
            "11:15",
            null,
            "WEEKLY",
            62,
            List.of());
    replanService.save(1L, new ReplanSaveRequest(42L, List.of("INTERRUPT_LATE_END"), List.of(op)));

    then(routine)
        .should()
        .update(
            eq("영단어 50개"), any(), eq(RoutineType.WEEKLY), eq(62), eq(LocalTime.of(11, 15)), any());
    then(routine).should().linkReplan(any(Replan.class));
    // 오늘 이후 override만 지워야 한다(과거/오늘 것을 같이 지우면 안 됨). 컷오프 = 오늘(clock=2026-06-18).
    org.mockito.ArgumentCaptor<java.time.LocalDate> cutoff =
        org.mockito.ArgumentCaptor.forClass(java.time.LocalDate.class);
    then(routineOverrideRepository)
        .should()
        .deleteByRoutineAndOverrideDateGreaterThanEqual(eq(routine), cutoff.capture());
    assertThat(cutoff.getValue()).isEqualTo(java.time.LocalDate.of(2026, 6, 18));
    // 새 규칙에 오늘 회차가 없어 대체 투두가 없다 → 소프트 삭제하면 리플랜이 통계에서 사라지므로 비활성화로 남긴다
    then(anchor).should().deactivate();
    then(anchor).should(never()).softDelete();
    then(routineService).should(never()).createTodoTreeFromMother(any()); // 오늘 회차 아님
  }

  @Test
  void MODIFY_ROUTINE_실패전이고_오늘이_회차면_오늘회차를_재생성한다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전
    given(anchor.getChildren()).willReturn(List.of());
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(anchor.getRoutine()).willReturn(routine);
    given(routine.getRoutineType()).willReturn(RoutineType.DAILY);
    given(routine.getRoutineTime()).willReturn(LocalTime.of(7, 30));
    given(routine.getTag()).willReturn(null);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(routineService.willCreateUpcomingOccurrence(routine)).willReturn(true);
    given(todoRepository.findFirstUpcomingMotherTodoByRoutine(any(), any()))
        .willReturn(Optional.of(org.mockito.Mockito.mock(Todo.class)));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE, 42L, "새 제목", null, null, null, null, null, List.of());
    replanService.save(1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op)));

    then(anchor).should().softDelete();
    then(routineService).should().createTodoTreeFromMother(routine);
  }

  @Test
  void MODIFY_ROUTINE_재생성된_오늘회차에_리플랜링크를_단다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전
    given(anchor.getChildren()).willReturn(List.of());
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(anchor.getRoutine()).willReturn(routine);
    given(routine.getRoutineType()).willReturn(RoutineType.DAILY);
    given(routine.getRoutineTime()).willReturn(LocalTime.of(7, 30));
    given(routine.getTag()).willReturn(null);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(routineService.willCreateUpcomingOccurrence(routine)).willReturn(true);
    Todo newInstance = org.mockito.Mockito.mock(Todo.class);
    given(todoRepository.findFirstUpcomingMotherTodoByRoutine(any(), any()))
        .willReturn(Optional.of(newInstance));
    org.mockito.ArgumentCaptor<Replan> replanCaptor =
        org.mockito.ArgumentCaptor.forClass(Replan.class);

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE, 42L, "새 제목", null, null, null, null, null, List.of());
    replanService.save(1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op)));

    then(newInstance).should().linkReplan(any(Replan.class));
    // 리플랜은 소프트 삭제된 앵커가 아니라 재생성된 새 회차를 가리켜야 한다
    then(replanRepository).should().save(replanCaptor.capture());
    assertThat(replanCaptor.getValue().getTodo()).isSameAs(newInstance);
  }

  @Test
  void MODIFY_ROUTINE_같은_회차를_여러_번_리플랜하면_이전_리플랜도_새_회차로_옮긴다() {
    // 루틴 회차도 같은 회차를 한 달에 두 번 리플랜하면 리플랜이 여러 개 달릴 수 있다.
    // 두 번째 리플랜으로 옛 회차를 소프트 삭제할 때 첫 번째 리플랜까지 새 회차로 옮기지 않으면,
    // 첫 번째 리플랜이 통계 조회에서 사라진다(MODIFY_TODO와 동일한 누락).
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전 → 소프트 삭제
    given(anchor.getChildren()).willReturn(List.of());
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(anchor.getRoutine()).willReturn(routine);
    given(routine.getRoutineType()).willReturn(RoutineType.DAILY);
    given(routine.getRoutineTime()).willReturn(LocalTime.of(7, 30));
    given(routine.getTag()).willReturn(null);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(routineService.willCreateUpcomingOccurrence(routine)).willReturn(true);
    Todo newInstance = org.mockito.Mockito.mock(Todo.class);
    given(newInstance.getChildren()).willReturn(List.of());
    given(todoRepository.findFirstUpcomingMotherTodoByRoutine(any(), any()))
        .willReturn(Optional.of(newInstance));
    // 같은 회차(앵커)에 이전부터 달려 있던 리플랜(첫 번째 리플랜)
    Replan previousReplan = org.mockito.Mockito.mock(Replan.class);
    given(replanRepository.findByTodo(anchor))
        .willReturn(new java.util.ArrayList<>(List.of(previousReplan)));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE, 42L, "새 제목", null, null, null, null, null, List.of());
    replanService.save(1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op)));

    // 이전 리플랜도 소프트 삭제된 옛 회차가 아니라 재생성된 새 회차를 가리켜야 한다.
    then(previousReplan).should().relinkTodo(newInstance);
  }

  @Test
  void MODIFY_ROUTINE_하위루틴_회차_대상이면_거부된다() {
    // 하위 루틴 회차는 규칙을 따로 갖지 않고 엄마 루틴을 따른다 — 루틴 규칙 변경(MODIFY_ROUTINE)은 거부한다.
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    Routine child = org.mockito.Mockito.mock(Routine.class);
    given(anchor.getRoutine()).willReturn(child);
    given(child.isChild()).willReturn(true);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE, 42L, "새 제목", null, null, null, null, null, List.of());

    assertThatThrownBy(
            () ->
                replanService.save(
                    1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op))))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
    then(routineService).should(never()).createTodoTreeFromMother(any());
  }

  @Test
  void MODIFY_ROUTINE_실패후면_회차와_하위회차를_함께_비활성화하고_재생성안한다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 1, 11, 0)); // 과거(실패 후)
    Todo subOccurrence = org.mockito.Mockito.mock(Todo.class); // 루틴 회차의 하위 회차
    given(anchor.getChildren()).willReturn(List.of(subOccurrence));
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(anchor.getRoutine()).willReturn(routine);
    given(routine.getRoutineType()).willReturn(RoutineType.DAILY);
    given(routine.getRoutineTime()).willReturn(null);
    given(routine.getTag()).willReturn(null);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE, 42L, "새 제목", null, null, null, null, null, List.of());
    replanService.save(1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op)));

    then(anchor).should().deactivate();
    then(anchor).should(never()).softDelete();
    then(subOccurrence).should().deactivate(); // 하위 회차도 함께 비활성화돼 부모와 상태가 맞아야 한다
    then(routineService).should(never()).createTodoTreeFromMother(any());
  }

  @Test
  void CREATE_ROUTINE은_새_루틴을_만든다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    Todo instanceTodo = org.mockito.Mockito.mock(Todo.class);
    given(todoRepository.findFirstUpcomingMotherTodoByRoutine(any(), any()))
        .willReturn(Optional.of(instanceTodo));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.CREATE_ROUTINE,
            null,
            "스트레칭",
            null,
            "20:00",
            null,
            "DAILY",
            null,
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("CONDITION_PAIN"), List.of(op));

    replanService.save(1L, req);

    then(routineRepository).should(times(1)).save(any(Routine.class));
    then(routineService).should().createTodoTreeFromMother(any());
    then(instanceTodo).should().linkReplan(any(Replan.class));
  }

  @Test
  void CREATE_ROUTINE이_회차를_못만들면_마감지난_앵커를_치우지_않는다() {
    // 루틴 종료일이 이미 지나 회차 투두가 안 생기는 경우, 앵커를 비활성화하면
    // 원본도 대체도 없이 사라지므로 앵커를 건드리면 안 된다.
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 1, 10, 0)); // 과거(실패 후)
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    // 회차 투두가 만들어지지 않음
    given(todoRepository.findFirstUpcomingMotherTodoByRoutine(any(), any()))
        .willReturn(Optional.empty());

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.CREATE_ROUTINE,
            null,
            "스트레칭",
            "2020-01-01",
            "20:00",
            null,
            "DAILY",
            null,
            List.of());
    replanService.save(1L, new ReplanSaveRequest(42L, List.of("CONDITION_PAIN"), List.of(op)));

    then(anchor).should(never()).deactivate();
    then(anchor).should(never()).softDelete();
  }

  @Test
  void CREATE_ROUTINE은_종료일이_있으면_루틴_종료일로_저장한다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.CREATE_ROUTINE,
            null,
            "스트레칭",
            "2026-12-31", // 반복 종료일
            "20:00",
            null,
            "DAILY",
            null,
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("CONDITION_PAIN"), List.of(op));

    replanService.save(1L, req);

    org.mockito.ArgumentCaptor<Routine> captor = org.mockito.ArgumentCaptor.forClass(Routine.class);
    then(routineRepository).should().save(captor.capture());
    assertThat(captor.getValue().getDueDate()).isEqualTo(LocalDateTime.of(2026, 12, 31, 0, 0));
  }

  @Test
  void CREATE_ROUTINE_DAILY는_반복날짜를_null로_정규화한다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    // DAILY인데 routineDate가 들어와도 저장 시 null로 정규화돼야 한다
    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.CREATE_ROUTINE, null, "스트레칭", null, "20:00", null, "DAILY", 5, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("CONDITION_PAIN"), List.of(op));

    replanService.save(1L, req);

    org.mockito.ArgumentCaptor<Routine> captor = org.mockito.ArgumentCaptor.forClass(Routine.class);
    then(routineRepository).should().save(captor.capture());
    assertThat(captor.getValue().getRoutineDate()).isNull();
  }

  @Test
  void 마감_지난_일반투두를_ADD로_대체하면_원본을_숨긴다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 1, 10, 0)); // 과거
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation add =
        new ReplanOperation(
            ReplanAction.ADD,
            null,
            "데이터 분석 3~4강",
            "2026-06-20",
            "23:59",
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(add));

    replanService.save(1L, req);

    then(todo).should().deactivate();
  }

  @Test
  void 앵커를_MODIFY로_치우면_post_loop에서_또_치우지_않는다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 1, 10, 0)); // 과거
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modifyOp =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO, 42L, "수정", "2026-06-25", null, null, null, null, List.of());
    ReplanOperation addOp =
        new ReplanOperation(
            ReplanAction.ADD, null, "추가", "2026-06-26", null, null, null, null, List.of());
    replanService.save(
        1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modifyOp, addOp)));

    then(anchor).should(times(1)).deactivate(); // MODIFY에서 1번만
  }

  @Test
  void 미래_앵커에_보조_ADD만이면_앵커는_그대로_둔다() {
    // 실패 전(미래) 앵커에 ADD는 보조 투두 추가일 수 있으므로 앵커를 치우면 안 된다
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 7, 1, 10, 0)); // 미래(실패 전)
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation addOp =
        new ReplanOperation(
            ReplanAction.ADD, null, "조용한 장소 세팅", "2026-07-02", null, null, null, null, List.of());
    replanService.save(1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(addOp)));

    then(anchor).should(never()).softDelete();
    then(anchor).should(never()).deactivate();
    then(todoRepository).should().save(any(Todo.class)); // 보조 투두는 생성됨
  }

  @Test
  void 우선순위_리플랜에서_앵커_아닌_투두만_수정하면_앵커는_건드리지_않는다() {
    // Bug 1 회귀: ADD/CREATE_ROUTINE 없이 MODIFY_TODO만 있을 때 앵커를 오삭제하던 문제
    Todo anchor = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));

    User sameUser = org.mockito.Mockito.mock(User.class);
    given(sameUser.getId()).willReturn(1L);
    Todo target = org.mockito.Mockito.mock(Todo.class);
    given(target.getUser()).willReturn(sameUser);
    given(target.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전
    given(target.getChildren()).willReturn(List.of());
    given(todoRepository.findById(99L)).willReturn(Optional.of(target));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modifyOp =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            99L, // 앵커(42)가 아닌 다른 투두를 대상으로 한다
            "[1] 데이터 분석 1~2강",
            null,
            null,
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modifyOp));

    replanService.save(1L, req);

    // 대체 투두(ADD)가 없으므로 앵커(42)는 건드리지 않아야 한다
    then(anchor).should(never()).softDelete();
    then(anchor).should(never()).deactivate();
    // 대상(99)은 치우고 새 투두로 대체해야 한다
    then(target).should().softDelete();
    then(todoRepository).should().save(any(Todo.class));
  }

  @Test
  void MODIFY_TODO_부모투두_수정시_하위투두는_새_부모로_옮기고_삭제하지_않는다() {
    // Bug 2 회귀: 하위 투두가 있는 부모를 수정할 때 하위 투두까지 삭제되던 문제
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전

    Todo child1 = org.mockito.Mockito.mock(Todo.class);
    Todo child2 = org.mockito.Mockito.mock(Todo.class);
    given(anchor.getChildren()).willReturn(List.of(child1, child2));

    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "데이터 분석 1~2강",
            "2026-07-02",
            "23:59",
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify));

    replanService.save(1L, req);

    // 새로 만들어진 부모 투두를 캡처해서 각 하위 투두가 그걸 부모로 갱신했는지 확인한다
    org.mockito.ArgumentCaptor<Todo> captor = org.mockito.ArgumentCaptor.forClass(Todo.class);
    then(todoRepository).should().save(captor.capture());
    Todo newParent = captor.getValue();

    then(child1).should().updateParent(newParent);
    then(child2).should().updateParent(newParent);
    // 하위 투두는 삭제돼서는 안 된다
    then(child1).should(never()).softDelete();
    then(child2).should(never()).softDelete();
  }

  @Test
  void MODIFY_TODO_하위투두_수정시_부모를_유지한다() {
    Todo parent = org.mockito.Mockito.mock(Todo.class);
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 11, 0)); // 실패 전
    given(anchor.getChildren()).willReturn(List.of());
    given(anchor.getParent()).willReturn(parent);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));
    org.mockito.ArgumentCaptor<Todo> captor = org.mockito.ArgumentCaptor.forClass(Todo.class);

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "수정된 제목",
            "2026-07-02",
            null,
            null,
            null,
            null,
            List.of());
    replanService.save(
        1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify)));

    then(todoRepository).should().save(captor.capture());
    assertThat(captor.getValue().getParent()).isSameAs(parent);
  }

  @Test
  void 잘못된_마감형식이면_400() {
    Todo anchor = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation addOp =
        new ReplanOperation(
            ReplanAction.ADD, null, "새 투두", "not-a-date", null, null, null, null, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(addOp));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void 실패이유에_null_원소가_있으면_400() {
    // validateReasonCodes가 findOwnedTodo보다 먼저 던지므로 todo stub은 두지 않는다.
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, java.util.Arrays.asList("GOAL_NO_PRIORITY", null), List.of());

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_REASON));
  }

  @Test
  void AI입력에_앵커_마감시간이_포함된다() {
    // buildInput만 직접 호출하므로 ownedTodo의 user stub은 불필요 — 최소 목만 둔다.
    Todo todo = org.mockito.Mockito.mock(Todo.class);
    given(todo.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 7, 10, 0));

    RecommendInput input =
        replanService.buildInput(todo, List.of("INTERRUPT_SUDDEN"), null, null, 0);

    assertThat(input.anchorDueDate()).isEqualTo("2026-06-07 10:00");
  }

  @Test
  void MODIFY_TODO_제목이_빈문자열이면_400() {
    Todo anchor = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO, 42L, "", null, null, null, null, null, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void MODIFY_TODO_시간만_바꾸면_새투두는_기존_날짜를_유지한다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 25, 10, 0)); // 실패 전
    given(anchor.getTitle()).willReturn("데이터 분석");
    given(anchor.getChildren()).willReturn(List.of());
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    given(todoRepository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));
    org.mockito.ArgumentCaptor<Todo> captor = org.mockito.ArgumentCaptor.forClass(Todo.class);

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO, 42L, null, null, "15:30", null, null, null, List.of());
    replanService.save(
        1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify)));

    then(todoRepository).should().save(captor.capture());
    assertThat(captor.getValue().getDueDate()).isEqualTo(LocalDateTime.of(2026, 6, 25, 15, 30));
  }

  // Fix 3 — 실패 이유 개수 검증
  @Test
  void 실패이유가_없으면_저장_실패() {
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of(), List.of());

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_REASON));
  }

  @Test
  void 실패이유가_4개이면_저장_실패() {
    ReplanSaveRequest req =
        new ReplanSaveRequest(
            42L,
            List.of("GOAL_NO_PRIORITY", "INTERRUPT_LATE_END", "CONDITION_PAIN", "OTHER"),
            List.of());

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_REASON));
  }

  @Test
  void 실패이유가_null이면_저장_실패() {
    ReplanSaveRequest req = new ReplanSaveRequest(42L, null, List.of());

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_REASON));
  }

  @Test
  void 실패이유가_128자를_넘으면_저장_실패() {
    // 직접 입력 사유가 컬럼 길이(128자)를 넘으면 DB 오류 전에 400으로 막아야 한다
    String tooLong = "가".repeat(129);
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of(tooLong), List.of());

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_REASON));
  }

  @Test
  void 새로고침_횟수가_3을_넘으면_추천_실패() {
    ReplanRecommendRequest req =
        new ReplanRecommendRequest(42L, List.of("GOAL_NO_PRIORITY"), null, 4);

    assertThatThrownBy(() -> replanService.recommend(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(ReplanErrorCode.REPLAN_INVALID_REFRESH_COUNT));
  }

  @Test
  void 새로고침_횟수가_음수면_추천_실패() {
    ReplanRecommendRequest req =
        new ReplanRecommendRequest(42L, List.of("GOAL_NO_PRIORITY"), null, -1);

    assertThatThrownBy(() -> replanService.recommend(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(ReplanErrorCode.REPLAN_INVALID_REFRESH_COUNT));
  }

  @Test
  void 추천_시_실패이유가_없으면_실패() {
    ReplanRecommendRequest req = new ReplanRecommendRequest(42L, List.of(), null, null);

    assertThatThrownBy(() -> replanService.recommend(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_REASON));
  }

  @Test
  void MODIFY_ROUTINE_위클리인데_요일없으면_400() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getId()).willReturn(42L);
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(todo.getRoutine()).willReturn(routine);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    // op에 routineType="WEEKLY"이고 routineDate=null → 유효성 검사에서 즉시 실패

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE,
            42L,
            null,
            null,
            null,
            null,
            "WEEKLY",
            null, // routineDate 없음 — 유효하지 않음
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void MODIFY_ROUTINE_반복유형_변경시_새_요일을_안주면_400() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getId()).willReturn(42L);
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(todo.getRoutine()).willReturn(routine);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    // 기존은 MONTHLY → WEEKLY로 바꾸면서 routineDate를 안 주면 기존 일자를 요일로 오용하면 안 되므로 400
    given(routine.getRoutineType()).willReturn(RoutineType.MONTHLY);

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE, 42L, null, null, null, null, "WEEKLY", null, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void MODIFY_ROUTINE_타깃이_앵커가_아니면_400() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE,
            99L, // 앵커(42L)와 다른 타깃
            null,
            null,
            null,
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void CREATE_ROUTINE_먼슬리인데_날짜범위밖이면_400() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.CREATE_ROUTINE,
            null,
            "매월 루틴",
            null,
            null,
            null,
            "MONTHLY",
            40, // 31 초과 — 유효하지 않음
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void 작업_action이_null이면_400() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(null, null, "제목", null, null, null, null, null, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void MODIFY_TODO_타깃ID_없으면_400() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO, null, "제목", null, null, null, null, null, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void ADD_제목이_비면_400() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.ADD, null, null, "2026-06-20", null, null, null, null, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    assertThatThrownBy(() -> replanService.save(1L, req))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }

  @Test
  void MODIFY_TODO_루틴회차_대상이면_거부된다() {
    // 루틴 회차 변경은 MODIFY_ROUTINE 담당 — MODIFY_TODO로 오면 거부
    Todo target = ownedTodo(42L, 1L);
    given(target.getRoutine()).willReturn(org.mockito.Mockito.mock(Routine.class));
    given(todoRepository.findById(42L)).willReturn(Optional.of(target));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "가벼운 스트레칭",
            "2026-07-02",
            null,
            null,
            null,
            null,
            List.of());

    assertThatThrownBy(
            () ->
                replanService.save(
                    1L, new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify))))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReplanErrorCode.REPLAN_INVALID_OPERATION));
  }
}
