package plana.replan.domain.routine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.entity.TagColor;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

  @Mock private RoutineRepository routineRepository;
  @Mock private UserRepository userRepository;
  @Mock private TagRepository tagRepository;
  @Mock private GoalRepository goalRepository;

  @InjectMocks private RoutineService routineService;

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
    Tag tag = Tag.builder().title("업무").color(TagColor.BLUE).user(testUser()).build();
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
                    new RoutineCreateRequestDto("루틴", null, RoutineType.DAILY, null, null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));

    verify(routineRepository, never()).save(any());
  }

  // ========== DAILY ==========

  @Test
  void 루틴_생성_DAILY_성공_선택필드_없음() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("아침 스트레칭", null, RoutineType.DAILY, null, null, null));

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

    // DAILY에서 routineDate=999를 넘겨도 검증 없이 저장됨
    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, RoutineType.DAILY, 999, null, null));

    assertThat(result.getRoutineType()).isEqualTo(RoutineType.DAILY);
    assertThat(result.getRoutineDate()).isEqualTo(999);
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
            1L, new RoutineCreateRequestDto("영어 단어", dueDate, RoutineType.WEEKLY, 21, 5L, 2L));

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
            1L, new RoutineCreateRequestDto("루틴", null, RoutineType.WEEKLY, 1, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(1);
  }

  @Test
  void 루틴_생성_WEEKLY_routineDate_경계값_127_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, RoutineType.WEEKLY, 127, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(127);
  }

  @Test
  void 루틴_생성_WEEKLY_routineDate_null이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto("루틴", null, RoutineType.WEEKLY, null, null, null)))
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
                    new RoutineCreateRequestDto("루틴", null, RoutineType.WEEKLY, 0, null, null)))
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
                    new RoutineCreateRequestDto("루틴", null, RoutineType.WEEKLY, 128, null, null)))
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
            1L, new RoutineCreateRequestDto("월간 회고", null, RoutineType.MONTHLY, 15, null, null));

    assertThat(result.getRoutineType()).isEqualTo(RoutineType.MONTHLY);
    assertThat(result.getRoutineDate()).isEqualTo(15);
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_경계값_1_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, RoutineType.MONTHLY, 1, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(1);
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_경계값_31_성공() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));

    RoutineResponseDto result =
        routineService.createRoutine(
            1L, new RoutineCreateRequestDto("루틴", null, RoutineType.MONTHLY, 31, null, null));

    assertThat(result.getRoutineDate()).isEqualTo(31);
  }

  @Test
  void 루틴_생성_MONTHLY_routineDate_null이면_400() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    assertThatThrownBy(
            () ->
                routineService.createRoutine(
                    1L,
                    new RoutineCreateRequestDto("루틴", null, RoutineType.MONTHLY, null, null, null)))
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
                    new RoutineCreateRequestDto("루틴", null, RoutineType.MONTHLY, 0, null, null)))
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
                    new RoutineCreateRequestDto("루틴", null, RoutineType.MONTHLY, 32, null, null)))
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
                        "루틴", null, RoutineType.DAILY, null, 999L, null)))
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
                        "루틴", null, RoutineType.DAILY, null, null, 999L)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_NOT_FOUND));

    verify(routineRepository, never()).save(any());
  }
}
