package plana.replan.domain.replan.dto;

import java.util.List;

/** 추천 생성부의 순수 입력. 엔티티·userId·HTTP에 의존하지 않아 배치에서도 재사용 가능하다. */
public record RecommendInput(
    Long anchorTodoId,
    String anchorTitle,
    String anchorDueDate,
    String anchorTagName,
    boolean routine,
    String routineType,
    List<String> reasonLabels,
    List<AnswerInput> answers,
    // 현재 일시 문자열(Asia/Seoul, ReplanAiService.PROMPT_NOW_FORMAT). 프롬프트에 그대로 들어간다.
    String now,
    int refreshCount,
    // 유저가 가진 태그 목록(id+이름). AI가 신규 투두에 배정할 태그를 이 안에서 고르게 하고,
    // 응답 파싱 시 AI가 준 tagId가 실제 내 태그인지 검증하는 데 쓴다.
    List<TagOption> tags) {

  public record AnswerInput(
      String key, String text, List<Long> selectedTodoIds, List<String> selectedTodoLabels) {}

  /** 프롬프트/검증에 쓰는 태그 한 건(엔티티 대신 값만). */
  public record TagOption(Long id, String name) {}
}
