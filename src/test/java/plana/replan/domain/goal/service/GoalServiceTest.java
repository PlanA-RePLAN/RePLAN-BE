package plana.replan.domain.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.goal.dto.common.GoalSingleResponse;
import plana.replan.domain.goal.dto.create.GoalCreateRequest;
import plana.replan.domain.goal.dto.list.GoalsByDateResponse;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
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
    given(goalRepository.save(any())).willReturn(savedGoal);

    GoalCreateRequest request =
        new GoalCreateRequest("토익 900점 달성", "2025-12-31", null, "https://toeic.ets.org");

    GoalSingleResponse response = goalService.createGoal(1L, request);

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
    given(goalRepository.save(any())).willReturn(savedGoal);

    GoalCreateRequest request = new GoalCreateRequest("독서 50권", null, null, null);

    GoalSingleResponse response = goalService.createGoal(1L, request);

    assertThat(response.title()).isEqualTo("독서 50권");
    assertThat(response.dueDate()).isNull();
    assertThat(response.reference()).isNull();
  }

  @Test
  void 목표_생성_유저_없음_404() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> goalService.createGoal(999L, new GoalCreateRequest("제목", null, null, null)))
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
  void 목표_조회_전체_날짜_내림차순_그룹핑() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal1 =
        mockGoal(
            10L,
            "목표A",
            LocalDateTime.of(2026, 5, 4, 10, 0),
            null,
            LocalDateTime.of(2026, 5, 4, 10, 0));
    Goal goal2 = mockGoal(9L, "목표B", null, null, LocalDateTime.of(2026, 5, 3, 9, 0));
    given(goalRepository.findByUserOrderByCreatedAtDescIdAsc(user))
        .willReturn(List.of(goal1, goal2));

    List<GoalsByDateResponse> result = goalService.getGoals(1L, null, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 5, 4));
    assertThat(result.get(0).goals()).hasSize(1);
    assertThat(result.get(0).goals().get(0).title()).isEqualTo("목표A");
    assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 5, 3));
  }

  @Test
  void 목표_조회_같은날_목표_ID_오름차순() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    LocalDateTime sameDay = LocalDateTime.of(2026, 5, 4, 0, 0);
    Goal goal1 = mockGoal(10L, "나중 목표", null, null, LocalDateTime.of(2026, 5, 4, 12, 0));
    Goal goal2 = mockGoal(8L, "먼저 목표", null, null, LocalDateTime.of(2026, 5, 4, 9, 0));
    // DB에서 createdAt DESC로 오지만 같은 날이면 id ASC로 재정렬
    given(goalRepository.findByUserOrderByCreatedAtDescIdAsc(user))
        .willReturn(List.of(goal1, goal2));

    List<GoalsByDateResponse> result = goalService.getGoals(1L, null, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).goals().get(0).id()).isEqualTo(8L);
    assertThat(result.get(0).goals().get(1).id()).isEqualTo(10L);
  }

  @Test
  void 목표_조회_연도별() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal = mockGoal(10L, "토익", null, null, LocalDateTime.of(2026, 5, 4, 10, 0));
    given(goalRepository.findByUserAndCreatedAtYear(user, 2026)).willReturn(List.of(goal));

    List<GoalsByDateResponse> result = goalService.getGoals(1L, 2026, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).goals().get(0).title()).isEqualTo("토익");
  }

  @Test
  void 목표_조회_월별() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal = mockGoal(10L, "토익", null, null, LocalDateTime.of(2026, 5, 4, 10, 0));
    given(goalRepository.findByUserAndCreatedAtYearAndMonth(user, 2026, 5))
        .willReturn(List.of(goal));

    List<GoalsByDateResponse> result = goalService.getGoals(1L, 2026, 5);

    assertThat(result).hasSize(1);
  }

  @Test
  void 목표_조회_year_없이_month만_전달하면_400() {
    assertThatThrownBy(() -> goalService.getGoals(1L, null, 5))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_INVALID_FILTER));
  }

  @Test
  void 목표_조회_month_0이면_400() {
    assertThatThrownBy(() -> goalService.getGoals(1L, 2026, 0))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_INVALID_MONTH));
  }

  @Test
  void 목표_조회_month_13이면_400() {
    assertThatThrownBy(() -> goalService.getGoals(1L, 2026, 13))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_INVALID_MONTH));
  }

  @Test
  void 목표_조회_dueDate_null_목표_포함() {
    User user = mock(User.class);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    Goal goal = mockGoal(10L, "마감없는 목표", null, null, LocalDateTime.of(2026, 5, 4, 10, 0));
    given(goalRepository.findByUserOrderByCreatedAtDescIdAsc(user)).willReturn(List.of(goal));

    List<GoalsByDateResponse> result = goalService.getGoals(1L, null, null);

    assertThat(result.get(0).goals().get(0).dueDate()).isNull();
  }

  @Test
  void 목표_조회_유저_없음_404() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.getGoals(999L, null, null))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  private Goal mockGoal(
      Long id, String title, LocalDateTime dueDate, String reference, LocalDateTime createdAt) {
    Goal goal = mock(Goal.class);
    given(goal.getId()).willReturn(id);
    given(goal.getTitle()).willReturn(title);
    given(goal.getDueDate()).willReturn(dueDate);
    given(goal.getReference()).willReturn(reference);
    given(goal.getCreatedAt()).willReturn(createdAt);
    return goal;
  }
}
