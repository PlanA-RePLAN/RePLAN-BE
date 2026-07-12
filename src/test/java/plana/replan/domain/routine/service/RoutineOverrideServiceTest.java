package plana.replan.domain.routine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.routine.dto.RoutineOverrideContentRequestDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.repository.RoutineOverrideRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

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
}
