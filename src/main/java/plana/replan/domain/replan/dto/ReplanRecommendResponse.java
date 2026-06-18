package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "리플랜 추천 결과. needsMoreInfo=true이면 추가 질문(questions)이 채워지고, "
            + "false이면 추천 결과(summary/tipNote/operations)가 채워진다.")
public record ReplanRecommendResponse(
    @Schema(
            description = "추가 질문이 필요한지 여부. true면 questions를 보여주고 답변을 받아 다시 호출한다.",
            example = "false")
        boolean needsMoreInfo,
    @Schema(description = "추가 질문 목록. needsMoreInfo=false면 빈 배열") List<ReplanQuestion> questions,
    @Schema(description = "AI가 입력을 정리한 요약('이렇게 정리했어요'). 질문 단계면 null", nullable = true)
        String summary,
    @Schema(description = "줄글 가이드(멘탈케어/조율 방향). 질문 단계면 null", nullable = true) String tipNote,
    @Schema(description = "추천 작업 목록. 질문 단계면 빈 배열") List<ReplanOperation> operations) {

  /** 추가 질문이 필요한 응답. */
  public static ReplanRecommendResponse askQuestions(List<ReplanQuestion> questions) {
    return new ReplanRecommendResponse(true, questions, null, null, List.of());
  }

  /** 추천 결과 응답. */
  public static ReplanRecommendResponse recommendation(
      String summary, String tipNote, List<ReplanOperation> operations) {
    return new ReplanRecommendResponse(false, List.of(), summary, tipNote, operations);
  }
}
