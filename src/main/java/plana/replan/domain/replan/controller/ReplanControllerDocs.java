package plana.replan.domain.replan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.global.common.ApiResult;

@Tag(name = "RePlan", description = "실패한 투두를 다시 계획하는 리플랜 API")
public interface ReplanControllerDocs {

  @Operation(
      summary = "추천 받기 (질문 또는 추천)",
      description =
          "2단계로 선택한 실패 이유(reasonCodes)를 보냅니다. 서버가 그 이유에 추가 질문이 필요한지 결정합니다.\n"
              + "- 추가 질문이 필요하면 needsMoreInfo=true와 questions, 그리고 \"기존 투두 수정 사항\" 카드용 anchorTodo(앵커 투두의 기존 정보)가 옵니다. 사용자가 답한 뒤 answers를 채워 다시 호출하세요.\n"
              + "- 질문이 필요 없거나 answers가 있으면 needsMoreInfo=false와 추천(reasonLabels, operations)이 옵니다.\n"
              + "- 새로고침: 같은 요청 몸체에 refreshCount만 1~3으로 올려 다시 호출하면 회차별 다른 스타일의 추천이 옵니다(최대 3회).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "성공 (추가 질문 또는 추천)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "추가 질문이 필요한 경우",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "needsMoreInfo": true,
                              "anchorTodo": {
                                "todoId": 42,
                                "title": "데이터 분석 공부하기",
                                "dueDate": "2026-06-25T11:00:00",
                                "tagId": 3,
                                "tagTitle": "Study",
                                "tagColor": "#FAD7A0",
                                "routineType": null
                              },
                              "questions": [
                                {
                                  "key": "priority_targets",
                                  "type": "TODO_SELECT",
                                  "title": "우선순위를 매길 투두를 선택하세요",
                                  "chips": null
                                }
                              ],
                              "operations": []
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "추천이 바로 나온 경우",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "needsMoreInfo": false,
                              "questions": [],
                              "reasonLabels": ["예상치 못한 방해 발생", "돌발 상황이 발생했어요"],
                              "operations": [
                                {
                                  "action": "MODIFY_TODO",
                                  "targetTodoId": 42,
                                  "title": "데이터 분석 공부하기",
                                  "dueDate": "2026-06-26",
                                  "dueTime": "23:59",
                                  "tagId": null,
                                  "routineType": null,
                                  "routineDate": null,
                                  "changedFields": [
                                    {"field": "dueDate", "before": "2026-06-25", "after": "2026-06-26"}
                                  ]
                                }
                              ]
                            },
                            "error": null
                          }
                          """)
                })),
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
        description = "실패 이유 개수 또는 새로고침 횟수가 올바르지 않음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "실패 이유 개수 오류",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_INVALID_REASON",
                              "message": "실패 이유는 최소 1개, 최대 3개여야 합니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "새로고침 횟수 오류",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_INVALID_REFRESH_COUNT",
                              "message": "새로고침 횟수는 0 이상 3 이하여야 합니다.",
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
        description = "요청 형식 오류 — 실패 이유 개수 또는 작업 형식",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "실패 이유 개수 오류",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "REPLAN_INVALID_REASON",
                              "message": "실패 이유는 최소 1개, 최대 3개여야 합니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "작업 형식 오류",
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
                          """)
                })),
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
