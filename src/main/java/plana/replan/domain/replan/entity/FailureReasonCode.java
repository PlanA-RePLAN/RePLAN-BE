package plana.replan.domain.replan.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FailureReasonCode {

  // depth 1
  MENTAL_RESISTANCE("심리적 저항", null),
  BAD_CONDITION("컨디션 난조", null),
  GOAL_NEEDS_IMPROVEMENT("목표/계획 개선 필요", null),
  UNEXPECTED_INTERRUPTION("예상치 못한 방해 발생", null),

  // depth 2 — 심리적 저항
  MENTAL_HARD_TO_START("시작이 어려웠어요", MENTAL_RESISTANCE),
  MENTAL_NO_MOTIVATION("동기가 부족했어요", MENTAL_RESISTANCE),
  MENTAL_PROCRASTINATION("어렵거나 오래걸릴 것 같아 미뤘어요", MENTAL_RESISTANCE),
  MENTAL_PERFECTIONISM("잘하고 싶어 부담을 가지다 미뤘어요", MENTAL_RESISTANCE),

  // depth 2 — 컨디션 난조
  CONDITION_EXHAUSTED("체력 방전/에너지 모두 소모 상태에요", BAD_CONDITION),
  CONDITION_SLEEP("수면부족/피로 누적 상태에요", BAD_CONDITION),
  CONDITION_PAIN("신체적 통증이 있어요", BAD_CONDITION),
  CONDITION_BURNOUT("번아웃이 왔어요", BAD_CONDITION),

  // depth 2 — 목표/계획 개선 필요
  GOAL_NO_PLAN("구체적 계획 수립을 실패했어요", GOAL_NEEDS_IMPROVEMENT),
  GOAL_TOO_MUCH("목표가 과했어요", GOAL_NEEDS_IMPROVEMENT),
  GOAL_NO_PRIORITY("우선 순위를 정하지 못했어요", GOAL_NEEDS_IMPROVEMENT),
  GOAL_UNDERESTIMATED("시간이 예측보다 더 소요됐어요", GOAL_NEEDS_IMPROVEMENT),

  // depth 2 — 예상치 못한 방해 발생
  INTERRUPT_SUDDEN("돌발 상황이 발생했어요", UNEXPECTED_INTERRUPTION),
  INTERRUPT_ENVIRONMENT("집중할 수 있는 환경이 아니었어요", UNEXPECTED_INTERRUPTION),
  INTERRUPT_URGENT("더 급한 일이 생겼어요", UNEXPECTED_INTERRUPTION),
  INTERRUPT_LATE_END("다른 일정이 늦게 끝났어요", UNEXPECTED_INTERRUPTION),

  // depth 3 — 수면부족 세분화
  CONDITION_SLEEP_3H_UNDER("3시간 이하", CONDITION_SLEEP),
  CONDITION_SLEEP_4_5H("4~5시간", CONDITION_SLEEP),
  CONDITION_SLEEP_6_7H("6~7시간", CONDITION_SLEEP),
  CONDITION_SLEEP_8H_OVER("8시간 이상", CONDITION_SLEEP);

  private final String label;
  private final FailureReasonCode parent;

  public boolean isPreset() {
    return true;
  }
}
