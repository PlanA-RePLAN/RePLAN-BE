package plana.replan.domain.routine.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import plana.replan.domain.routine.entity.RoutineType;

class RoutineDaysTest {

  @Test
  @DisplayName("WEEKLY 배열 → 비트마스크 (월0…일6 → 비트 index)")
  void weeklyToMask() {
    // 월·수·금 = [0,2,4] → 1 + 4 + 16 = 21
    assertThat(RoutineDays.toMask(RoutineType.WEEKLY, List.of(0, 2, 4))).isEqualTo(21);
    // 일요일 = [6] → 1<<6 = 64
    assertThat(RoutineDays.toMask(RoutineType.WEEKLY, List.of(6))).isEqualTo(64);
  }

  @Test
  @DisplayName("WEEKLY 비트마스크 → 배열")
  void weeklyToDays() {
    assertThat(RoutineDays.toDays(RoutineType.WEEKLY, 21)).containsExactly(0, 2, 4);
    assertThat(RoutineDays.toDays(RoutineType.WEEKLY, 64)).containsExactly(6);
  }

  @Test
  @DisplayName("MONTHLY 배열 → 비트마스크 (일자 d → 비트 d-1)")
  void monthlyToMask() {
    // 15일 = [15] → 1<<14 = 16384
    assertThat(RoutineDays.toMask(RoutineType.MONTHLY, List.of(15))).isEqualTo(16384);
    // 3·20일 = [3,20] → (1<<2)+(1<<19) = 4 + 524288 = 524292
    assertThat(RoutineDays.toMask(RoutineType.MONTHLY, List.of(3, 20))).isEqualTo(524292);
    // 31일 = [31] → 1<<30 = 1073741824
    assertThat(RoutineDays.toMask(RoutineType.MONTHLY, List.of(31))).isEqualTo(1073741824);
  }

  @Test
  @DisplayName("MONTHLY 비트마스크 → 배열")
  void monthlyToDays() {
    assertThat(RoutineDays.toDays(RoutineType.MONTHLY, 16384)).containsExactly(15);
    assertThat(RoutineDays.toDays(RoutineType.MONTHLY, 524292)).containsExactly(3, 20);
  }

  @Test
  @DisplayName("배열↔비트마스크 왕복 변환이 일치한다")
  void roundTrip() {
    List<Integer> weekly = List.of(0, 3, 6);
    assertThat(
            RoutineDays.toDays(RoutineType.WEEKLY, RoutineDays.toMask(RoutineType.WEEKLY, weekly)))
        .isEqualTo(weekly);
    List<Integer> monthly = List.of(1, 15, 31);
    assertThat(
            RoutineDays.toDays(
                RoutineType.MONTHLY, RoutineDays.toMask(RoutineType.MONTHLY, monthly)))
        .isEqualTo(monthly);
  }

  @Test
  @DisplayName("DAILY는 null 또는 빈 배열만 허용한다")
  void dailyIsNull() {
    assertThat(RoutineDays.toMask(RoutineType.DAILY, null)).isNull();
    assertThat(RoutineDays.toMask(RoutineType.DAILY, List.of())).isNull();
    assertThat(RoutineDays.toDays(RoutineType.DAILY, 21)).isNull();
    assertThat(RoutineDays.isValid(RoutineType.DAILY, null)).isTrue();
    assertThat(RoutineDays.isValid(RoutineType.DAILY, List.of())).isTrue();
    assertThat(RoutineDays.isValid(RoutineType.DAILY, List.of(1, 2))).isFalse();
  }

  @Test
  @DisplayName("유효성: WEEKLY는 0~6, MONTHLY는 1~31, 비어있으면 무효")
  void validity() {
    assertThat(RoutineDays.isValid(RoutineType.WEEKLY, List.of(0, 6))).isTrue();
    assertThat(RoutineDays.isValid(RoutineType.WEEKLY, List.of(7))).isFalse(); // 범위 밖
    assertThat(RoutineDays.isValid(RoutineType.WEEKLY, List.of())).isFalse(); // 빈 배열
    assertThat(RoutineDays.isValid(RoutineType.WEEKLY, null)).isFalse(); // null
    assertThat(RoutineDays.isValid(RoutineType.MONTHLY, List.of(1, 31))).isTrue();
    assertThat(RoutineDays.isValid(RoutineType.MONTHLY, List.of(0))).isFalse(); // 0은 무효
    assertThat(RoutineDays.isValid(RoutineType.MONTHLY, List.of(32))).isFalse(); // 32 무효
  }
}
