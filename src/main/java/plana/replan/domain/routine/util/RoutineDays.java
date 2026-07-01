package plana.replan.domain.routine.util;

import java.util.ArrayList;
import java.util.List;
import plana.replan.domain.routine.entity.RoutineType;

/**
 * 반복 날짜의 API 표현(숫자 배열)과 내부 저장 표현(비트마스크 정수)을 변환한다.
 *
 * <p>프론트는 배열로 주고받고, 서버 내부(엔티티·발생일 판정·조회 쿼리)는 그대로 비트마스크를 쓴다. 이 변환은 요청 수신·응답 생성 경계에서만 일어난다.
 *
 * <ul>
 *   <li>WEEKLY: 요일 인덱스 배열 (월=0, 화=1 … 일=6) → 비트 index 그대로. 예) [0,2,4] → 1|4|16 = 21
 *   <li>MONTHLY: 일자 배열 (1~31) → 비트 (일-1). 예) [3,20] → (1&lt;&lt;2)|(1&lt;&lt;19) = 524292
 *   <li>DAILY: 반복 날짜 없음 → null
 * </ul>
 */
public final class RoutineDays {

  private RoutineDays() {}

  private static final int WEEKLY_MIN = 0;
  private static final int WEEKLY_MAX = 6;
  private static final int MONTHLY_MIN = 1;
  private static final int MONTHLY_MAX = 31;

  /** WEEKLY/MONTHLY 배열 값이 유효 범위 안이고 비어있지 않은지 검사한다. DAILY는 항상 true(무시). */
  public static boolean isValid(RoutineType type, List<Integer> days) {
    if (type == RoutineType.DAILY) {
      return true;
    }
    if (days == null || days.isEmpty()) {
      return false;
    }
    int min = type == RoutineType.WEEKLY ? WEEKLY_MIN : MONTHLY_MIN;
    int max = type == RoutineType.WEEKLY ? WEEKLY_MAX : MONTHLY_MAX;
    return days.stream().allMatch(d -> d != null && d >= min && d <= max);
  }

  /** 배열 → 비트마스크. DAILY이거나 days가 null이면 null. (유효성 검사는 호출 전에 {@link #isValid} 로 한다.) */
  public static Integer toMask(RoutineType type, List<Integer> days) {
    if (type == null || type == RoutineType.DAILY || days == null) {
      return null;
    }
    int mask = 0;
    for (Integer d : days) {
      int bit = type == RoutineType.WEEKLY ? d : d - 1;
      mask |= (1 << bit);
    }
    return mask;
  }

  /** 비트마스크 → 배열. type/mask가 null이면 null. */
  public static List<Integer> toDays(RoutineType type, Integer mask) {
    if (type == null || type == RoutineType.DAILY || mask == null) {
      return null;
    }
    int min = type == RoutineType.WEEKLY ? WEEKLY_MIN : MONTHLY_MIN;
    int max = type == RoutineType.WEEKLY ? WEEKLY_MAX : MONTHLY_MAX;
    List<Integer> days = new ArrayList<>();
    for (int d = min; d <= max; d++) {
      int bit = type == RoutineType.WEEKLY ? d : d - 1;
      if ((mask & (1 << bit)) != 0) {
        days.add(d);
      }
    }
    return days;
  }
}
