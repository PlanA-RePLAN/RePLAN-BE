package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "리플랜 추천 결과. needsMoreInfo=true이면 추가 질문(questions)이 채워지고, false이면 추천 작업(operations)이 채워진다.")
public record ReplanRecommendResponse(
    @Schema(
            description = "추가 질문이 필요한지 여부. true면 questions를 보여주고 답변을 받아 다시 호출한다.",
            example = "false")
        boolean needsMoreInfo,
    @Schema(description = "추가 질문 목록. needsMoreInfo=false면 빈 배열") List<ReplanQuestion> questions,
    @Schema(
            description = "사용자가 선택한 실패 이유의 한글 라벨(상위→하위 순). 프론트가 추천 화면 상단에 그대로 표시.",
            example = "[\"목표/계획 개선 필요\", \"구체적 계획 수립을 실패했어요\"]")
        List<String> reasonLabels,
    @Schema(description = "추천 작업 목록. 질문 단계면 빈 배열") List<ReplanOperation> operations) {

  /** 추가 질문이 필요한 응답. */
  public static ReplanRecommendResponse askQuestions(
      List<ReplanQuestion> questions, List<String> reasonLabels) {
    return new ReplanRecommendResponse(true, questions, reasonLabels, List.of());
  }

  /** 추천 결과 응답. */
  public static ReplanRecommendResponse recommendation(
      List<ReplanOperation> operations, List<String> reasonLabels) {
    return new ReplanRecommendResponse(false, List.of(), reasonLabels, operations);
  }
}
