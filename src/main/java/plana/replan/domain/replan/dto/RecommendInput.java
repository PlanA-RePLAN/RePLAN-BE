package plana.replan.domain.replan.dto;

import java.util.List;

/** 추천 생성부의 순수 입력. 엔티티·userId·HTTP에 의존하지 않아 배치에서도 재사용 가능하다. */
public record RecommendInput(
    String anchorTitle,
    String anchorDueDate,
    String anchorTagName,
    boolean routine,
    String routineType,
    List<String> reasonLabels,
    List<AnswerInput> answers,
    String today) {

  public record AnswerInput(
      String key, String text, List<Long> selectedTodoIds, List<String> selectedChips) {}
}
