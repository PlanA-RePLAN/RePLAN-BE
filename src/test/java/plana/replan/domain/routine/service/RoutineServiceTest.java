package plana.replan.domain.routine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.dto.RoutineUpdateRequestDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineOverrideRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

  // 2024-01-15: Monday(DayOfWeek=1, bit=1), day of month=15
  private static final LocalDate TEST_DATE = LocalDate.of(2024, 1, 15);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock private Clock clock;
  @Mock private RoutineRepository routineRepository;
  @Mock private RoutineOverrideRepository routineOverrideRepository;
  @Mock private UserRepository userRepository;
  @Mock private TagRepository tagRepository;
  @Mock private GoalRepository goalRepository;
  @Mock private TodoRepository todoRepository;

  @InjectMocks private RoutineService routineService;

  @BeforeEach
  void setUpClock() {
    // 예외 발생 테스트에서는 clock이 호출되지 않으므로 lenient 처리
    lenient().when(clock.instant()).thenReturn(TEST_DATE.atStartOfDay(KST).toInstant());
    lenient().when(clock.getZone()).thenReturn(KST);
    lenient()
        .when(routineOverrideRepository.findByRoutineIdInAndOverrideDate(any(), any()))
        .thenReturn(Collections.emptyList());
  }

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

  private Tag testTag(Long id) {
    Tag tag = Tag.builder().title("업무").color("#3B82F6").user(testUser()).build();
    ReflectionTestUtils.setField(tag, "id", id);
    return tag;
  }

  private Goal testGoal(Long id) {
    Goal goal = Goal.builder().title("목표").user(testUser()).build();
    ReflectionTestUtils.setField(goal, "id", id);
    return goal;
  }

  // ========== 유저 없음 ==========

  @Test
  void 루틴_생성_유저_없음_404() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    999L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.DAILY, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(routineRepository, never()).save(any());
  }

  // ========== 반복 종료일 ==========

  @Test
  void 루틴_생성_종료일이_지났으면_회차_Todo를_만들지_않는다() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    // 종료일(2024-01-10)이 오늘(2024-01-15)보다 과거 → 다음 회차가 종료일을 넘으므로 회차 Todo 생성 안 함
    routineService.createRoutine(
        1L,
        new RoutineCreateRequestDto(
            "끝난 루틴",
            java.time.LocalDateTime.of(2024, 1, 10, 0, 0),
            null,
            RoutineType.DAILY,
            null,
            null,
            null));

    verify(todoRepository, never()).saveAndFlush(any());
  }

  @Test
  void 루틴_생성_종료일이_오늘_자정이어도_그날_회차는_만든다() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    // 종료일이 오늘(2024-01-15) 자정으로 저장돼도, 그날 회차(23:59)는 종료일 안쪽이므로 생성돼야 한다
    routineService.createRoutine(
        1L,
        new RoutineCreateRequestDto(
            "오늘까지 루틴",
            java.time.LocalDateTime.of(2024, 1, 15, 0, 0),
            null,
            RoutineType.DAILY,
            null,
            null,
            null));

    verify(todoRepository).saveAndFlush(any(Todo.class));
  }

  // ========== DAILY ==========

  @Test
  void 루틴_생성_DAILY_성공_선택필드_없음() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L,
            new RoutineCreateRequestDto(
                "아침 스트레칭", null, null, RoutineType.DAILY, null, null, null));

    assertThat(result.getTitle()).isEqualTo("아침 스트레칭");
    assertThat(result.getRoutineType()).isEqualTo(RoutineType.DAILY);
    assertThat(result.getRoutineDate()).isNull();
    assertThat(result.getTagId()).isNull();
    assertThat(result.getGoalId()).isNull();
    assertThat(result.getDueDate()).isNull();
  }

  @Test
  void 루틴_생성_DAILY_routineDate_무시됨() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    // DAILY에서 routineDate=999를 넘겨도 null로 정규화되어 저장됨
    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.DAILY, 999, null, null));

    assertThat(result.getRoutineType()).isEqualTo(RoutineType.DAILY);
    assertThat(result.getRoutineDate()).isNull();
  }

  // ========== WEEKLY ==========

  @Test
  void 루틴_생성_WEEKLY_성공_전체필드() {
    LocalDateTime dueDate = LocalDateTime.of(2025, 12, 31, 0, 0);
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(tagRepository.findById(5L)).willReturn(Optional.of(testTag(5L)));
    given(goalRepository.findById(2L)).willReturn(Optional.of(testGoal(2L)));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L,
            new RoutineCreateRequestDto("영어 단어", dueDate, null, RoutineType.WEEKLY, 21, 5L, 2L));

    assertThat(result.getTitle()).isEqualTo("영어 단어");
    assertThat(result.getRoutineType()).isEqualTo(RoutineType.WEEKLY);
    assertThat(result.getRoutineDate()).isEqualTo(21);
    assertThat(result.getDueDate()).isEqualTo(dueDate);
    assertThat(result.getTagId()).isEqualTo(5L);
    assertThat(result.getGoalId()).isEqualTo(2L);
  }

  @Test
  void 루틴_생성_WEEKLY_routineDate_경계값_1_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.WEEKLY, 1, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(1);
  }

  @Test
  void 루틴_생성_WEEKLY_routineDate_경계값_127_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.WEEKLY, 127, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(127);
  }

  @Test
  void 루틴_생성_WEEKLY_routineDate_null이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.WEEKLY, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));

    verify(routineRepository, never()).save(any());
  }

  @Test
  void 루틴_생성_WEEKLY_routineDate_0이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.WEEKLY, 0, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  @Test
  void 루틴_생성_WEEKLY_routineDate_128이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.WEEKLY, 128, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  // ========== MONTHLY ==========

  @Test
  void 루틴_생성_MONTHLY_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L,
            new RoutineCreateRequestDto("월간 회고", null, null, RoutineType.MONTHLY, 15, null, null));

    assertThat(result.getRoutineType()).isEqualTo(RoutineType.MONTHLY);
    assertThat(result.getRoutineDate()).isEqualTo(15);
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_경계값_1_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.MONTHLY, 1, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(1);
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_경계값_31_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.MONTHLY, 31, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(31);
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_null이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.MONTHLY, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_0이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.MONTHLY, 0, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_32이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.MONTHLY, 32, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  // ========== tag / goal 없음 ==========

  @Test
  void 루틴_생성_존재하지_않는_tagId_404() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(tagRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.DAILY, null, 999L, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));

    verify(routineRepository, never()).save(any());
  }

  @Test
  void 루틴_생성_존재하지_않는_goalId_404() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(goalRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto(
                        "루틴", null, null, RoutineType.DAILY, null, null, 999L)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_NOT_FOUND));

    verify(routineRepository, never()).save(any());
  }

  // ========== Todo 즉시 생성 (createRoutine) ==========

  @Test
  void 루틴_생성_DAILY_Todo_즉시_생성됨() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    routineService.createRoutine(
        1L,
        new RoutineCreateRequestDto("아침 스트레칭", null, null, RoutineType.DAILY, null, null, null));

    verify(todoRepository).saveAndFlush(any(Todo.class));
  }

  @Test
  void 루틴_생성_WEEKLY_오늘_요일_포함_Todo_생성됨() {
    // TEST_DATE = Monday(bit=1). routineDate=1 → Monday만 포함 → 일치
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    routineService.createRoutine(
        1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.WEEKLY, 1, null, null));

    verify(todoRepository).saveAndFlush(any(Todo.class));
  }

  @Test
  void 루틴_생성_WEEKLY_오늘_요일_미포함이어도_Todo_즉시_생성되고_다음_반복일이_dueDate() {
    // TEST_DATE = 2024-01-15(Mon). routineDate=2 → Tuesday만 → 다음 발생 = 2024-01-16
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    routineService.createRoutine(
        1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.WEEKLY, 2, null, null));

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDueDate())
        .isEqualTo(LocalDate.of(2024, 1, 16).atTime(23, 59, 59));
  }

  @Test
  void 루틴_생성_MONTHLY_오늘_날짜_일치_Todo_생성됨() {
    // TEST_DATE = 15일. routineDate=15 → 일치
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    routineService.createRoutine(
        1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.MONTHLY, 15, null, null));

    verify(todoRepository).saveAndFlush(any(Todo.class));
  }

  @Test
  void 루틴_생성_MONTHLY_오늘_날짜_불일치여도_Todo_즉시_생성되고_다음_반복일이_dueDate() {
    // TEST_DATE = 2024-01-15. routineDate=16 → 다음 발생 = 2024-01-16
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    routineService.createRoutine(
        1L, new RoutineCreateRequestDto("루틴", null, null, RoutineType.MONTHLY, 16, null, null));

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDueDate())
        .isEqualTo(LocalDate.of(2024, 1, 16).atTime(23, 59, 59));
  }

  @Test
  void 루틴_생성_오늘_이미_Todo_존재시_중복_생성_안됨() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));
    given(todoRepository.existsByRoutineAndDueDate(any(), any())).willReturn(true);

    routineService.createRoutine(
        1L,
        new RoutineCreateRequestDto("아침 스트레칭", null, null, RoutineType.DAILY, null, null, null));

    verify(todoRepository, never()).saveAndFlush(any(Todo.class));
  }

  // ========== generateDailyTodos (배치) ==========

  // TEST_DATE = 2024-01-15 (월요일, DayOfWeek=1, bit=1), day of month = 15

  private Routine buildRoutine(RoutineType type, Integer routineDate) {
    return Routine.builder()
        .title("테스트 루틴")
        .routineType(type)
        .routineDate(routineDate)
        .user(testUser())
        .build();
  }

  @Test
  void generateDailyTodos_활성루틴_없으면_생성_안됨() {
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of());

    routineService.generateDailyTodos();

    verify(todoRepository, never()).saveAndFlush(any(Todo.class));
  }

  @Test
  void generateDailyTodos_DAILY_오늘_투두_생성됨() {
    // DAILY → isOccurrenceDay = 항상 true → 오늘(2024-01-15) 투두 생성
    Routine routine = buildRoutine(RoutineType.DAILY, null);
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));

    routineService.generateDailyTodos();

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDueDate())
        .isEqualTo(LocalDate.of(2024, 1, 15).atTime(23, 59, 59));
  }

  @Test
  void generateDailyTodos_WEEKLY_오늘이_해당요일_투두_생성됨() {
    // 오늘=월요일(bit=1), WEEKLY(mask=1) → isOccurrenceDay = true → 오늘 투두 생성
    Routine routine = buildRoutine(RoutineType.WEEKLY, 1);
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));

    routineService.generateDailyTodos();

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDueDate())
        .isEqualTo(LocalDate.of(2024, 1, 15).atTime(23, 59, 59));
  }

  @Test
  void generateDailyTodos_WEEKLY_오늘이_해당요일_아님_생성_안됨() {
    // 오늘=월요일(bit=1), WEEKLY(mask=2, 화요일) → isOccurrenceDay = false
    Routine routine = buildRoutine(RoutineType.WEEKLY, 2);
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));

    routineService.generateDailyTodos();

    verify(todoRepository, never()).saveAndFlush(any(Todo.class));
  }

  @Test
  void generateDailyTodos_MONTHLY_오늘이_해당일_투두_생성됨() {
    // 오늘=15일, MONTHLY(15일) → isOccurrenceDay = true → 오늘 투두 생성
    Routine routine = buildRoutine(RoutineType.MONTHLY, 15);
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));

    routineService.generateDailyTodos();

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDueDate())
        .isEqualTo(LocalDate.of(2024, 1, 15).atTime(23, 59, 59));
  }

  @Test
  void generateDailyTodos_MONTHLY_오늘이_해당일_아님_생성_안됨() {
    // 오늘=15일, MONTHLY(16일) → isOccurrenceDay = false
    Routine routine = buildRoutine(RoutineType.MONTHLY, 16);
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));

    routineService.generateDailyTodos();

    verify(todoRepository, never()).saveAndFlush(any(Todo.class));
  }

  @Test
  void generateDailyTodos_종료일_지난_루틴_생성_안됨() {
    // DAILY, 종료일 = 2024-01-14 → 오늘(Jan 15)이 종료일 이후 → 생성 안 함
    Routine routine =
        Routine.builder()
            .title("테스트 루틴")
            .routineType(RoutineType.DAILY)
            .dueDate(LocalDate.of(2024, 1, 14).atStartOfDay())
            .user(testUser())
            .build();
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));

    routineService.generateDailyTodos();

    verify(todoRepository, never()).saveAndFlush(any(Todo.class));
  }

  @Test
  void generateDailyTodos_오늘_투두_이미_존재시_중복_생성_안됨() {
    Routine routine = buildRoutine(RoutineType.DAILY, null);
    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));
    given(todoRepository.existsByRoutineAndDueDate(any(), any())).willReturn(true);

    routineService.generateDailyTodos();

    verify(todoRepository, never()).saveAndFlush(any(Todo.class));
  }

  // ========== updateMotherRoutine ==========

  private Routine motherRoutine() {
    Routine r = buildRoutine(RoutineType.DAILY, null);
    ReflectionTestUtils.setField(r, "id", 10L);
    return r;
  }

  @Test
  void 엄마루틴_수정_DAILY_성공_필수필드만() {
    Routine routine = motherRoutine();
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));

    RoutineResponseDto result =
        routineService.updateMotherRoutine(
            1L,
            10L,
            new RoutineUpdateRequestDto("수정된 루틴", null, null, RoutineType.DAILY, null, null));

    assertThat(result.getTitle()).isEqualTo("수정된 루틴");
    assertThat(result.getRoutineType()).isEqualTo(RoutineType.DAILY);
    assertThat(result.getRoutineDate()).isNull();
    assertThat(result.getDueDate()).isNull();
    assertThat(result.getTagId()).isNull();
  }

  @Test
  void 엄마루틴_수정_WEEKLY_전체필드() {
    Routine routine = motherRoutine();
    LocalDateTime dueDate = LocalDateTime.of(2025, 12, 31, 0, 0);
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    given(tagRepository.findById(5L)).willReturn(Optional.of(testTag(5L)));

    RoutineResponseDto result =
        routineService.updateMotherRoutine(
            1L,
            10L,
            new RoutineUpdateRequestDto("수정된 루틴", dueDate, null, RoutineType.WEEKLY, 21, 5L));

    assertThat(result.getTitle()).isEqualTo("수정된 루틴");
    assertThat(result.getRoutineType()).isEqualTo(RoutineType.WEEKLY);
    assertThat(result.getRoutineDate()).isEqualTo(21);
    assertThat(result.getDueDate()).isEqualTo(dueDate);
    assertThat(result.getTagId()).isEqualTo(5L);
  }

  @Test
  void 엄마루틴_수정_하위루틴_ID_전달_400() {
    User user = testUser();
    Routine parent = buildRoutine(RoutineType.DAILY, null);
    ReflectionTestUtils.setField(parent, "id", 10L);
    Routine child = Routine.builder().title("하위").user(user).parent(parent).build();
    ReflectionTestUtils.setField(child, "id", 11L);
    given(routineRepository.findById(11L)).willReturn(Optional.of(child));

    assertThatThrownBy(
            () ->
                routineService.updateMotherRoutine(
                    1L,
                    11L,
                    new RoutineUpdateRequestDto("수정", null, null, RoutineType.DAILY, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_TARGET));
  }

  @Test
  void 엄마루틴_수정_routineDate_범위오류_400() {
    Routine routine = motherRoutine();
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));

    assertThatThrownBy(
            () ->
                routineService.updateMotherRoutine(
                    1L,
                    10L,
                    new RoutineUpdateRequestDto("수정", null, null, RoutineType.WEEKLY, 128, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_INVALID_DATE));
  }

  @Test
  void 엄마루틴_수정_존재하지않는_tagId_404() {
    Routine routine = motherRoutine();
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    given(tagRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                routineService.updateMotherRoutine(
                    1L,
                    10L,
                    new RoutineUpdateRequestDto("수정", null, null, RoutineType.DAILY, null, 999L)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(TagErrorCode.TAG_NOT_FOUND));
  }

  @Test
  void 엄마루틴_수정_루틴없음_404() {
    given(routineRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                routineService.updateMotherRoutine(
                    1L,
                    999L,
                    new RoutineUpdateRequestDto("수정", null, null, RoutineType.DAILY, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(RoutineErrorCode.ROUTINE_NOT_FOUND));
  }

  // ========== generateDailyTodos with override ==========

  @Test
  void generateDailyTodos_override_제목_태그_반영됨() {
    Tag tag = testTag(5L);
    Routine routine = buildRoutine(RoutineType.DAILY, null);

    RoutineOverride override =
        RoutineOverride.builder().routine(routine).overrideDate(TEST_DATE).build();
    override.updateContent("오버라이드 제목", tag);

    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));
    given(routineOverrideRepository.findByRoutineIdInAndOverrideDate(any(), any()))
        .willReturn(List.of(override));

    routineService.generateDailyTodos();

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getTitle()).isEqualTo("오버라이드 제목");
    assertThat(captor.getValue().getTag()).isEqualTo(tag);
  }

  @Test
  void generateDailyTodos_override_skip_이면_생성_안됨() {
    Routine routine = buildRoutine(RoutineType.DAILY, null);

    RoutineOverride override =
        RoutineOverride.builder().routine(routine).overrideDate(TEST_DATE).build();
    override.skip();

    given(routineRepository.findAllActiveMotherRoutines()).willReturn(List.of(routine));
    given(routineOverrideRepository.findByRoutineIdInAndOverrideDate(any(), any()))
        .willReturn(List.of(override));

    routineService.generateDailyTodos();

    verify(todoRepository, never()).saveAndFlush(any(Todo.class));
  }

  // ========== updateMotherRoutine — override 정리 및 오늘 todo 동기화 ==========

  @Test
  void 엄마루틴_수정_오늘_이후_override_삭제됨() {
    Routine routine = motherRoutine();
    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));

    routineService.updateMotherRoutine(
        1L, 10L, new RoutineUpdateRequestDto("수정된 루틴", null, null, RoutineType.DAILY, null, null));

    verify(routineOverrideRepository)
        .deleteByRoutineAndOverrideDateGreaterThanEqual(routine, TEST_DATE);
  }

  @Test
  void 엄마루틴_수정_오늘_todo_존재시_제목_태그_즉시_반영() {
    Routine routine = motherRoutine();
    Todo existingTodo =
        Todo.builder().title("기존 제목").user(testUser()).isPinned(false).routine(routine).build();
    ReflectionTestUtils.setField(existingTodo, "id", 100L);

    given(routineRepository.findById(10L)).willReturn(Optional.of(routine));
    given(todoRepository.findMotherTodoByRoutineAndDate(any(), any(), any()))
        .willReturn(Optional.of(existingTodo));

    routineService.updateMotherRoutine(
        1L, 10L, new RoutineUpdateRequestDto("수정된 루틴", null, null, RoutineType.DAILY, null, null));

    assertThat(existingTodo.getTitle()).isEqualTo("수정된 루틴");
  }
}
