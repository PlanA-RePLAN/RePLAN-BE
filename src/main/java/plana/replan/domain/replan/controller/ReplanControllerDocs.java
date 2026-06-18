package plana.replan.domain.replan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanQuestionsRequest;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.global.common.ApiResult;

@Tag(name = "RePlan", description = "실패한 투두를 다시 계획하는 리플랜 API")
public interface ReplanControllerDocs {

  @Operation(summary = "추가 질문 조회",
      description = "실패 이유에 따라 AI가 추가로 물어볼 질문을 반환합니다. 없으면 빈 배열입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "404", description = "REPLAN_TODO_NOT_FOUND — 대상 투두를 찾을 수 없음"),
    @ApiResponse(responseCode = "502", description = "REPLAN_GEMINI_API_ERROR / REPLAN_GEMINI_PARSE_ERROR")
  })
  ResponseEntity<ApiResult<List<ReplanQuestion>>> getQuestions(
      Long userId, ReplanQuestionsRequest request);

  @Operation(summary = "추천 받기",
      description = "실패 이유와 추가질문 답변으로 투두 수정안·추가안을 제안합니다. 새로고침은 재호출하세요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "404", description = "REPLAN_TODO_NOT_FOUND"),
    @ApiResponse(responseCode = "502", description = "REPLAN_GEMINI_API_ERROR / REPLAN_GEMINI_PARSE_ERROR")
  })
  ResponseEntity<ApiResult<ReplanRecommendResponse>> recommend(
      Long userId, ReplanRecommendRequest request);

  @Operation(summary = "수락 저장",
      description = "사용자가 수락한 작업을 반영합니다. 작업이 비어도 실패 이유는 항상 저장됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "404", description = "REPLAN_TODO_NOT_FOUND / TAG_NOT_FOUND")
  })
  ResponseEntity<ApiResult<Void>> save(Long userId, ReplanSaveRequest request);
}
