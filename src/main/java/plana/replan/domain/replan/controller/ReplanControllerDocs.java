package plana.replan.domain.replan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

  @Operation(summary = "추가 질문 조회", description = "실패 이유에 따라 AI가 추가로 물어볼 질문을 반환합니다. 없으면 빈 배열입니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": [
                                {
                                  "key": "priority_targets",
                                  "type": "TODO_SELECT",
                                  "title": "투두 선택",
                                  "chips": null
                                }
                              ],
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패 — 토큰 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "리플랜 대상 투두를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 404,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "REPLAN_TODO_NOT_FOUND",
                                "message": "리플랜 대상 투두를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "502",
        description = "AI 서비스 오류",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "Gemini API 오류",
                      value =
                          """
                          {
                            "status": 502,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_GEMINI_API_ERROR",
                              "message": "AI 추천 서비스에 일시적인 오류가 발생했습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "응답 파싱 오류",
                      value =
                          """
                          {
                            "status": 502,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_GEMINI_PARSE_ERROR",
                              "message": "AI 응답을 처리하는 중 오류가 발생했습니다.",
                              "detail": null
                            }
                          }
                          """)
                }))
  })
  ResponseEntity<ApiResult<List<ReplanQuestion>>> getQuestions(
      Long userId, ReplanQuestionsRequest request);

  @Operation(summary = "추천 받기", description = "실패 이유와 추가질문 답변으로 투두 수정안·추가안을 제안합니다. 새로고침은 재호출하세요.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "summary": "일정이 너무 촉박했습니다. 마감 기한을 늘리고 단계를 나눠보세요.",
                                "tipNote": "작은 단위로 쪼개면 성공률이 올라갑니다.",
                                "operations": []
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패 — 토큰 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "리플랜 대상 투두를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 404,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "REPLAN_TODO_NOT_FOUND",
                                "message": "리플랜 대상 투두를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "502",
        description = "AI 서비스 오류",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "Gemini API 오류",
                      value =
                          """
                          {
                            "status": 502,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_GEMINI_API_ERROR",
                              "message": "AI 추천 서비스에 일시적인 오류가 발생했습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "응답 파싱 오류",
                      value =
                          """
                          {
                            "status": 502,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_GEMINI_PARSE_ERROR",
                              "message": "AI 응답을 처리하는 중 오류가 발생했습니다.",
                              "detail": null
                            }
                          }
                          """)
                }))
  })
  ResponseEntity<ApiResult<ReplanRecommendResponse>> recommend(
      Long userId, ReplanRecommendRequest request);

  @Operation(summary = "수락 저장", description = "사용자가 수락한 작업을 반영합니다. 작업이 비어도 실패 이유는 항상 저장됩니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": null,
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패 — 토큰 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "수락한 작업의 형식이 올바르지 않음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 400,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "REPLAN_INVALID_OPERATION",
                                "message": "수락한 작업의 형식이 올바르지 않습니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "404",
        description = "리플랜 대상 투두 또는 태그를 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "투두를 찾을 수 없음",
                      value =
                          """
                          {
                            "status": 404,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_TODO_NOT_FOUND",
                              "message": "리플랜 대상 투두를 찾을 수 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "태그를 찾을 수 없음",
                      value =
                          """
                          {
                            "status": 404,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "TAG_NOT_FOUND",
                              "message": "태그를 찾을 수 없습니다.",
                              "detail": null
                            }
                          }
                          """)
                }))
  })
  ResponseEntity<ApiResult<Void>> save(Long userId, ReplanSaveRequest request);
}
