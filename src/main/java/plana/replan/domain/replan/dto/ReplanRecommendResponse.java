package plana.replan.domain.replan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description =
        "리플랜 추천 결과. needsMoreInfo=true이면 추가 질문(questions)과 앵커 투두 정보(anchorTodo)가 채워지고, "
            + "false이면 추천 작업(operations)과 선택 사유 라벨(reasonLabels)이 채워진다.")
public record ReplanRecommendResponse(
    @Schema(
            description = "추가 질문이 필요한지 여부. true면 questions를 보여주고 답변을 받아 다시 호출한다.",
            example = "false")
        boolean needsMoreInfo,
    @Schema(description = "질문 단계에서만 채워지는 앵커 투두의 기존 정보. 추천 단계면 생략(null)된다.")
        ReplanAnchorTodo anchorTodo,
    @Schema(description = "추가 질문 목록. needsMoreInfo=false면 빈 배열") List<ReplanQuestion> questions,
    @Schema(
            description =
                "사용자가 선택한 실패 이유의 한글 라벨(상위→하위 순). 추천 단계에서만 채워지고 질문 단계면 생략(null)된다.",
            example = "[\"예상치 못한 방해 발생\", \"돌발 상황이 발생했어요\"]")
        List<String> reasonLabels,
    @Schema(description = "추천 작업 목록. 질문 단계면 빈 배열") List<ReplanOperation> operations) {

  /** 추가 질문이 필요한 응답. 질문 화면에 보여줄 앵커 투두 정보를 함께 담고, reasonLabels는 내려보내지 않는다. */
  public static ReplanRecommendResponse askQuestions(
      List<ReplanQuestion> questions, ReplanAnchorTodo anchorTodo) {
    return new ReplanRecommendResponse(true, anchorTodo, questions, null, List.of());
  }

  /** 추천 결과 응답. 선택 사유 라벨을 함께 담고, 질문용 앵커 정보는 내려보내지 않는다. */
  public static ReplanRecommendResponse recommendation(
      List<ReplanOperation> operations, List<String> reasonLabels) {
    return new ReplanRecommendResponse(false, null, List.of(), reasonLabels, operations);
  }
}
