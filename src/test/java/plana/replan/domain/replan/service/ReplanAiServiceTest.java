package plana.replan.domain.replan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import plana.replan.domain.replan.dto.QuestionType;
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;

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
           "routineType":null,"routineDate":null,
           "changedFields":[{"field":"title","before":"데이터 분석 공부","after":"데이터 분석 1~2강"}]},
          {"action":"ADD","targetTodoId":null,"title":"3~4강","dueDate":"2026-06-09",
           "dueTime":null,"tagId":null,"routineType":null,"routineDate":null,
           "changedFields":[{"field":"title","before":null,"after":"3~4강"}]}
        ]} 뒤 텍스트
        """;

    ReplanRecommendResponse res = service.parseRecommend(raw);

    assertThat(res.summary()).isEqualTo("강의 4강");
    assertThat(res.tipNote()).isEqualTo("무리하지 마세요");
    assertThat(res.operations()).hasSize(2);
    ReplanOperation first = res.operations().get(0);
    assertThat(first.action()).isEqualTo(ReplanAction.MODIFY_TODO);
    assertThat(first.targetTodoId()).isEqualTo(42L);
    assertThat(first.tagId()).isEqualTo(5L);
    assertThat(first.changedFields()).hasSize(1);
    assertThat(first.changedFields().get(0).before()).isEqualTo("데이터 분석 공부");
    assertThat(res.operations().get(1).action()).isEqualTo(ReplanAction.ADD);
    assertThat(res.operations().get(1).targetTodoId()).isNull();
  }

  @Test
  void 질문_JSON_파싱_성공() {
    String raw =
        """
        {"questions":[
          {"key":"priority_targets","type":"TODO_SELECT","title":"투두 선택","chips":null},
          {"key":"hint","type":"CHIP","title":"참고","chips":["마감기한순","과제 1순위"]}
        ]}
        """;

    List<ReplanQuestion> questions = service.parseQuestions(raw);

    assertThat(questions).hasSize(2);
    assertThat(questions.get(0).type()).isEqualTo(QuestionType.TODO_SELECT);
    assertThat(questions.get(1).type()).isEqualTo(QuestionType.CHIP);
    assertThat(questions.get(1).chips()).containsExactly("마감기한순", "과제 1순위");
  }

  @Test
  void 빈_질문_파싱() {
    List<ReplanQuestion> questions = service.parseQuestions("{\"questions\":[]}");
    assertThat(questions).isEmpty();
  }
}
