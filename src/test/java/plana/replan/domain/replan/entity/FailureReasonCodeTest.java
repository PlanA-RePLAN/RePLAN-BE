package plana.replan.domain.replan.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureReasonCodeTest {

  @Test
  void 시작막막_세부사유는_시작막막의_3단계다_CHIP_평탄화() {
    // 기존 칩 3개가 트리 3단계 정식 코드로 승격됐다.
    assertThat(FailureReasonCode.MENTAL_START_WHERE.getParent())
        .isEqualTo(FailureReasonCode.MENTAL_HARD_TO_START);
    assertThat(FailureReasonCode.MENTAL_START_HEAVY.getParent())
        .isEqualTo(FailureReasonCode.MENTAL_HARD_TO_START);
    assertThat(FailureReasonCode.MENTAL_PERFECTIONISM.getParent())
        .isEqualTo(FailureReasonCode.MENTAL_HARD_TO_START);
  }

  @Test
  void 번아웃_세부사유는_번아웃의_3단계다() {
    assertThat(FailureReasonCode.CONDITION_BURNOUT_NO_PROGRESS.getParent())
        .isEqualTo(FailureReasonCode.CONDITION_BURNOUT);
    assertThat(FailureReasonCode.CONDITION_BURNOUT_LOST_DIRECTION.getParent())
        .isEqualTo(FailureReasonCode.CONDITION_BURNOUT);
  }

  @Test
  void 할일개수_분량은_목표가과했어요의_3단계로_이동했다() {
    assertThat(FailureReasonCode.GOAL_TOO_MANY_TODOS.getParent())
        .isEqualTo(FailureReasonCode.GOAL_TOO_MUCH);
    assertThat(FailureReasonCode.GOAL_TOO_MUCH_VOLUME.getParent())
        .isEqualTo(FailureReasonCode.GOAL_TOO_MUCH);
  }

  @Test
  void 타인연락은_집중환경의_3단계로_이동했다() {
    assertThat(FailureReasonCode.INTERRUPT_CONTACT.getParent())
        .isEqualTo(FailureReasonCode.INTERRUPT_ENVIRONMENT);
    assertThat(FailureReasonCode.INTERRUPT_ENV_NOISE.getParent())
        .isEqualTo(FailureReasonCode.INTERRUPT_ENVIRONMENT);
  }

  @Test
  void 대분류_라벨은_피그마_문구다() {
    assertThat(FailureReasonCode.GOAL_NEEDS_IMPROVEMENT.getLabel()).isEqualTo("목표 개선 필요");
    assertThat(FailureReasonCode.GOAL_NEEDS_IMPROVEMENT.getParent()).isNull();
  }

  @Test
  void 깊은_사유도_부모를_거슬러_올라가면_대분류에_닿는다() {
    FailureReasonCode fc = FailureReasonCode.MENTAL_PERFECTIONISM;
    while (fc.getParent() != null) {
      fc = fc.getParent();
    }
    assertThat(fc).isEqualTo(FailureReasonCode.MENTAL_RESISTANCE);
  }

  @Test
  void 과거호환_코드는_여전히_해석된다() {
    // 피그마 트리에서 빠졌지만 과거 데이터 라벨 해석을 위해 enum에 남아 있어야 한다.
    assertThat(FailureReasonCode.valueOf("GOAL_UNFAMILIAR").getLabel())
        .isEqualTo("처음 해보거나 익숙하지 않아 예측이 빗나갔어요");
    assertThat(FailureReasonCode.valueOf("GOAL_BLOCKED").getParent())
        .isEqualTo(FailureReasonCode.GOAL_NEEDS_IMPROVEMENT);
  }
}
