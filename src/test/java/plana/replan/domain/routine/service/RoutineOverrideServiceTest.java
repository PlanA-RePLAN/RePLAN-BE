package plana.replan.domain.routine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.routine.dto.RoutineOverrideContentRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideResponseDto;
import plana.replan.domain.routine.entity.ReservedSubtodo;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineOverrideRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class RoutineOverrideServiceTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2024, 1, 15);

  @Mock private Clock clock;
  @Mock private RoutineRepository routineRepository;
  @Mock private RoutineOverrideRepository routineOverrideRepository;
  @Mock private TagRepository tagRepository;
  @Mock private TodoRepository todoRepository;

  @InjectMocks private RoutineOverrideService routineOverrideService;

  private User testUser() {
    User user =
        User.builder()
            .email("test@test.com")
            .nickname("테스트")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    return user;
  }

  private Routine dailyRoutine(LocalTime routineTime) {
    Routine routine =
        Routine.builder()
            .title("테스트 루틴")
            .routineType(RoutineType.DAILY)
            .routineTime(routineTime)
            .user(testUser())
            .build();
    ReflectionTestUtils.setField(routine, "id", 10L);
    return routine;
  }

  private RoutineOverride givenOverrideFor(Routine routine) {
    RoutineOverride override =
        RoutineOverride.builder().routine(routine).overrideDate(TEST_DATE).build();
    given(routineOverrideRepository.findByRoutineAndOverrideDate(routine, TEST_DATE))
        .willReturn(Optional.of(override));
    return override;
  }

  @Test
  void updateContent_시간을_주면_쪽지에_기록되고_이미_생성된_회차의_마감일시도_갱신된다() {
    Routine routine = dailyRoutine(LocalTime.of(9, 0));
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);

    Todo existing =
        Todo.builder()
            .title("테스트 루틴")
            .dueDate(TEST_DATE.atTime(9, 0))
            .user(routine.getUser())
            .isPinned(false)
            .routine(routine)
            .build();
    given(
            todoRepository.findMotherTodoByRoutineAndDate(
                routine, TEST_DATE.atStartOfDay(), TEST_DATE.plusDays(1).atStartOfDay()))
        .willReturn(Optional.of(existing));

    routineOverrideService.updateContent(
        1L, 10L, TEST_DATE, new RoutineOverrideContentRequestDto(null, null, LocalTime.of(19, 0)));

    assertThat(override.getOverrideTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(existing.getDueDate()).isEqualTo(TEST_DATE.atTime(19, 0));
  }

  @Test
  void updateContent_시간이_null이면_루틴_기본_시간으로_복귀한다() {
    Routine routine = dailyRoutine(LocalTime.of(9, 0));
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    // 이전에 시간 예외가 있던 상태에서 null로 덮으면 기본 시간으로 돌아간다
    override.updateContent(null, null, LocalTime.of(19, 0));

    Todo existing =
        Todo.builder()
            .title("테스트 루틴")
            .dueDate(TEST_DATE.atTime(19, 0))
            .user(routine.getUser())
            .isPinned(false)
            .routine(routine)
            .build();
    given(
            todoRepository.findMotherTodoByRoutineAndDate(
                routine, TEST_DATE.atStartOfDay(), TEST_DATE.plusDays(1).atStartOfDay()))
        .willReturn(Optional.of(existing));

    routineOverrideService.updateContent(
        1L, 10L, TEST_DATE, new RoutineOverrideContentRequestDto(null, null, null));

    assertThat(override.getOverrideTime()).isNull();
    assertThat(existing.getDueDate()).isEqualTo(TEST_DATE.atTime(9, 0));
  }

  @Test
  void updateContent_루틴_기본_시간도_없으면_하루의_끝으로_채운다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    givenOverrideFor(routine);

    Todo existing =
        Todo.builder()
            .title("테스트 루틴")
            .dueDate(TEST_DATE.atTime(23, 59, 59))
            .user(routine.getUser())
            .isPinned(false)
            .routine(routine)
            .build();
    given(
            todoRepository.findMotherTodoByRoutineAndDate(
                routine, TEST_DATE.atStartOfDay(), TEST_DATE.plusDays(1).atStartOfDay()))
        .willReturn(Optional.of(existing));

    routineOverrideService.updateContent(
        1L, 10L, TEST_DATE, new RoutineOverrideContentRequestDto("제목만 수정", null, null));

    assertThat(existing.getDueDate()).isEqualTo(TEST_DATE.atTime(23, 59, 59));
    assertThat(existing.getTitle()).isEqualTo("제목만 수정");
  }

  // ========== 회차별 하위 투두 (예약) ==========

  @Test
  void addSubtodo_행이_없으면_쪽지에_예약된다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    given(
            todoRepository.findMotherTodoByRoutineAndDate(
                routine, TEST_DATE.atStartOfDay(), TEST_DATE.plusDays(1).atStartOfDay()))
        .willReturn(Optional.empty());

    routineOverrideService.addSubtodo(1L, 10L, TEST_DATE, "단어 50개");

    assertThat(override.getOverrideSubtodos())
        .extracting(ReservedSubtodo::title)
        .containsExactly("단어 50개");
    verify(todoRepository, never()).save(any());
  }

  @Test
  void addSubtodo_행이_있으면_그_행에_실제_하위_투두를_만든다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    given(routineOverrideRepository.findByRoutineAndOverrideDate(routine, TEST_DATE))
        .willReturn(Optional.empty());

    Todo existing =
        Todo.builder()
            .title("테스트 루틴")
            .dueDate(TEST_DATE.atTime(23, 59, 59))
            .user(routine.getUser())
            .isPinned(false)
            .routine(routine)
            .build();
    given(
            todoRepository.findMotherTodoByRoutineAndDate(
                routine, TEST_DATE.atStartOfDay(), TEST_DATE.plusDays(1).atStartOfDay()))
        .willReturn(Optional.of(existing));

    routineOverrideService.addSubtodo(1L, 10L, TEST_DATE, "단어 50개");

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).save(captor.capture());
    assertThat(captor.getValue().getTitle()).isEqualTo("단어 50개");
    assertThat(captor.getValue().getParent()).isEqualTo(existing);
  }

  @Test
  void addSubtodo_건너뛴_날짜면_거절한다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    override.skip();

    assertThatThrownBy(() -> routineOverrideService.addSubtodo(1L, 10L, TEST_DATE, "단어 50개"))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", RoutineErrorCode.ROUTINE_OVERRIDE_SKIPPED);
  }

  @Test
  void updateSubtodo_예약된_제목이_수정된다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    override.addSubtodo("단어 50개");

    routineOverrideService.updateSubtodo(1L, 10L, TEST_DATE, 0, "단어 100개");

    assertThat(override.getOverrideSubtodos())
        .extracting(ReservedSubtodo::title)
        .containsExactly("단어 100개");
  }

  @Test
  void updateSubtodo_index가_범위_밖이면_404() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    override.addSubtodo("단어 50개");

    assertThatThrownBy(() -> routineOverrideService.updateSubtodo(1L, 10L, TEST_DATE, 1, "x"))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", RoutineErrorCode.ROUTINE_OVERRIDE_SUBTODO_NOT_FOUND);
  }

  @Test
  void deleteSubtodo_마지막_예약을_지우면_배열이_비워진다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    override.addSubtodo("단어 50개");

    routineOverrideService.deleteSubtodo(1L, 10L, TEST_DATE, 0);

    assertThat(override.getOverrideSubtodos()).isNull();
  }

  @Test
  void completeSubtodo_예약_하위의_완료_상태가_바뀐다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    override.addSubtodo("단어 50개");

    routineOverrideService.completeSubtodo(1L, 10L, TEST_DATE, 0, true);
    assertThat(override.getOverrideSubtodos().get(0).isCompleted()).isTrue();
    assertThat(override.getOverrideSubtodos().get(0).title()).isEqualTo("단어 50개");

    routineOverrideService.completeSubtodo(1L, 10L, TEST_DATE, 0, false);
    assertThat(override.getOverrideSubtodos().get(0).isCompleted()).isFalse();
  }

  @Test
  void completeSubtodo_index가_범위_밖이면_404() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    override.addSubtodo("단어 50개");

    assertThatThrownBy(() -> routineOverrideService.completeSubtodo(1L, 10L, TEST_DATE, 1, true))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", RoutineErrorCode.ROUTINE_OVERRIDE_SUBTODO_NOT_FOUND);
  }

  @Test
  void getOverride_반복정보와_그날의_유효시간을_함께_내려준다() {
    Routine routine = dailyRoutine(LocalTime.of(9, 0));
    ReflectionTestUtils.setField(routine, "dueDate", LocalDate.of(2099, 12, 31).atTime(8, 0));
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    RoutineOverride override = givenOverrideFor(routine);
    override.updateContent(null, null, LocalTime.of(15, 0));

    RoutineOverrideResponseDto result = routineOverrideService.getOverride(1L, 10L, TEST_DATE);

    assertThat(result.routineType()).isEqualTo("DAILY");
    assertThat(result.routineTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(result.effectiveTime()).isEqualTo(LocalTime.of(15, 0));
    assertThat(result.repeatEndDate()).isEqualTo(LocalDate.of(2099, 12, 31).atTime(8, 0));
  }

  @Test
  void getOverride_쪽지_시간이_없으면_루틴_기본_시간이_유효시간이다() {
    Routine routine = dailyRoutine(LocalTime.of(9, 0));
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    given(routineOverrideRepository.findByRoutineAndOverrideDate(routine, TEST_DATE))
        .willReturn(Optional.empty());

    RoutineOverrideResponseDto result = routineOverrideService.getOverride(1L, 10L, TEST_DATE);

    assertThat(result.effectiveTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(result.routineTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(result.routineType()).isEqualTo("DAILY");
  }

  @Test
  void getOverride_시간_설정이_아무데도_없으면_235959가_유효시간이고_routineTime은_null이다() {
    Routine routine = dailyRoutine(null);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    given(routineOverrideRepository.findByRoutineAndOverrideDate(routine, TEST_DATE))
        .willReturn(Optional.empty());

    RoutineOverrideResponseDto result = routineOverrideService.getOverride(1L, 10L, TEST_DATE);

    assertThat(result.effectiveTime()).isEqualTo(LocalTime.of(23, 59, 59));
    assertThat(result.routineTime()).isNull();
  }
}
