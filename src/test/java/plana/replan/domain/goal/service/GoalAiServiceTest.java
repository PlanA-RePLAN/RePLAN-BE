package plana.replan.domain.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationRequest;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.global.exception.CustomException;

class GoalAiServiceTest {

  private final GoalAiService service = new GoalAiService(null);

  private TodoRecommendationRequest req(Integer refreshCount) {
    return new TodoRecommendationRequest(
        "토익 900점 달성", "2026-08-25", "08:00", "토익 600점", "평일 1시간", "해커스 보카", refreshCount);
  }

  @Test
  void 새로고침_횟수가_3을_넘으면_추천_실패() {
    assertThatThrownBy(() -> service.recommendTodos(req(4)))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(GoalErrorCode.INVALID_REFRESH_COUNT));
  }

  @Test
  void 새로고침_횟수가_음수면_추천_실패() {
    assertThatThrownBy(() -> service.recommendTodos(req(-1)))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(GoalErrorCode.INVALID_REFRESH_COUNT));
  }

  @Test
  void 새로고침_0회차면_스타일_블록이_없다() {
    String prompt = service.buildRecommendPrompt(req(0));
    assertThat(prompt).doesNotContain("[이번 새로고침 스타일]");
  }

  @Test
  void 새로고침_생략_null이면_0회차처럼_스타일_블록이_없다() {
    String prompt = service.buildRecommendPrompt(req(null));
    assertThat(prompt).doesNotContain("[이번 새로고침 스타일]");
  }

  @Test
  void 새로고침_2회차면_벼락치기_스타일_블록이_붙는다() {
    String prompt = service.buildRecommendPrompt(req(2));
    assertThat(prompt).contains("[이번 새로고침 스타일]");
    assertThat(prompt).contains("벼락치기");
    assertThat(prompt).contains("todos"); // 기존 JSON 포맷 규칙 유지
  }
}
