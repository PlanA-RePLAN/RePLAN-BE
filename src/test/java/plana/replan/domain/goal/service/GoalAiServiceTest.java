package plana.replan.domain.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.goal.dto.explore.GoalExploreRequest;
import plana.replan.domain.goal.dto.explore.GoalExploreResponse;
import plana.replan.domain.goal.dto.recommend.SolutionInput;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationRequest;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationResponse;
import plana.replan.domain.goal.dto.refine.GoalRefinementRequest;
import plana.replan.domain.goal.dto.refine.GoalRefinementResponse;
import plana.replan.domain.goal.dto.refine.QuestionAnswer;
import plana.replan.domain.goal.dto.refine.RefinedNoteItem;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.global.exception.CustomException;

class GoalAiServiceTest {

  private final GoalAiService service = new GoalAiService(null, null);

  private Tag tag(long id, String title) {
    User user =
        User.builder()
            .email("a@a.com")
            .nickname("n")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();
    Tag t = Tag.builder().title(title).user(user).build();
    ReflectionTestUtils.setField(t, "id", id);
    return t;
  }

  private TodoRecommendationRequest req(Integer refreshCount) {
    return new TodoRecommendationRequest(
        "토익 900점 달성",
        "2026-08-25",
        "08:00",
        List.of(new SolutionInput("현재 수준", List.of(new RefinedNoteItem("실력", "토익 600점")))),
        refreshCount);
  }

  @Test
  void 탐색_프롬프트에_목표와_종료일정이_들어간다() {
    GoalExploreRequest req = new GoalExploreRequest("토익 850점 이상 달성", "2026-05-01", "23:59");
    String prompt = service.buildExplorePrompt(req, "2026-06-20");
    assertThat(prompt).contains("토익 850점 이상 달성");
    assertThat(prompt).contains("2026-05-01");
    assertThat(prompt).contains("23:59");
    assertThat(prompt).contains("2026-06-20");
  }

  @Test
  void 탐색_종료일정이_없으면_미입력으로_들어간다() {
    GoalExploreRequest req = new GoalExploreRequest("토익 850점", null, null);
    String prompt = service.buildExplorePrompt(req, "2026-06-20");
    assertThat(prompt).containsPattern("미입력[\\s\\S]*미입력");
  }

  @Test
  void 탐색_유효한_응답을_파싱한다() {
    String raw =
        "{\"valid\":true,\"message\":null,\"questions\":"
            + "[{\"question\":\"현재 영어 실력\",\"chips\":[\"토익 600점대\",\"RC 파트 취약\"]}]}";
    GoalExploreResponse res = service.parseExploreResponse(raw);
    assertThat(res.valid()).isTrue();
    assertThat(res.message()).isNull();
    assertThat(res.questions()).hasSize(1);
    assertThat(res.questions().get(0).question()).isEqualTo("현재 영어 실력");
    assertThat(res.questions().get(0).chips()).containsExactly("토익 600점대", "RC 파트 취약");
  }

  @Test
  void 탐색_목표가_아니면_valid_false와_안내메시지를_파싱한다() {
    String raw = "{\"valid\":false,\"message\":\"달성할 수 있는 목표를 입력해주세요.\",\"questions\":[]}";
    GoalExploreResponse res = service.parseExploreResponse(raw);
    assertThat(res.valid()).isFalse();
    assertThat(res.message()).isEqualTo("달성할 수 있는 목표를 입력해주세요.");
    assertThat(res.questions()).isEmpty();
  }

