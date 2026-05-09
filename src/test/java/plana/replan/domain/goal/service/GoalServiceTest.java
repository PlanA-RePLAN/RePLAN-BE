package plana.replan.domain.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import plana.replan.domain.goal.dto.GoalCreateRequest;
import plana.replan.domain.goal.dto.GoalPageResponse;
import plana.replan.domain.goal.dto.GoalResponse;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoalServiceTest {

  @Mock private GoalRepository goalRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private GoalService goalService;

  // ========== createGoal ==========

  @Test
  void 목표_생성_성공() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal savedGoal = mock(Goal.class);
    given(savedGoal.getId()).willReturn(42L);
    given(savedGoal.getTitle()).willReturn("토익 900점 달성");
    given(savedGoal.getDueDate()).willReturn(LocalDateTime.of(2025, 12, 31, 0, 0));
    given(savedGoal.getReference()).willReturn("https://toeic.ets.org");
    given(savedGoal.getUpdatedAt()).willReturn(LocalDateTime.of(2025, 5, 7, 12, 0));
    given(goalRepository.save(any())).willReturn(savedGoal);

    GoalCreateRequest request =
        new GoalCreateRequest(
            "토익 900점 달성", LocalDateTime.of(2025, 12, 31, 0, 0), "https://toeic.ets.org");

    GoalResponse response = goalService.createGoal(1L, request);

    assertThat(response.id()).isEqualTo(42L);
    assertThat(response.title()).isEqualTo("토익 900점 달성");
    assertThat(response.reference()).isEqualTo("https://toeic.ets.org");
    verify(goalRepository).save(any());
  }

  @Test
  void 목표_생성_성공_선택필드_없이() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal savedGoal = mock(Goal.class);
    given(savedGoal.getId()).willReturn(1L);
    given(savedGoal.getTitle()).willReturn("독서 50권");
    given(savedGoal.getDueDate()).willReturn(null);
    given(savedGoal.getReference()).willReturn(null);
    given(savedGoal.getUpdatedAt()).willReturn(LocalDateTime.now());
    given(goalRepository.save(any())).willReturn(savedGoal);

    GoalCreateRequest request = new GoalCreateRequest("독서 50권", null, null);

    GoalResponse response = goalService.createGoal(1L, request);

    assertThat(response.title()).isEqualTo("독서 50권");
    assertThat(response.dueDate()).isNull();
    assertThat(response.reference()).isNull();
  }

  @Test
  void 목표_생성_유저_없음_404() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.createGoal(999L, new GoalCreateRequest("제목", null, null)))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  // ========== deleteGoal ==========

  @Test
  void 목표_삭제_성공() {
    User owner = mock(User.class);
    given(owner.getId()).willReturn(1L);

    Goal goal = mock(Goal.class);
    given(goal.getUser()).willReturn(owner);
    given(goalRepository.findById(42L)).willReturn(Optional.of(goal));

    goalService.deleteGoal(1L, 42L);

    verify(goal).softDelete();
  }

  @Test
  void 목표_삭제_목표_없음_404() {
    given(goalRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.deleteGoal(1L, 999L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_NOT_FOUND));
  }

  @Test
  void 목표_삭제_타인_목표_403() {
    User owner = mock(User.class);
    given(owner.getId()).willReturn(2L);

    Goal goal = mock(Goal.class);
    given(goal.getUser()).willReturn(owner);
    given(goalRepository.findById(42L)).willReturn(Optional.of(goal));

    assertThatThrownBy(() -> goalService.deleteGoal(1L, 42L))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_ACCESS_DENIED));
  }

  // ========== getGoals ==========

  @Test
  void 목표_조회_첫_페이지_커서없음() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal = mockGoal(10L, "운동 습관", null, null);
    given(goalRepository.findByUserOrderByIdDesc(any(), any(Pageable.class)))
        .willReturn(List.of(goal));

    GoalPageResponse response = goalService.getGoals(1L, null, 10, null);

    assertThat(response.goals()).hasSize(1);
    assertThat(response.hasNext()).isFalse();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void 목표_조회_커서_있는_페이지() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal = mockGoal(5L, "독서", null, null);
    given(
            goalRepository.findByUserAndIdLessThanOrderByIdDesc(
                any(), any(Long.class), any(Pageable.class)))
        .willReturn(List.of(goal));

    GoalPageResponse response = goalService.getGoals(1L, 10L, 10, null);

    assertThat(response.goals()).hasSize(1);
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  void 목표_조회_연도_필터_첫_페이지() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal = mockGoal(10L, "토익", LocalDateTime.of(2025, 6, 1, 0, 0), null);
    given(goalRepository.findByUserAndYear(any(), any(Integer.class), any(Pageable.class)))
        .willReturn(List.of(goal));

    GoalPageResponse response = goalService.getGoals(1L, null, 10, 2025);

    assertThat(response.goals()).hasSize(1);
  }

  @Test
  void 목표_조회_연도_필터_커서_있는_페이지() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal = mockGoal(5L, "토익", LocalDateTime.of(2025, 6, 1, 0, 0), null);
    given(
            goalRepository.findByUserAndYearAndIdLessThan(
                any(), any(Integer.class), any(Long.class), any(Pageable.class)))
        .willReturn(List.of(goal));

    GoalPageResponse response = goalService.getGoals(1L, 10L, 10, 2025);

    assertThat(response.goals()).hasSize(1);
  }

  @Test
  void 목표_조회_다음_페이지_있음() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    List<Goal> goals =
        List.of(
            mockGoal(10L, "목표1", null, null),
            mockGoal(9L, "목표2", null, null),
            mockGoal(8L, "목표3", null, null));
    given(goalRepository.findByUserOrderByIdDesc(any(), any(Pageable.class))).willReturn(goals);

    GoalPageResponse response = goalService.getGoals(1L, null, 2, null);

    assertThat(response.goals()).hasSize(2);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo(9L);
  }

  @Test
  void 목표_조회_마지막_페이지() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    List<Goal> goals = List.of(mockGoal(10L, "목표1", null, null), mockGoal(9L, "목표2", null, null));
    given(goalRepository.findByUserOrderByIdDesc(any(), any(Pageable.class))).willReturn(goals);

    GoalPageResponse response = goalService.getGoals(1L, null, 10, null);

    assertThat(response.goals()).hasSize(2);
    assertThat(response.hasNext()).isFalse();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void 목표_조회_유저_없음_404() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.getGoals(999L, null, 10, null))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  void 목표_조회_size_0이면_400() {
    assertThatThrownBy(() -> goalService.getGoals(1L, null, 0, null))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void 목표_조회_size_101이면_400() {
    assertThatThrownBy(() -> goalService.getGoals(1L, null, 101, null))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  private Goal mockGoal(Long id, String title, LocalDateTime dueDate, String reference) {
    Goal goal = mock(Goal.class);
    given(goal.getId()).willReturn(id);
    given(goal.getTitle()).willReturn(title);
    given(goal.getDueDate()).willReturn(dueDate);
    given(goal.getReference()).willReturn(reference);
    given(goal.getUpdatedAt()).willReturn(LocalDateTime.now());
    return goal;
  }
}
