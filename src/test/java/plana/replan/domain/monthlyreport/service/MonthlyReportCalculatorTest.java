package plana.replan.domain.monthlyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.monthlyreport.entity.MonthlyReport;
import plana.replan.domain.monthlyreport.repository.MonthlyReportRepository;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class MonthlyReportCalculatorTest {

  @Mock private TodoRepository todoRepository;
  @Mock private ReplanRepository replanRepository;
  @Mock private MonthlyReportRepository monthlyReportRepository;

  @InjectMocks private MonthlyReportCalculator calculator;

  private static final YearMonth TARGET = YearMonth.of(2025, 5);

  private User user() {
    User u =
        User.builder()
            .email("test@test.com")
            .nickname("테스트")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    ReflectionTestUtils.setField(u, "id", 1L);
    return u;
  }

  private Tag tag(String title) {
    Tag t = Tag.builder().title(title).color("#3B82F6").user(user()).build();
    ReflectionTestUtils.setField(t, "id", 1L);
    return t;
  }

  private Todo todo(User user, boolean completed, Tag tag, LocalDateTime dueDate) {
    Todo t =
        Todo.builder().title("투두").user(user).tag(tag).dueDate(dueDate).isPinned(false).build();
    ReflectionTestUtils.setField(t, "isCompleted", completed);
    // 14일 조건 충족: 대상 월 1일에 생성된 것으로 설정 (월말 기준 30일+ 이전)
    ReflectionTestUtils.setField(t, "createdAt", TARGET.atDay(1).atStartOfDay());
    return t;
  }

  private Replan replan(Todo todo, String reason) {
    return Replan.builder().todo(todo).failureReason1(reason).build();
  }

  private void stubNoPrev() {
    given(monthlyReportRepository.findByUserAndReportMonth(any(), any()))
        .willReturn(Optional.empty());
  }

  // ── 기본 달성률 ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("투두 없음: totalTodos=0, achievementRate=0, hasActivity=false")
  void calculate_noTodos_zeroStats() {
    given(todoRepository.findMonthlyTodos(any(), any(), any())).willReturn(List.of());
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(user(), TARGET);

    assertThat(stats.totalTodos()).isZero();
    assertThat(stats.completedTodos()).isZero();
    assertThat(stats.achievementRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(stats.hasActivity()).isFalse();
    assertThat(stats.replanCount()).isZero();
    assertThat(stats.replanAchievementEffect()).isNull();
  }

  @Test
  @DisplayName("투두 4개 중 3개 완료: achievementRate=75.00, hasActivity=true")
  void calculate_partialCompletion_correctRate() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    List<Todo> todos =
        List.of(
            todo(u, true, null, due),
            todo(u, true, null, due),
            todo(u, true, null, due),
            todo(u, false, null, due));

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(todos) // 당월
        .willReturn(List.of()); // 전월
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.totalTodos()).isEqualTo(4);
    assertThat(stats.completedTodos()).isEqualTo(3);
    assertThat(stats.achievementRate()).isEqualByComparingTo(new BigDecimal("75.00"));
    assertThat(stats.hasActivity()).isTrue();
  }

  // ── prevMonthDiff ────────────────────────────────────────────────────────

  @Test
  @DisplayName("이전 달 리포트 존재: prevMonthDiff = 현재 달성률 - 이전 달성률")
  void calculate_prevReportExists_diffCalculated() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    List<Todo> todos = List.of(todo(u, true, null, due), todo(u, false, null, due)); // 50%

    MonthlyReport prev =
        MonthlyReport.builder()
            .user(u)
            .reportMonth(TARGET.minusMonths(1).atDay(1))
            .totalTodos(10)
            .completedTodos(4)
            .achievementRate(new BigDecimal("40.00"))
            .build();

    given(todoRepository.findMonthlyTodos(any(), any(), any())).willReturn(todos);
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of());
    given(monthlyReportRepository.findByUserAndReportMonth(any(), any()))
        .willReturn(Optional.of(prev));

    CalculatedStats stats = calculator.calculate(u, TARGET);

    // 50.00 - 40.00 = 10.00
    assertThat(stats.prevMonthDiff()).isEqualByComparingTo(new BigDecimal("10.00"));
  }

  @Test
  @DisplayName("이전 달 리포트 없고 이전 달 투두도 없음: prevMonthDiff=null")
  void calculate_noPrevData_prevMonthDiffNull() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    List<Todo> todos = List.of(todo(u, true, null, due));

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(todos) // 당월
        .willReturn(List.of()); // 전월
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.prevMonthDiff()).isNull();
  }

  // ── 태그별 달성률 ────────────────────────────────────────────────────────

  @Test
  @DisplayName("태그별 투두 2개 이상: bestTag=운동(100%), worstTag=독서(0%)")
  void calculate_tagsWithMinTwo_tagStatsCalculated() {
    User u = user();
    Tag exercise = tag("운동");
    Tag reading = tag("독서");
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();

    List<Todo> todos =
        List.of(
            todo(u, true, exercise, due),
            todo(u, true, exercise, due), // 운동: 2/2 = 100%
            todo(u, false, reading, due),
            todo(u, false, reading, due)); // 독서: 0/2 = 0%

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(todos)
        .willReturn(List.of());
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.analysisData().bestAchievementTag()).isNotNull();
    assertThat(stats.analysisData().bestAchievementTag().tag()).isEqualTo("운동");
    assertThat(stats.analysisData().worstAchievementTag()).isNotNull();
    assertThat(stats.analysisData().worstAchievementTag().tag()).isEqualTo("독서");
  }

  @Test
  @DisplayName("태그별 투두 1개: 2개 미만 조건으로 bestTag, worstTag 모두 null")
  void calculate_tagWithOnlyOne_noTagStats() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    List<Todo> todos = List.of(todo(u, true, tag("운동"), due)); // 1개만

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(todos)
        .willReturn(List.of());
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.analysisData().bestAchievementTag()).isNull();
    assertThat(stats.analysisData().worstAchievementTag()).isNull();
  }

  // ── 실패 사유 depth 변환 ─────────────────────────────────────────────────

  @Test
  @DisplayName("depth-3 실패 사유(CONDITION_SLEEP_3H_UNDER)는 depth-1 라벨(컨디션 난조)로 그룹화")
  void calculate_depth3FailureReason_groupedToDepth1Label() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    Todo t = todo(u, false, null, due);
    Replan r = replan(t, "CONDITION_SLEEP_3H_UNDER");

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(List.of(t))
        .willReturn(List.of());
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of(r));
    given(todoRepository.findReplanDerivedMonthlyTodos(any(), any(), any())).willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.analysisData().topFailureReason()).isEqualTo("컨디션 난조");
    assertThat(stats.analysisData().failureDistribution()).hasSize(1);
    assertThat(stats.analysisData().failureDistribution().get(0).reason()).isEqualTo("컨디션 난조");
  }

  @Test
  @DisplayName("depth-1 실패 사유(MENTAL_RESISTANCE)는 그대로 라벨(심리적 저항)로 변환")
  void calculate_depth1FailureReason_usesOwnLabel() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    Todo t = todo(u, false, null, due);
    Replan r = replan(t, "MENTAL_RESISTANCE");

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(List.of(t))
        .willReturn(List.of());
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of(r));
    given(todoRepository.findReplanDerivedMonthlyTodos(any(), any(), any())).willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.analysisData().topFailureReason()).isEqualTo("심리적 저항");
  }

  // ── 리플랜 효과 ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("리플랜 1개, 파생 투두 1개 달성: replanAchievementEffect=100.00")
  void calculate_withReplans_effectCalculated() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    Todo t = todo(u, false, null, due);
    Replan r = replan(t, "MENTAL_RESISTANCE");
    Todo derived = todo(u, true, null, due);

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(List.of(t))
        .willReturn(List.of());
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of(r));
    given(todoRepository.findReplanDerivedMonthlyTodos(any(), any(), any()))
        .willReturn(List.of(derived));
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.replanCount()).isEqualTo(1);
    assertThat(stats.replanAchievementEffect()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("리플랜 없음: replanCount=0, replanAchievementEffect=null")
  void calculate_noReplans_effectNull() {
    User u = user();
    LocalDateTime due = TARGET.atDay(10).atStartOfDay();
    List<Todo> todos = List.of(todo(u, true, null, due));

    given(todoRepository.findMonthlyTodos(any(), any(), any()))
        .willReturn(todos)
        .willReturn(List.of());
    given(replanRepository.findByUserAndCreatedAtBetween(any(), any(), any()))
        .willReturn(List.of());
    stubNoPrev();

    CalculatedStats stats = calculator.calculate(u, TARGET);

    assertThat(stats.replanCount()).isZero();
    assertThat(stats.replanAchievementEffect()).isNull();
  }
}