  @Test
  void 새로고침_횟수가_3을_넘으면_추천_실패() {
    assertThatThrownBy(() -> service.recommendTodos(1L, req(4)))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(GoalErrorCode.INVALID_REFRESH_COUNT));
  }

  @Test
  void 새로고침_횟수가_음수면_추천_실패() {
    assertThatThrownBy(() -> service.recommendTodos(1L, req(-1)))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(GoalErrorCode.INVALID_REFRESH_COUNT));
  }

  @Test
  void 새로고침_0회차면_스타일_블록이_없다() {
    String prompt = service.buildRecommendPrompt(req(0), "없음");
    assertThat(prompt).doesNotContain("[이번 새로고침 스타일]");
  }

  @Test
  void 새로고침_생략_null이면_0회차처럼_스타일_블록이_없다() {
    String prompt = service.buildRecommendPrompt(req(null), "없음");
    assertThat(prompt).doesNotContain("[이번 새로고침 스타일]");
  }

  @Test
  void 새로고침_2회차면_벼락치기_스타일_블록이_붙는다() {
    String prompt = service.buildRecommendPrompt(req(2), "없음");
    assertThat(prompt).contains("[이번 새로고침 스타일]");
    assertThat(prompt).contains("벼락치기");
    assertThat(prompt).contains("todos"); // 기존 JSON 포맷 규칙 유지
  }

  @Test
  void 추천_프롬프트에_솔루션이_들어간다() {
    String prompt = service.buildRecommendPrompt(req(0), "없음");
    assertThat(prompt).contains("[현재 수준]");
    assertThat(prompt).contains("실력: 토익 600점");
  }

  @Test
  void 추천_프롬프트에_태그_목록이_들어간다() {
    String prompt =
        service.buildRecommendPrompt(req(0), "- id=1, name=Study\n- id=2, name=Health\n");
    assertThat(prompt).contains("[사용 가능한 태그 목록]");
    assertThat(prompt).contains("id=1, name=Study");
    assertThat(prompt).contains("id=2, name=Health");
    assertThat(prompt).contains("tagId"); // 출력 JSON 스키마에 tagId 포함
  }

  @Test
  void 태그정보_문자열_생성_태그가_없으면_없음() {
    assertThat(service.buildTagInfo(List.of())).isEqualTo("없음");
  }

  @Test
  void 태그정보_문자열_생성_id와_이름이_들어간다() {
    String info = service.buildTagInfo(List.of(tag(1L, "Study"), tag(2L, "Health")));
    assertThat(info).contains("id=1, name=Study");
    assertThat(info).contains("id=2, name=Health");
  }

  @Test
  void 추천파싱_AI가_준_tagId가_유저태그면_tagId와_이름을_채운다() {
    String raw =
        "{\"overallReason\":\"r\",\"todos\":[{\"type\":\"ONE_TIME\",\"title\":\"단어 암기\","
            + "\"dueDate\":null,\"dueTime\":null,\"routineType\":null,\"routineDate\":null,\"tagId\":1}]}";
    TodoRecommendationResponse res =
        service.parseRecommendResponse(raw, List.of(tag(1L, "Study"), tag(2L, "Health")));
    assertThat(res.todos()).hasSize(1);
    assertThat(res.todos().get(0).tagId()).isEqualTo(1L);
    assertThat(res.todos().get(0).tagName()).isEqualTo("Study");
  }

  @Test
  void 추천파싱_tagId가_문자열_숫자여도_유저태그면_채운다() {
    String raw =
        "{\"overallReason\":\"r\",\"todos\":[{\"type\":\"ONE_TIME\",\"title\":\"단어 암기\","
            + "\"dueDate\":null,\"dueTime\":null,\"routineType\":null,\"routineDate\":null,\"tagId\":\"1\"}]}";
    TodoRecommendationResponse res = service.parseRecommendResponse(raw, List.of(tag(1L, "Study")));
    assertThat(res.todos().get(0).tagId()).isEqualTo(1L);
    assertThat(res.todos().get(0).tagName()).isEqualTo("Study");
  }

  @Test
  void 추천파싱_AI가_지어낸_tagId면_태그없음으로_처리한다() {
    String raw =
        "{\"overallReason\":\"r\",\"todos\":[{\"type\":\"ONE_TIME\",\"title\":\"단어 암기\","
            + "\"dueDate\":null,\"dueTime\":null,\"routineType\":null,\"routineDate\":null,\"tagId\":999}]}";
    TodoRecommendationResponse res = service.parseRecommendResponse(raw, List.of(tag(1L, "Study")));
    assertThat(res.todos().get(0).tagId()).isNull();
    assertThat(res.todos().get(0).tagName()).isNull();
  }

  @Test
  void 추천파싱_tagId가_null이면_태그없음() {
    String raw =
        "{\"overallReason\":\"r\",\"todos\":[{\"type\":\"ONE_TIME\",\"title\":\"단어 암기\","
            + "\"dueDate\":null,\"dueTime\":null,\"routineType\":null,\"routineDate\":null,\"tagId\":null}]}";
    TodoRecommendationResponse res = service.parseRecommendResponse(raw, List.of(tag(1L, "Study")));
    assertThat(res.todos().get(0).tagId()).isNull();
    assertThat(res.todos().get(0).tagName()).isNull();
  }

  private GoalRefinementRequest refineReq() {
    return new GoalRefinementRequest(
        "토익 850점", "2026-05-01", "23:59", List.of(new QuestionAnswer("현재 영어 실력", "토익 600점대")));
  }

  @Test
  void 정제_프롬프트에_목표와_질문답변이_들어간다() {
    String prompt = service.buildRefinePrompt(refineReq(), "2026-06-20");
    assertThat(prompt).contains("토익 850점");
    assertThat(prompt).contains("현재 영어 실력: 토익 600점대");
  }

  @Test
  void 정제_답변이_비면_미입력으로_들어간다() {
    GoalRefinementRequest req =
        new GoalRefinementRequest("토익 850점", null, null, List.of(new QuestionAnswer("특이사항", "")));
    String prompt = service.buildRefinePrompt(req, "2026-06-20");
    assertThat(prompt).contains("특이사항: 미입력");
  }

  @Test
  void 정제_응답을_질문별_솔루션으로_파싱한다() {
    String raw =
        "{\"goal\":{\"value\":\"토익 850점 달성\",\"reason\":\"구체화함\"},"
            + "\"deadline\":{\"date\":\"2026-05-01\",\"time\":\"23:59\",\"reason\":\"유지\"},"
            + "\"solutions\":[{\"question\":\"현재 수준\",\"items\":"
            + "[{\"title\":\"독해\",\"content\":\"실전풀이 필요\"}],\"reason\":\"격차 정리\"}]}";
    GoalRefinementResponse res = service.parseRefineResponse(raw);
    assertThat(res.goal().value()).isEqualTo("토익 850점 달성");
    assertThat(res.deadline().date()).isEqualTo("2026-05-01");
    assertThat(res.solutions()).hasSize(1);
    assertThat(res.solutions().get(0).question()).isEqualTo("현재 수준");
    assertThat(res.solutions().get(0).items().get(0).title()).isEqualTo("독해");
  }
}
