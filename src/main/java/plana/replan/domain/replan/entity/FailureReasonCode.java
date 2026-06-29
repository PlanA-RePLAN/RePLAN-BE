package plana.replan.domain.replan.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 리플랜 실패 사유 트리. 피그마(고해상도리플랜) 디자인의 1/2/3단계 선택 구조를 그대로 코드로 옮긴 것이다. 같은 개념이 이어지는 사유는 enum 이름을 보존하고
 * 라벨/부모만 바꿔, 과거 리플랜 데이터의 통계 라벨 해석이 깨지지 않게 한다.
 */
@Getter
@RequiredArgsConstructor
public enum FailureReasonCode {

  // ── depth 1 (대분류) ──
  MENTAL_RESISTANCE("심리적 저항", null),
  BAD_CONDITION("컨디션 난조", null),
  GOAL_NEEDS_IMPROVEMENT("목표 개선 필요", null),
  UNEXPECTED_INTERRUPTION("예상치 못한 방해 발생", null),

  // ── 심리적 저항 ──
  MENTAL_HARD_TO_START("시작하기 막막하거나 부담스러웠어요", MENTAL_RESISTANCE),
  // 시작막막 3단계 (기존 CHIP 칩 3개를 정식 사유 코드로 승격)
  MENTAL_START_WHERE("무엇부터 시작할지 몰라서", MENTAL_HARD_TO_START),
  MENTAL_START_HEAVY("시간이나 에너지가 많이 들 것 같아서", MENTAL_HARD_TO_START),
  MENTAL_PERFECTIONISM("완벽하게 해내고 싶어서", MENTAL_HARD_TO_START),
  MENTAL_NO_MOTIVATION("의욕·동기가 부족했어요", MENTAL_RESISTANCE),
  MENTAL_PROCRASTINATION("당장 안 해도 되서 미루다 미뤘어요", MENTAL_RESISTANCE),
  MENTAL_DISTRACTION("딴짓을 하다가 미뤘어요", MENTAL_RESISTANCE),

  // ── 컨디션 난조 ──
  CONDITION_EXHAUSTED("체력 방전/에너지 모두 소모 상태에요", BAD_CONDITION),
  CONDITION_SLEEP("수면부족/피로 누적 상태에요", BAD_CONDITION),
  // 수면 3단계 (수면 시간대)
  CONDITION_SLEEP_3H_UNDER("3시간 이하", CONDITION_SLEEP),
  CONDITION_SLEEP_4_5H("4~5시간", CONDITION_SLEEP),
  CONDITION_SLEEP_6_7H("6~7시간", CONDITION_SLEEP),
  CONDITION_SLEEP_8H_OVER("8시간 이상", CONDITION_SLEEP),
  CONDITION_PAIN("신체적 통증이 있어요", BAD_CONDITION),
  CONDITION_BURNOUT("번아웃이 왔어요", BAD_CONDITION),
  // 번아웃 3단계
  CONDITION_BURNOUT_NO_PROGRESS("성과나 변화가 보이지 않아 무기력해요", CONDITION_BURNOUT),
  CONDITION_BURNOUT_LOST_DIRECTION("목표의 방향성을 잃었어요", CONDITION_BURNOUT),

  // ── 목표 개선 필요 ──
  GOAL_NO_PLAN("구체적 계획 수립을 실패했어요", GOAL_NEEDS_IMPROVEMENT),
  GOAL_TOO_MUCH("목표가 과했어요", GOAL_NEEDS_IMPROVEMENT),
  // 목표가 과했어요 3단계
  GOAL_TOO_MANY_TODOS("하루에 계획한 할 일 개수가 많았어요", GOAL_TOO_MUCH),
  GOAL_TOO_MUCH_VOLUME("특정 할 일의 분량이 많았어요", GOAL_TOO_MUCH),
  GOAL_NO_PRIORITY("우선 순위를 정하지 못했어요", GOAL_NEEDS_IMPROVEMENT),
  GOAL_UNDERESTIMATED("시간이 예측보다 더 소요됐어요", GOAL_NEEDS_IMPROVEMENT),

  // ── 예상치 못한 방해 발생 ──
  INTERRUPT_SUDDEN("돌발 상황이 발생했어요", UNEXPECTED_INTERRUPTION),
  INTERRUPT_ENVIRONMENT("집중할 수 있는 환경이 아니었어요", UNEXPECTED_INTERRUPTION),
  // 집중 환경 3단계
  INTERRUPT_ENV_NOISE("시끄럽거나 작업할 공간이 마땅치 않았어요", INTERRUPT_ENVIRONMENT),
  INTERRUPT_CONTACT("타인의 요청이나 연락이 계속 들어왔어요", INTERRUPT_ENVIRONMENT),
  INTERRUPT_URGENT("더 급한 일이 생겼어요", UNEXPECTED_INTERRUPTION),
  INTERRUPT_LATE_END("다른 일정이 늦게 끝났어요", UNEXPECTED_INTERRUPTION),

  // ── 과거 호환용 ──
  // 피그마 트리에서 빠진 사유(새로 선택 불가). 과거 리플랜 데이터의 라벨 해석이 깨지지 않도록 enum에는 남겨둔다.
  @Deprecated
  GOAL_UNFAMILIAR("처음 해보거나 익숙하지 않아 예측이 빗나갔어요", GOAL_NEEDS_IMPROVEMENT),
  @Deprecated
  GOAL_BLOCKED("중간에 예상치 못한 문제나 막히는 부분이 생겼어요", GOAL_NEEDS_IMPROVEMENT);

  private final String label;
  private final FailureReasonCode parent;

  public boolean isPreset() {
    return true;
  }
}
