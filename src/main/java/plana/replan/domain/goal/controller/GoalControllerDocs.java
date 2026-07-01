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
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.goal.dto.common.GoalSingleResponse;
import plana.replan.domain.goal.dto.create.GoalCreateRequest;
import plana.replan.domain.goal.dto.create.GoalWithTodosCreateRequest;
import plana.replan.domain.goal.dto.create.GoalWithTodosCreateResponse;
import plana.replan.domain.goal.dto.explore.GoalExploreRequest;
import plana.replan.domain.goal.dto.explore.GoalExploreResponse;
import plana.replan.domain.goal.dto.list.GoalsByDateResponse;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationRequest;
import plana.replan.domain.goal.dto.recommend.TodoRecommendationResponse;
import plana.replan.domain.goal.dto.refine.GoalRefinementRequest;
import plana.replan.domain.goal.dto.refine.GoalRefinementResponse;
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
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 목표 제목. 공백만 있으면 유효성 오류 | `"토익 900점 달성"` |
          | dueDate | ❌ 선택 | string | 목표 기한 날짜 (yyyy-MM-dd 형식) | `"2025-12-31"` |
          | dueTime | ❌ 선택 | string | 목표 기한 시간 (HH:mm 형식). dueDate 없이 단독 사용 불가. | `"09:00"` |
          | reference | ❌ 선택 | string | 참고 자료 (URL 또는 자유 텍스트). 생략 가능 | `"https://toeic.ets.org"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### 주의사항
          - `title`이 없거나 공백이면 400 반환
          - `dueTime`만 있고 `dueDate`가 없으면 400 반환
          - `dueDate`, `dueTime`, `reference`는 생략하거나 null로 전달해도 동일하게 처리됨 (null 저장)
          - JSON 파싱 실패(잘못된 형식, 타입 불일치 등)도 400 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "목표 생성 성공",
        content =
            @Content(
                schema = @Schema(implementation = GoalSingleResponse.class),
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
        description = "요청 데이터 유효성 오류 (title 누락/공백) 또는 JSON 파싱 실패",
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
  ResponseEntity<ApiResult<GoalSingleResponse>> createGoal(
      @AuthenticationPrincipal Long userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "전체 필드 포함",
                            value =
                                """
                                {
                                  "title": "토익 900점 달성",
                                  "dueDate": "2025-12-31",
                                  "dueTime": "09:00",
                                  "reference": "https://toeic.ets.org"
                                }
                                """),
                        @ExampleObject(
                            name = "날짜만 (시간 생략)",
                            value =
                                """
                                {
                                  "title": "토익 900점 달성",
                                  "dueDate": "2025-12-31"
                                }
                                """),
                        @ExampleObject(
                            name = "title만 (optional 생략)",
                            summary = "dueDate, dueTime, reference를 생략하면 null로 처리됨",
                            value =
                                """
                                {
                                  "title": "토익 900점 달성"
                                }
                                """)
                      }))
          @RequestBody
          GoalCreateRequest request);

  @Operation(
      summary = "목표+투두 일괄 생성",
      description =
          """
          목표와 투두(일반형/반복형) 및 하위투두를 단일 요청으로 일괄 생성합니다.
          투두 추천 결과를 그대로 저장할 때 사용합니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 목표 제목 | `"토익 900점 달성"` |
          | dueDate | ❌ 선택 | string | 목표 기한 날짜 (yyyy-MM-dd 형식) | `"2025-12-31"` |
          | dueTime | ❌ 선택 | string | 목표 기한 시간 (HH:mm 형식). dueDate 없이 단독 사용 불가. | `"09:00"` |
          | reference | ❌ 선택 | string | 참고 자료 (URL 또는 메모) | `"https://toeic.ets.org"` |
          | todos | ✅ 필수 | array | 생성할 투두 목록 | |
          | todos[].type | ✅ 필수 | string | `ONE_TIME` (일반형) 또는 `RECURRING` (반복형) | `"ONE_TIME"` |
          | todos[].title | ✅ 필수 | string | 투두 제목 | `"단어 암기"` |
          | todos[].dueDate | ❌ 선택 | string | 마감 날짜 (yyyy-MM-dd 형식). ONE_TIME만 사용. | `"2025-06-01"` |
          | todos[].dueTime | ❌ 선택 | string | 마감 시간 (HH:mm 형식). ONE_TIME만 사용. | `"09:00"` |
          | todos[].routineType | ❌ 선택 | string | 반복 유형. RECURRING만 사용. `DAILY` / `WEEKLY` / `MONTHLY` | `"DAILY"` |
          | todos[].routineDate | ❌ 선택 | integer | 반복 날짜. RECURRING만 사용. WEEKLY: 요일 bitmask, MONTHLY: 일자 bitmask (여러 날 합산), DAILY: null | `null` |
          | todos[].tagId | ❌ 선택 | integer | 태그 ID | `1` |
          | todos[].subTodos | ❌ 선택 | array | 하위 투두 제목 목록. ONE_TIME만 사용 가능. | `["챕터 1"]` |
          | todos[].subRoutines | ❌ 선택 | array | 하위 루틴 제목 목록. RECURRING만 사용 가능. | `["스트레칭", "유산소"]` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | goalId | integer | 생성된 목표 ID |
          | todos | array | 생성된 투두/루틴 목록 |
          | todos[].type | string | `ONE_TIME` 또는 `RECURRING` |
          | todos[].title | string | 투두 제목 |
          | todos[].todoId | integer | 생성된 투두 ID. ONE_TIME만 해당. RECURRING이면 null. |
          | todos[].routineId | integer | 생성된 루틴 ID. RECURRING만 해당. ONE_TIME이면 null. |
          | todos[].subRoutineIds | array | 생성된 하위 루틴 ID 목록. RECURRING + subRoutines 사용 시. |

          ---

          ### 주의사항
          - 모든 생성은 단일 트랜잭션으로 처리됩니다. 하나라도 실패하면 전체 롤백됩니다.
          - RECURRING 투두는 다음 반복일에 해당하는 Todo가 즉시 생성됩니다.
          - 하위투두는 ONE_TIME 투두에만, 하위루틴은 RECURRING 투두에만 추가할 수 있습니다. 교차 사용 시 400.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "일괄 생성 성공",
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
                                "goalId": 10,
                                "todos": [
                                  { "type": "ONE_TIME", "title": "단어 암기", "todoId": 101, "routineId": null, "subRoutineIds": [] },
                                  { "type": "RECURRING", "title": "리스닝 연습", "todoId": null, "routineId": 201, "subRoutineIds": [301, 302] }
                                ]
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 데이터 유효성 오류",
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
                            "error": { "code": "EMPTY_TOKEN", "message": "토큰이 없습니다.", "detail": null }
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
                            "error": { "code": "EXPIRED_TOKEN", "message": "만료된 토큰입니다.", "detail": null }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "유저 또는 태그를 찾을 수 없음",
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
                                "code": "TAG_NOT_FOUND",
                                "message": "태그를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<GoalWithTodosCreateResponse>> createGoalWithTodos(
      @AuthenticationPrincipal Long userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "전체 필드 포함",
                            value =
                                """
                                {
                                  "title": "토익 900점 달성",
                                  "dueDate": "2025-12-31",
                                  "dueTime": "09:00",
                                  "reference": "https://toeic.ets.org",
                                  "todos": [
                                    {
                                      "type": "ONE_TIME",
                                      "title": "단어 암기",
                                      "dueDate": "2025-06-01",
                                      "dueTime": "09:00",
                                      "routineType": null,
                                      "routineDate": null,
                                      "tagId": 1,
                                      "subTodos": ["챕터 1", "챕터 2"]
                                    },
                                    {
                                      "type": "RECURRING",
                                      "title": "리스닝 연습",
                                      "dueDate": null,
                                      "dueTime": null,
                                      "routineType": "DAILY",
                                      "routineDate": null,
                                      "tagId": null,
                                      "subTodos": [],
                                      "subRoutines": ["받아쓰기", "쉐도잉"]
                                    }
                                  ]
                                }
                                """),
                        @ExampleObject(
                            name = "필수 필드만 (선택 필드 생략)",
                            value =
                                """
                                {
                                  "title": "토익 900점 달성",
                                  "todos": [
                                    {
                                      "type": "ONE_TIME",
                                      "title": "단어 암기",
                                      "subTodos": []
                                    }
                                  ]
                                }
                                """)
                      }))
          @RequestBody
          GoalWithTodosCreateRequest request);

  @Operation(
      summary = "목표 삭제",
      description =
          """
          지정한 목표를 삭제합니다 (소프트 삭제 — DB에서 완전히 지워지지 않고 deleted_at이 기록됩니다).

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | id | ✅ 필수 | integer | 삭제할 목표의 ID | `42` |

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
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Query Parameters

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | year | ❌ 선택 | integer | 조회할 연도 (생성일 기준) | `2026` |
          | month | ❌ 선택 | integer | 조회할 월 (1~12). **year가 없으면 400** | `5` |

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | [].date | string (yyyy-MM-dd) | 생성 날짜 |
          | [].goals | array | 해당 날짜에 생성된 목표 목록 |
          | [].goals[].id | integer | 목표 ID |
          | [].goals[].title | string | 목표 제목 |
          | [].goals[].dueDate | string (ISO 8601) | 마감기한 (없으면 null). 예: `"2026-05-26T20:00:00"` |
          | [].goals[].reference | string | 참고 자료 (없으면 null) |

          ---

          ### 주의사항

          **조회 케이스**

          | year | month | 동작 |
          |------|-------|------|
          | ❌ | ❌ | <span style="color:green">전체 조회</span> |
          | ⭕ | ❌ | <span style="color:green">해당 연도 전체 조회</span> |
          | ⭕ | ⭕ | <span style="color:green">해당 연도·월 조회</span> |
          | ❌ | ⭕ | <span style="color:red">400 오류 — year 없이 month만 전달 불가</span> |

          - 날짜 내림차순(최신 날짜 먼저) 반환
          - 같은 날짜 내 목표는 생성 순서(ID 오름차순)로 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @Parameters({
    @Parameter(name = "year", description = "조회할 연도. 없으면 전체 조회, 있으면 연도별 조회.", example = "2026"),
    @Parameter(
        name = "month",
        description = "조회할 월 (1~12). year와 함께 전달해야 월별 조회. year 없이 month만 전달하면 400.",
        example = "5")
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
        description = "잘못된 필터 파라미터 (year 없이 month만 전달, 또는 month 범위 오류)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "year 없이 month만 전달",
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
                          """),
                  @ExampleObject(
                      name = "월 범위 오류",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "GOAL_INVALID_MONTH",
                              "message": "월은 1 이상 12 이하여야 합니다.",
                              "detail": null
                            }
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
                }))
  })
  ResponseEntity<ApiResult<List<GoalsByDateResponse>>> getGoals(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month);

  @Operation(
      summary = "AI 목표 정제",
      description =
          """
          탐색(exploreGoal) 단계에서 받은 질문과 사용자 답변을 함께 전달하면, AI가 목표·종료일정·질문별 솔루션을 정제하여 반환합니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | goal | ✅ 필수 | string | 목표 (자연어). 공백만 있으면 유효성 오류 | `"토익 850점 이상 달성"` |
          | deadlineDate | ❌ 선택 | string | 종료 날짜 (yyyy-MM-dd 형식) | `"2026-05-01"` |
          | deadlineTime | ❌ 선택 | string | 종료 시간 (HH:mm 형식) | `"23:59"` |
          | answers | ✅ 필수 | array | 탐색 단계에서 받은 질문과 사용자 답변 목록. 최소 1개 이상 | |
          | answers[].question | ✅ 필수 | string | 탐색에서 받은 질문 | `"현재 영어 실력"` |
          | answers[].answer | ❌ 선택 | string | 사용자 답변. 빈 값일 수 있음 | `"토익 600점대, RC 취약"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | goal | object | 정제된 목표 |
          | goal.value | string | 정제된 목표 텍스트 |
          | goal.reason | string | AI 정제 근거 |
          | deadline | object | 정제된 종료일정 |
          | deadline.date | string | 마감 날짜 (yyyy-MM-dd). 기한 없음이면 null |
          | deadline.time | string | 마감 시간 (HH:mm). 시간 미설정이면 null |
          | deadline.reason | string | AI 정제 근거 |
          | solutions | array | 질문별 정제 결과 목록 |
          | solutions[].question | string | 어떤 질문에 대한 정제인지 |
          | solutions[].items | array | 정제된 항목 목록 |
          | solutions[].items[].title | string | 항목 제목 (카테고리) |
          | solutions[].items[].content | string | 항목 내용 |
          | solutions[].reason | string | AI 정제 근거 |

          ---

          ### 주의사항
          - 응답 시간이 일반 API보다 길 수 있습니다 (최대 30초).
          - AI 서비스 장애 시 502 반환.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "목표 정제 성공",
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
                                "goal": {
                                  "value": "토익 900점 달성 (LC 450·RC 450 이상)",
                                  "reason": "섹션별 목표를 명시해 학습 방향을 명확히 했습니다."
                                },
                                "deadline": {
                                  "date": "2025-08-25",
                                  "time": "08:00",
                                  "reason": "오늘부터 3개월, 마지막 1주는 점검 기간으로 배정했습니다."
                                },
                                "solutions": [
                                  {
                                    "question": "현재 영어 실력",
                                    "items": [
                                      { "title": "현재 수준", "content": "토익 600점대 (LC 310·RC 290 추정), RC 취약" },
                                      { "title": "목표와의 격차", "content": "LC 140점, RC 160점 향상 필요" }
                                    ],
                                    "reason": "현재 실력과 목표 사이 격차를 영역별로 정리했습니다."
                                  },
                                  {
                                    "question": "하루에 투자할 수 있는 시간",
                                    "items": [
                                      { "title": "학습 루틴", "content": "평일 1시간·주말 4시간 (주 약 13시간)" },
                                      { "title": "배분 전략", "content": "평일 아침 단어 암기, 저녁 RC 파트 풀이" }
                                    ],
                                    "reason": "주말을 활용해 목표 달성 가능성을 높였습니다."
                                  }
                                ]
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "필수 필드 누락 (goal 비어있음 또는 answers 빈 배열)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "goal 누락",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "INVALID_INPUT",
                              "message": "목표는 필수입니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "answers 비어있음",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "INVALID_INPUT",
                              "message": "질문/답변은 최소 1개 이상이어야 합니다.",
                              "detail": null
                            }
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
                            "error": { "code": "EMPTY_TOKEN", "message": "토큰이 없습니다.", "detail": null }
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
                            "error": { "code": "EXPIRED_TOKEN", "message": "만료된 토큰입니다.", "detail": null }
                          }
                          """)
                })),
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
                            "error": { "code": "GEMINI_API_ERROR", "message": "AI 추천 서비스에 일시적인 오류가 발생했습니다.", "detail": null }
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
                            "error": { "code": "GEMINI_PARSE_ERROR", "message": "AI 응답을 처리하는 중 오류가 발생했습니다.", "detail": null }
                          }
                          """)
                }))
  })
  ResponseEntity<ApiResult<GoalRefinementResponse>> refineGoal(
      @AuthenticationPrincipal Long userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "전체 필드 포함 (종료일정 포함)",
                            value =
                                """
                                {
                                  "goal": "토익 850점 이상 달성",
                                  "deadlineDate": "2026-05-01",
                                  "deadlineTime": "23:59",
                                  "answers": [
                                    { "question": "현재 영어 실력", "answer": "토익 600점대, RC 취약" },
                                    { "question": "하루에 투자할 수 있는 시간", "answer": "평일 1시간, 주말 4시간" },
                                    { "question": "어떤 교재나 학습 방법을 선호하시나요?", "answer": "해커스" }
                                  ]
                                }
                                """),
                        @ExampleObject(
                            name = "종료일정 생략",
                            summary = "deadlineDate, deadlineTime을 생략하면 null로 처리됩니다.",
                            value =
                                """
                                {
                                  "goal": "토익 850점 이상 달성",
                                  "answers": [
                                    { "question": "현재 영어 실력", "answer": "토익 600점대, RC 취약" },
                                    { "question": "하루에 투자할 수 있는 시간", "answer": "" },
                                    { "question": "어떤 교재나 학습 방법을 선호하시나요?", "answer": "상관없어요" }
                                  ]
                                }
                                """)
                      }))
          @Valid
          @RequestBody
          GoalRefinementRequest request);

  @Operation(
      summary = "AI 투두 추천",
      description =
          """
          목표 정보를 기반으로 AI가 달성에 필요한 투두 리스트를 추천합니다.
          반환된 데이터를 사용해 클라이언트가 기존 투두/루틴 생성 API를 호출하여 실제 투두를 생성합니다.

          새로고침: 같은 요청 몸체에 refreshCount만 1~3으로 올려 다시 호출하면 회차별 다른 스타일의 추천이 옵니다(최대 3회).

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | goal | ✅ 필수 | string | 목표. 공백만 있으면 유효성 오류 | `"토익 900점 달성 (LC 450·RC 450 이상)"` |
          | deadlineDate | ❌ 선택 | string | 마감 날짜 (yyyy-MM-dd 형식) | `"2025-08-25"` |
          | deadlineTime | ❌ 선택 | string | 마감 시간 (HH:mm 형식) | `"08:00"` |
          | solutions | ❌ 선택 | array | 정제·수정이 끝난 솔루션 목록. 정제(refineGoal) 응답의 solutions를 그대로 전달 | |
          | solutions[].question | ❌ 선택 | string | 어떤 질문에 대한 솔루션인지 | `"현재 영어 실력"` |
          | solutions[].items | ❌ 선택 | array | 솔루션 항목 목록 | |
          | solutions[].items[].title | ❌ 선택 | string | 항목 제목 (카테고리) | `"현재 수준"` |
          | solutions[].items[].content | ❌ 선택 | string | 항목 내용 | `"토익 600점대 (LC 310·RC 290 추정), RC 취약"` |
          | refreshCount | ❌ 선택 | integer | 새로고침 횟수 (0~3). 0 또는 생략은 첫 추천, 1~3은 회차별 스타일로 다시 추천 | `0` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | overallReason | string | 전체 추천 총평. 교재·강의가 포함된 경우 저자/출판사/링크 또는 강사/플랫폼/링크 포함 |
          | todos | array | 추천 투두 목록 |
          | todos[].type | string | `ONE_TIME` (일회형) 또는 `RECURRING` (반복형) |
          | todos[].title | string | 투두 제목 |
          | todos[].dueDate | string | 마감 날짜 (yyyy-MM-dd 형식). 없으면 null |
          | todos[].dueTime | string | 마감 시간 (HH:mm 형식). 없으면 null |
          | todos[].routineType | string | RECURRING만: `DAILY` / `WEEKLY` / `MONTHLY`. ONE_TIME이면 null |
          | todos[].routineDate | integer | RECURRING WEEKLY: 요일 bitmask. MONTHLY: 일자 bitmask. DAILY·ONE_TIME: null |
          | todos[].tagId | integer | AI가 이 투두에 배정한 태그 ID. 요청한 유저의 실제 태그 중에서 선택됨. 마땅한 태그가 없으면 null |
          | todos[].tagName | string | 배정된 태그 이름. tagId가 null이면 함께 null |

          ---

          ### 주의사항
          - 추천 결과는 실제 투두 생성이 아닙니다. 클라이언트가 `POST /api/todos` 또는 루틴 생성 API를 별도 호출해야 합니다.
          - 교재·강의가 포함된 경우 Google Search로 목차·분량을 검색하여 투두를 세분화합니다.
          - 응답 시간이 일반 API보다 길 수 있습니다 (최대 30초).
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "투두 추천 성공",
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
                                "overallReason": "단어 암기와 RC 강의 수강을 병행하고 마감 D-1에 최종 점검을 배치했습니다. 하루 투자시간의 50%를 초과하지 않도록 분량을 조정했습니다. 사용 교재: 해커스 토익 기출 VOCA (저자: 해커스어학연구소 / 출판사: 해커스어학원 / 링크: https://www.yes24.com/product/goods/97469327), 해커스 토익 실전 1000제 RC (저자: 해커스어학연구소 / 출판사: 해커스어학원 / 링크: https://www.yes24.com/product/goods/74369738).",
                                "todos": [
                                  {
                                    "type": "RECURRING",
                                    "title": "해커스 보카 30단어 암기 및 복습",
                                    "dueDate": "2025-08-25",
                                    "dueTime": null,
                                    "routineType": "DAILY",
                                    "routineDate": null,
                                    "tagId": 1,
                                    "tagName": "Study"
                                  },
                                  {
                                    "type": "ONE_TIME",
                                    "title": "해커스 RC 1~5강 수강",
                                    "dueDate": "2025-06-10",
                                    "dueTime": "08:00",
                                    "routineType": null,
                                    "routineDate": null,
                                    "tagId": 1,
                                    "tagName": "Study"
                                  },
                                  {
                                    "type": "ONE_TIME",
                                    "title": "실전 모의고사 최종 점검",
                                    "dueDate": "2025-08-24",
                                    "dueTime": null,
                                    "routineType": null,
                                    "routineDate": null,
                                    "tagId": null,
                                    "tagName": null
                                  }
                                ]
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "필수 필드 누락 또는 새로고침 횟수 오류",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "필수 필드 누락",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "INVALID_INPUT",
                              "message": "목표는 필수입니다.",
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
                              "code": "INVALID_REFRESH_COUNT",
                              "message": "새로고침 횟수는 0 이상 3 이하여야 합니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
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
                            "error": { "code": "EMPTY_TOKEN", "message": "토큰이 없습니다.", "detail": null }
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
                            "error": { "code": "EXPIRED_TOKEN", "message": "만료된 토큰입니다.", "detail": null }
                          }
                          """)
                })),
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
                            "error": { "code": "GEMINI_API_ERROR", "message": "AI 추천 서비스에 일시적인 오류가 발생했습니다.", "detail": null }
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
                            "error": { "code": "GEMINI_PARSE_ERROR", "message": "AI 응답을 처리하는 중 오류가 발생했습니다.", "detail": null }
                          }
                          """)
                }))
  })
  ResponseEntity<ApiResult<TodoRecommendationResponse>> recommendTodos(
      @AuthenticationPrincipal Long userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "전체 필드 포함 (솔루션·종료일정 포함)",
                            value =
                                """
                                {
                                  "goal": "토익 900점 달성 (LC 450·RC 450 이상)",
                                  "deadlineDate": "2025-08-25",
                                  "deadlineTime": "08:00",
                                  "solutions": [
                                    {
                                      "question": "현재 영어 실력",
                                      "items": [
                                        { "title": "현재 수준", "content": "토익 600점대 (LC 310·RC 290 추정), RC 취약" },
                                        { "title": "목표와의 격차", "content": "LC 140점, RC 160점 향상 필요" }
                                      ]
                                    },
                                    {
                                      "question": "하루에 투자할 수 있는 시간",
                                      "items": [
                                        { "title": "학습 루틴", "content": "평일 1시간·주말 4시간 (주 약 13시간)" },
                                        { "title": "배분 전략", "content": "평일 아침 단어 암기, 저녁 RC 파트 풀이" }
                                      ]
                                    }
                                  ],
                                  "refreshCount": 0
                                }
                                """),
                        @ExampleObject(
                            name = "솔루션·종료일정 생략 (goal만)",
                            summary =
                                "goal만 필수. solutions, deadlineDate, deadlineTime, refreshCount는 생략하면 null로 처리됩니다.",
                            value =
                                """
                                {
                                  "goal": "토익 900점 달성 (LC 450·RC 450 이상)"
                                }
                                """)
                      }))
          @Valid
          @RequestBody
          TodoRecommendationRequest request);

  @Operation(
      summary = "AI 목표 탐색",
      description =
          """
          사용자가 입력한 목표를 AI가 분석하여 목표 달성에 필요한 추가 정보를 수집하기 위한 질문 목록을 반환합니다.
          목표가 달성 불가능하다고 판단되면 valid=false와 안내 메시지를 반환합니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | goal | ✅ 필수 | string | 목표 (자연어). 공백만 있으면 유효성 오류 | `"토익 850점 이상 달성"` |
          | deadlineDate | ❌ 선택 | string | 종료 날짜 (yyyy-MM-dd 형식) | `"2026-05-01"` |
          | deadlineTime | ❌ 선택 | string | 종료 시간 (HH:mm 형식) | `"23:59"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | valid | boolean | 달성 가능한 목표인지 여부. false면 questions는 빈 배열 |
          | message | string | valid=false일 때 사용자에게 보여줄 안내 메시지. valid=true면 null |
          | questions | array | AI가 생성한 질문 목록 (valid=true일 때 3개) |
          | questions[].question | string | 질문 본문 |
          | questions[].chips | array | 질문에 대한 선택지 목록 (string 배열) |

          ---

          ### 주의사항
          - valid=false는 오류가 아니라 정상 200 응답입니다. 이 경우 message를 사용자에게 안내하세요.
          - 응답 시간이 일반 API보다 길 수 있습니다 (최대 30초).
          - AI 서비스 장애 시 502 반환.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "목표 탐색 성공 (valid=true: 질문 반환 / valid=false: 안내 메시지 반환)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "탐색 성공 — 질문 3개 반환 (valid=true)",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "valid": true,
                              "message": null,
                              "questions": [
                                {
                                  "question": "현재 토익 점수가 어떻게 되시나요?",
                                  "chips": ["아직 없어요", "600점대", "700점대", "800점 이상"]
                                },
                                {
                                  "question": "하루에 투자할 수 있는 시간이 얼마나 되나요?",
                                  "chips": ["30분 미만", "30분~1시간", "1~2시간", "2시간 이상"]
                                },
                                {
                                  "question": "어떤 교재나 학습 방법을 선호하시나요?",
                                  "chips": ["해커스", "YBM", "온라인 강의", "상관없어요"]
                                }
                              ]
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "달성 불가 목표 — 안내 메시지 반환 (valid=false)",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "valid": false,
                              "message": "달성할 수 있는 구체적인 목표를 입력해주세요. 예: '토익 850점 달성', '체중 5kg 감량'",
                              "questions": []
                            },
                            "error": null
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "goal 누락 또는 공백",
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
                                "message": "목표는 필수입니다.",
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
                            "error": { "code": "EMPTY_TOKEN", "message": "토큰이 없습니다.", "detail": null }
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
                            "error": { "code": "EXPIRED_TOKEN", "message": "만료된 토큰입니다.", "detail": null }
                          }
                          """)
                })),
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
                            "error": { "code": "GEMINI_API_ERROR", "message": "AI 추천 서비스에 일시적인 오류가 발생했습니다.", "detail": null }
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
                            "error": { "code": "GEMINI_PARSE_ERROR", "message": "AI 응답을 처리하는 중 오류가 발생했습니다.", "detail": null }
                          }
                          """)
                }))
  })
  ResponseEntity<ApiResult<GoalExploreResponse>> exploreGoal(
      @AuthenticationPrincipal Long userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "전체 필드 포함",
                            value =
                                """
                                {
                                  "goal": "토익 850점 이상 달성",
                                  "deadlineDate": "2026-05-01",
                                  "deadlineTime": "23:59"
                                }
                                """),
                        @ExampleObject(
                            name = "필수 필드만 (선택 필드 생략)",
                            summary = "deadlineDate, deadlineTime을 생략하면 null로 처리됩니다.",
                            value =
                                """
                                {
                                  "goal": "토익 850점 이상 달성"
                                }
                                """)
                      }))
          @Valid
          @RequestBody
          GoalExploreRequest request);
}
