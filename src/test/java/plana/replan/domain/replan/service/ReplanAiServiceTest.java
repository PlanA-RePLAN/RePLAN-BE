package plana.replan.domain.replan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import plana.replan.domain.replan.dto.RecommendInput;
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanOperation;

class ReplanAiServiceTest {

  private final ReplanAiService service = new ReplanAiService(null);

  @Test
  void 추천_JSON_파싱_성공() {
    String raw =
        """
        설명 텍스트 {"summary":"강의 4강","tipNote":"무리하지 마세요",
        "operations":[
          {"action":"MODIFY_TODO","targetTodoId":42,"title":"데이터 분석 1~2강",
           "dueDate":"2026-06-08","dueTime":"23:59","tagId":5,
           "routineType":null,"routineDays":null,
           "changedFields":[{"field":"title","before":"데이터 분석 공부","after":"데이터 분석 1~2강"}]},
          {"action":"ADD","targetTodoId":null,"title":"3~4강","dueDate":"2026-06-09",
           "dueTime":null,"tagId":null,"routineType":null,"routineDays":null,
           "changedFields":[{"field":"title","before":null,"after":"3~4강"}]}
        ]} 뒤 텍스트
        """;

    List<ReplanOperation> ops = service.parseRecommend(raw);

    assertThat(ops).hasSize(2);
    ReplanOperation first = ops.get(0);
    assertThat(first.action()).isEqualTo(ReplanAction.MODIFY_TODO);
    assertThat(first.targetTodoId()).isEqualTo(42L);
    assertThat(first.tagId()).isEqualTo(5L);
    // 수정(MODIFY)은 changedFields를 유지
    assertThat(first.changedFields()).hasSize(1);
    assertThat(first.changedFields().get(0).before()).isEqualTo("데이터 분석 공부");
    // 추가(ADD)는 changedFields를 비운다
    assertThat(ops.get(1).action()).isEqualTo(ReplanAction.ADD);
    assertThat(ops.get(1).targetTodoId()).isNull();
    assertThat(ops.get(1).changedFields()).isEmpty();
  }

  @Test
  void 추천_프롬프트에_입력이_포함된다() {
    RecommendInput input =
        new RecommendInput(
            7L,
            "데이터 분석 공부하기",
            "2026-06-07",
            "Study",
            false,
            null,
            List.of("목표 개선 필요", "우선 순위를 정하지 못했어요"),
            List.of(new RecommendInput.AnswerInput("free", "ADsP 4챕터", null, null)),
            "2026-06-18",
            0);

    String prompt = service.buildRecommendPrompt(input);

    assertThat(prompt).contains("데이터 분석 공부하기");
    assertThat(prompt).contains("우선 순위를 정하지 못했어요");
    assertThat(prompt).contains("ADsP 4챕터");
    assertThat(prompt).contains("2026-06-18");
    assertThat(prompt).contains("operations");
  }

  @Test
  void 추천_프롬프트에_대상_투두ID와_규칙이_포함된다() {
    RecommendInput input =
        new RecommendInput(
            42L,
            "영단어 100개 암기",
            "2026-06-07",
            null,
            false,
            null,
            List.of("목표 개선 필요"),
            List.of(),
            "2026-06-18",
            0);

    String prompt = service.buildRecommendPrompt(input);

    assertThat(prompt).contains("42");
    assertThat(prompt).contains("targetTodoId");
    assertThat(prompt).contains("'대상 투두 ID'");
  }

  @Test
  void 새로고침_0회차면_스타일_블록이_없다() {
    RecommendInput input =
        new RecommendInput(
            7L,
            "데이터 분석",
            "2026-06-07",
            null,
            false,
            null,
            List.of("목표 개선 필요"),
            List.of(),
            "2026-06-18",
            0);

    String prompt = service.buildRecommendPrompt(input);

    assertThat(prompt).doesNotContain("[이번 새로고침 스타일]");
  }

  @Test
  void 새로고침_2회차면_벼락치기_스타일_블록이_붙는다() {
    RecommendInput input =
        new RecommendInput(
            7L,
            "데이터 분석",
            "2026-06-07",
            null,
            false,
            null,
            List.of("목표 개선 필요"),
            List.of(),
            "2026-06-18",
            2);

    String prompt = service.buildRecommendPrompt(input);

    assertThat(prompt).contains("[이번 새로고침 스타일]");
    assertThat(prompt).contains("벼락치기");
    // 기존 공통 규칙/포맷은 여전히 포함
    assertThat(prompt).contains("operations");
  }

  @Test
  void 선택_투두_레이블이_있으면_프롬프트에_id_제목_형태로_포함된다() {
    RecommendInput input =
        new RecommendInput(
            10L,
            "데이터 분석",
            null,
            null,
            false,
            null,
            List.of("우선순위 미정"),
            List.of(
                new RecommendInput.AnswerInput(
                    "priority_targets",
                    null,
                    List.of(99L, 100L),
                    List.of("99:영단어 100개 암기", "100:독서 30분"))),
            "2026-06-18",
            0);

    String prompt = service.buildRecommendPrompt(input);

    assertThat(prompt).contains("99:영단어 100개 암기");
    assertThat(prompt).contains("100:독서 30분");
  }
}
