package plana.replan.domain.routine.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 쪽지(routine_override)의 override_subtodos jsonb 배열 원소. 행(Todo)이 아직 없는 회차에 예약해 둔 하위 투두 하나로, 배치가 행을
 * 만들 때 완료 상태까지 실제 하위 투두로 승계된다.
 */
public record ReservedSubtodo(String title, @JsonProperty("isCompleted") boolean isCompleted) {

  public static ReservedSubtodo of(String title) {
    return new ReservedSubtodo(title, false);
  }

  public ReservedSubtodo withTitle(String newTitle) {
    return new ReservedSubtodo(newTitle, isCompleted);
  }

  public ReservedSubtodo withCompleted(boolean completed) {
    return new ReservedSubtodo(title, completed);
  }
}
