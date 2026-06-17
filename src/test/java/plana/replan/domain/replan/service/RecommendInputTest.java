package plana.replan.domain.replan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** RecommendInput은 순수 데이터 record — 생성과 필드 접근만 검증한다. */
class RecommendInputTest {

  @Test
  void 필드를_올바르게_저장한다() {
    RecommendInput.AnswerInput answer =
        new RecommendInput.AnswerInput("free", "답변 내용", null, null);
    RecommendInput input =
        new RecommendInput(
            "토익 900점",
            "2026-06-30",
            "Study",
            false,
            null,
            List.of("시간 부족"),
            List.of(answer),
            "2026-06-18");

    assertThat(input.anchorTitle()).isEqualTo("토익 900점");
    assertThat(input.routine()).isFalse();
    assertThat(input.answers()).hasSize(1);
    assertThat(input.answers().get(0).key()).isEqualTo("free");
  }
}
