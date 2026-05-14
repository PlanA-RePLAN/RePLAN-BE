package plana.replan.domain.goal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.goal.dto.GoalCreateRequestDto;
import plana.replan.domain.goal.dto.GoalSingleResponseDto;
import plana.replan.domain.goal.dto.GoalsByDateResponseDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "Goal", description = "목표(Goal) 관련 API. 모든 요청에 JWT 인증 필수.")
public interface GoalControllerDocs {

  @Operation(
      summary = "목표 생성",
      description =
          """
          새로운 목표를 생성합니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | String | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | String | `application/json` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | String | 목표 제목. 공백만 있으면 유효성 오류 | `"토익 900점 달성"` |
          | dueDate | ❌ 선택 | LocalDateTime | 목표 기한. ISO 8601 형식 | `"2025-12-31T00:00:00"` |
          | reference | ❌ 선택 | String | 참고 자료 (URL 또는 자유 텍스트) | `"https://toeic.ets.org"` |

          ---

          ### 주의사항
          - `title`이 없거나 공백이면 400 반환
          - `dueDate`를 전달하지 않으면 null로 저장됨
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "목표 생성 성공",
        content =
            @Content(
                schema = @Schema(implementation = GoalSingleResponseDto.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "id": 42,
                                "title": "토익 900점 달성",
                                "dueDate": "2025-12-31T00:00:00",
                                "reference": "https://toeic.ets.org"
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 데이터 유효성 오류 (title 누락 또는 공백)",
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
                                "code": "INVALID_INPUT",
                                "message": "제목은 필수입니다.",
                                "detail": null
                              }
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
        description = "유저를 찾을 수 없음",
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
                                "code": "USER_NOT_FOUND",
                                "message": "유저를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<GoalSingleResponseDto>> createGoal(
      @AuthenticationPrincipal Long userId, @RequestBody GoalCreateRequestDto request);

  @Operation(
      summary = "목표 삭제",
      description =
          """
          지정한 목표를 삭제합니다 (소프트 삭제 — DB에서 완전히 지워지지 않고 deleted_at이 기록됩니다).

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | String | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | id | ✅ 필수 | Long | 삭제할 목표의 ID | `42` |

          ---

          ### 주의사항
          - 본인 소유의 목표만 삭제 가능. 다른 유저의 목표 삭제 시도 시 403 반환
          - 삭제된 목표는 이후 조회 API에서 반환되지 않음
          - 이미 삭제된 목표 ID로 요청 시 404 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @Parameter(name = "id", description = "삭제할 목표 ID", example = "42", required = true)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "목표 삭제 성공",
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
        responseCode = "403",
        description = "본인 소유의 목표가 아님",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 403,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "GOAL_ACCESS_DENIED",
                                "message": "본인의 목표만 삭제할 수 있습니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "404",
        description = "목표를 찾을 수 없음 (존재하지 않거나 이미 삭제됨)",
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
                                "code": "GOAL_NOT_FOUND",
                                "message": "목표를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<Void>> deleteGoal(
      @AuthenticationPrincipal Long userId, @PathVariable Long id);

  @Operation(
      summary = "목표 목록 조회",
      description =
          """
          목표 목록을 생성일 기준으로 날짜별로 묶어 반환합니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | String | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Query Parameters

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | year | ❌ 선택 | Integer | 없음 (전체) | 조회할 연도 (생성일 기준). 없으면 전체 목표 반환 | `2026` |
          | month | ❌ 선택 | Integer | 없음 (연도 전체) | 조회할 월. **year 파라미터가 함께 있어야 함** | `5` |

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | [].date | String (yyyy-MM-dd) | 생성 날짜 |
          | [].goals | List | 해당 날짜에 생성된 목표 목록 |
          | [].goals[].id | Long | 목표 ID |
          | [].goals[].title | String | 목표 제목 |
          | [].goals[].dueDate | LocalDateTime | 마감기한 (없으면 null) |
          | [].goals[].reference | String | 참고 자료 (없으면 null) |

          ---

          ### 주의사항
          - `month`만 단독으로 전달하고 `year`가 없으면 400 반환
          - 날짜 내림차순(최신 날짜 먼저) 반환
          - 같은 날짜 내 목표는 생성 순서(ID 오름차순)로 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @Parameters({
    @Parameter(name = "year", description = "조회할 연도 (생성일 기준). 없으면 전체 조회.", example = "2026"),
    @Parameter(name = "month", description = "조회할 월 (year 필수). 없으면 연도 전체 조회.", example = "5")
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
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
                                  "date": "2026-05-04",
                                  "goals": [
                                    {
                                      "id": 10,
                                      "title": "토익 850점 달성",
                                      "dueDate": "2026-05-26T20:00:00",
                                      "reference": "https://toeic.ets.org"
                                    },
                                    {
                                      "id": 11,
                                      "title": "컴퓨터활용능력 1급 취득",
                                      "dueDate": null,
                                      "reference": null
                                    }
                                  ]
                                },
                                {
                                  "date": "2026-05-03",
                                  "goals": [
                                    {
                                      "id": 9,
                                      "title": "토익 900점 달성",
                                      "dueDate": "2026-05-11T00:00:00",
                                      "reference": null
                                    }
                                  ]
                                }
                              ],
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "year 없이 month만 전달한 경우",
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
                                "code": "GOAL_INVALID_FILTER",
                                "message": "월별 조회 시 연도(year)는 필수입니다.",
                                "detail": null
                              }
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
                }))
  })
  ResponseEntity<ApiResult<List<GoalsByDateResponseDto>>> getGoals(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month);
}
