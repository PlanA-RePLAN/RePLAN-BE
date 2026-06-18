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
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanQuestionsRequest;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.exception.ReplanErrorCode;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
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
            routineService);
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
  void 추가질문_조회_성공() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(aiService.generateQuestions(any()))
        .willReturn(List.of(new ReplanQuestion("k", null, "질문", null)));

    ReplanQuestionsRequest req = new ReplanQuestionsRequest(42L, List.of("GOAL_NO_PRIORITY"), null);

    List<ReplanQuestion> result = replanService.getQuestions(1L, req);

    assertThat(result).hasSize(1);
  }

  @Test
  void 남의_투두면_조회_실패() {
    Todo todo = ownedTodo(42L, 999L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));

    ReplanQuestionsRequest req = new ReplanQuestionsRequest(42L, List.of("GOAL_NO_PRIORITY"), null);

    assertThatThrownBy(() -> replanService.getQuestions(1L, req))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void 추천_성공() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 7, 10, 0));
    given(todo.getTag()).willReturn(org.mockito.Mockito.mock(Tag.class));
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(aiService.generateRecommend(any()))
        .willReturn(new ReplanRecommendResponse("요약", "팁", List.of()));

    ReplanRecommendRequest req = new ReplanRecommendRequest(42L, List.of("GOAL_NO_PRIORITY"), null);

    ReplanRecommendResponse res = replanService.recommend(1L, req);

    assertThat(res.summary()).isEqualTo("요약");
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
  void MODIFY_TODO는_기존_투두를_제자리_수정한다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "데이터 분석 1~2강",
            "2026-06-20",
            "23:59",
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify));

    replanService.save(1L, req);

    then(todo).should().updateTitle("데이터 분석 1~2강");
    then(todo).should().linkReplan(any(Replan.class));
    then(todoRepository).should(never()).save(any(Todo.class));
  }

  @Test
  void MODIFY_TODO_제목만_바꾸면_마감일은_유지된다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modify =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "새 제목",
            null, // dueDate null
            null, // dueTime null
            null,
            null,
            null,
            List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modify));

    replanService.save(1L, req);

    then(anchor).should().updateTitle("새 제목");
    then(anchor).should(never()).updateDueDate(any());
  }

  @Test
  void 우선순위_다른_투두도_같은_사용자면_수정된다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));

    User sameUser = org.mockito.Mockito.mock(User.class);
    given(sameUser.getId()).willReturn(1L);
    Todo target = org.mockito.Mockito.mock(Todo.class);
    given(target.getUser()).willReturn(sameUser);
    given(todoRepository.findById(99L)).willReturn(Optional.of(target));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

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

    replanService.save(1L, req);

    then(target).should().updateTitle("[1] 데이터 분석 1~2강");
    then(target).should().linkReplan(any(Replan.class));
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
  void MODIFY_ROUTINE은_루틴_규칙을_수정한다() {
    Todo todo = ownedTodo(42L, 1L);
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(todo.getRoutine()).willReturn(routine);
    given(todo.isCompleted()).willReturn(false);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE,
            42L,
            "영단어 50개 암기",
            null,
            "11:15",
            null,
            "WEEKLY",
            62,
            List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("INTERRUPT_LATE_END"), List.of(op));

    replanService.save(1L, req);

    then(routine)
        .should()
        .update(eq("영단어 50개 암기"), eq(RoutineType.WEEKLY), eq(62), eq(LocalTime.of(11, 15)), any());
    then(routine).should().linkReplan(any(Replan.class));
    then(todo).should().linkReplan(any(Replan.class));
  }

  @Test
  void 루틴_수정_시_미지정_필드는_기존값_유지된다() {
    Todo todo = ownedTodo(42L, 1L);
    Routine routine = org.mockito.Mockito.mock(Routine.class);
    given(todo.getRoutine()).willReturn(routine);
    given(todo.isCompleted()).willReturn(false);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));
    // op에서 title만 지정, 나머지는 null → 기존 루틴 값으로 fallback
    given(routine.getRoutineType()).willReturn(RoutineType.DAILY);
    given(routine.getRoutineDate()).willReturn(3);
    given(routine.getRoutineTime()).willReturn(LocalTime.of(7, 30));
    given(routine.getTag()).willReturn(null);

    ReplanOperation op =
        new ReplanOperation(
            ReplanAction.MODIFY_ROUTINE, 42L, "새 제목", null, null, null, null, null, List.of());
    ReplanSaveRequest req = new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(op));

    replanService.save(1L, req);

    then(routine)
        .should()
        .update(eq("새 제목"), eq(RoutineType.DAILY), eq(3), eq(LocalTime.of(7, 30)), any());
  }

  @Test
  void CREATE_ROUTINE은_새_루틴을_만든다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

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
  }

  @Test
  void 마감_지난_일반투두를_ADD로_대체하면_원본을_숨긴다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getRoutine()).willReturn(null);
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
  void 마감_지난_앵커를_MODIFY하고_ADD도_있으면_원본은_안숨긴다() {
    Todo anchor = ownedTodo(42L, 1L);
    given(anchor.getId()).willReturn(42L);
    given(anchor.getRoutine()).willReturn(null);
    given(anchor.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 1, 10, 0)); // 과거
    given(todoRepository.findById(42L)).willReturn(Optional.of(anchor));
    given(replanRepository.save(any(Replan.class))).willAnswer(inv -> inv.getArgument(0));

    ReplanOperation modifyOp =
        new ReplanOperation(
            ReplanAction.MODIFY_TODO,
            42L,
            "수정된 제목",
            "2026-06-25",
            null,
            null,
            null,
            null,
            List.of());
    ReplanOperation addOp =
        new ReplanOperation(
            ReplanAction.ADD, null, "추가 투두", "2026-06-26", null, null, null, null, List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(modifyOp, addOp));

    replanService.save(1L, req);

    then(anchor).should(never()).deactivate();
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
}
