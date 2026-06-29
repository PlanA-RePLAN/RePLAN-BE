package plana.replan.domain.replan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import plana.replan.domain.replan.dto.QuestionType;
import plana.replan.domain.replan.dto.ReplanQuestion;

class ReplanQuestionRegistryTest {

  @Test
  void 우선순위_사유는_투두선택_질문을_요구한다() {
    List<ReplanQuestion> questions =
        ReplanQuestionRegistry.forReasonCodes(List.of("GOAL_NO_PRIORITY"));

    assertThat(questions).hasSize(1);
    assertThat(questions.get(0).key()).isEqualTo("priority_targets");
    assertThat(questions.get(0).type()).isEqualTo(QuestionType.TODO_SELECT);
  }

  @Test
  void 시작막막_사유는_칩_되묻기가_아니라_트리_3단계라_추가질문이_없다() {
    // CHIP 평탄화: 시작막막의 세부 사유(무엇부터/시간에너지/완벽)는 3단계 트리 선택으로 처리하므로
    // 칩 되묻기 질문이 사라졌다.
    assertThat(ReplanQuestionRegistry.forReasonCodes(List.of("MENTAL_HARD_TO_START"))).isEmpty();
    // 3단계 코드도 부모(시작막막)에 질문이 없으니 추가 질문 없이 바로 추천으로 간다.
    assertThat(ReplanQuestionRegistry.forReasonCodes(List.of("MENTAL_START_WHERE"))).isEmpty();
  }

  @Test
  void 질문이_필요없는_사유는_빈목록() {
    // GOAL_TOO_MUCH_VOLUME(마감 +1시간)은 추가 질문 없이 바로 추천
    assertThat(ReplanQuestionRegistry.forReasonCodes(List.of("GOAL_TOO_MUCH_VOLUME"))).isEmpty();
  }

  @Test
  void 매핑에_없는_코드는_무시() {
    assertThat(ReplanQuestionRegistry.forReasonCodes(List.of("어떤_자유입력_텍스트"))).isEmpty();
  }

  @Test
  void 여러_사유의_질문을_합치되_같은_key는_중복제거() {
    List<ReplanQuestion> questions =
        ReplanQuestionRegistry.forReasonCodes(
            List.of("GOAL_NO_PRIORITY", "GOAL_NO_PRIORITY", "CONDITION_PAIN"));

    assertThat(questions).hasSize(2);
    assertThat(questions)
        .extracting(ReplanQuestion::key)
        .containsExactly("priority_targets", "pain_area");
  }

  @Test
  void null이면_빈목록() {
    assertThat(ReplanQuestionRegistry.forReasonCodes(null)).isEmpty();
  }

  @Test
  void 수면_하위코드는_부모_질문을_상속한다() {
    List<ReplanQuestion> questions =
        ReplanQuestionRegistry.forReasonCodes(List.of("CONDITION_SLEEP_3H_UNDER"));

    assertThat(questions).hasSize(1);
    assertThat(questions.get(0).key()).isEqualTo("reschedule_date");
  }
}
