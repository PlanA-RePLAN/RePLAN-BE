package plana.replan.domain.routine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.dto.RoutineUpdateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineCreateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineResponseDto;
import plana.replan.domain.routine.dto.SubRoutineUpdateRequestDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "Routine", description = "루틴 관련 API. 모든 요청에 JWT 인증 필수.")
public interface RoutineControllerDocs {

  @Operation(
      summary = "루틴 조회 (날짜 / 주간 / 월간)",
      description =
          """
          `filter`와 `date`를 기준으로 해당 기간의 루틴 목록을 날짜별로 묶어 반환합니다.

          - `DAILY` 루틴: 기간 내 모든 날짜에 포함
          - `WEEKLY` 루틴: 해당 날짜의 요일이 `routineDate` 비트마스크에 포함된 날짜에만 포함
          - `MONTHLY` 루틴: 해당 날짜의 일(day)이 `routineDate`와 일치하는 날짜에만 포함

          각 루틴에 해당 날짜의 Todo가 이미 생성되어 있으면 `todoId`가 포함되고, 아직 없으면 `null`입니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Query Parameters

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | filter | ❌ 선택 | string | `day` | 조회 범위 (`day` / `week` / `month`) | `week` |
          | date | ✅ 필수 | string | 없음 | 기준 시작 날짜 (yyyy-MM-dd 형식) | `2025-06-20` |

          **filter 범위 기준**
          - `day`: date 당일 1일치
          - `week`: date 포함 7일 (date ~ date+6)
          - `month`: date 포함 ~30일 (date ~ date+1개월-1일)

          ---

          ### Response Elements

          응답 `data`는 날짜(yyyy-MM-dd)를 키로 하는 객체이며, 값은 해당 날짜의 루틴 배열입니다.

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | routineId | integer | 루틴 ID |
          | title | string | 루틴 제목 |
          | dueDate | string | 반복 종료 마감일 (ISO 8601 형식). 없으면 null |
          | routineTime | string | 마감 시각 (HH:mm:ss 형식). 없으면 null |
          | routineType | string | 반복 유형 (`DAILY` / `WEEKLY` / `MONTHLY`) |
          | routineDate | integer | 반복 날짜 설정값. DAILY는 null, WEEKLY는 요일 비트마스크, MONTHLY는 일자 |
          | tagId | integer | 태그 ID. 없으면 null |
          | tagTitle | string | 태그 제목. 없으면 null |
          | tagColor | string | 태그 색상. 없으면 null |
          | goalId | integer | 목표 ID. 없으면 null |
          | todoId | integer | 해당 날짜에 생성된 Todo ID. 아직 생성 안 됐으면 null |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
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
                              "data": {
                                "2025-06-20": [
                                  {
                                    "routineId": 1,
                                    "title": "아침 스트레칭",
                                    "dueDate": null,
                                    "routineTime": "08:00:00",
                                    "routineType": "DAILY",
                                    "routineDate": null,
                                    "tagId": null,
                                    "tagTitle": null,
                                    "tagColor": null,
                                    "goalId": null,
                                    "todoId": 42
                                  }
                                ],
                                "2025-06-21": [
                                  {
                                    "routineId": 1,
                                    "title": "아침 스트레칭",
                                    "dueDate": null,
                                    "routineTime": "08:00:00",
                                    "routineType": "DAILY",
                                    "routineDate": null,
                                    "tagId": null,
                                    "tagTitle": null,
                                    "tagColor": null,
                                    "goalId": null,
                                    "todoId": null
                                  },
                                  {
                                    "routineId": 2,
                                    "title": "영어 단어 외우기",
                                    "dueDate": "2025-12-31T00:00:00",
                                    "routineTime": "09:00:00",
                                    "routineType": "WEEKLY",
                                    "routineDate": 21,
                                    "tagId": 3,
                                    "tagTitle": "영어",
                                    "tagColor": "BLUE",
                                    "goalId": 2,
                                    "todoId": 43
                                  }
                                ]
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "date 파라미터 누락·형식 오류 또는 filter 값 오류",
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
                                "message": "잘못된 입력입니다.",
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
  ResponseEntity<ApiResult<Map<String, List<RoutineResponseDto>>>> getRoutinesByFilter(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "조회 범위 (day / week / month)", example = "week")
          @RequestParam(defaultValue = "day")
          String filter,
      @Parameter(description = "기준 시작 날짜 (yyyy-MM-dd)", example = "2025-06-20") @RequestParam
          LocalDate date);

  @Operation(
      summary = "루틴 생성",
      description =
          """
          새로운 루틴을 생성합니다. 루틴 타입에 따라 반복 날짜 규칙이 달라집니다.

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
          | title | ✅ 필수 | string | 루틴 제목 | `"영어 단어 외우기"` |
          | routineType | ✅ 필수 | string | 반복 유형 (`DAILY` / `WEEKLY` / `MONTHLY`) | `"WEEKLY"` |
          | dueDate | ❌ 선택 | string | 반복 종료 마감일 (ISO 8601 형식). 이 날짜 이후로는 반복 생성 안 됨 | `"2025-12-31T00:00:00"` |
          | routineTime | ❌ 선택 | string | 반복되는 날의 마감 시각 (HH:mm:ss 형식). 생략 시 23:59:59 | `"09:00:00"` |
          | routineDate | ❌ 선택 | integer | 반복 날짜. WEEKLY: 요일 bitmask (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64). MONTHLY: 일자 (1~31). DAILY: 불필요 | `21` |
          | tagId | ❌ 선택 | integer | 태그 ID | `1` |
          | goalId | ❌ 선택 | integer | 목표 ID | `2` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### routineDate 규칙

          | routineType | routineDate 의미 | 유효 범위 |
          |-------------|-----------------|-----------|
          | `DAILY` | 사용 안 함 (무시) | — |
          | `WEEKLY` | 요일 bitmask (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64) | 1 ~ 127 |
          | `MONTHLY` | 반복할 일자 | 1 ~ 31 |

          **WEEKLY 예시**: 월+수+금 → 1+4+16 = **21**

          ---

          ### 주의사항
          - `WEEKLY` 또는 `MONTHLY` 타입에서 `routineDate`가 유효 범위를 벗어나면 400 반환
          - `tagId`가 제공된 경우 해당 태그가 존재하지 않으면 404 반환
          - `goalId`가 제공된 경우 해당 목표가 존재하지 않으면 404 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "루틴 생성 성공 — HTTP 상태는 201이며, 응답 본문의 status 필드는 ApiResult 공통 성공 규약에 따라 200으로 고정됩니다.",
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
                                "routineId": 1,
                                "title": "영어 단어 외우기",
                                "dueDate": "2025-12-31T00:00:00",
                                "routineTime": "09:00:00",
                                "routineType": "WEEKLY",
                                "routineDate": 21,
                                "tagId": 1,
                                "tagTitle": "영어",
                                "tagColor": "BLUE",
                                "goalId": 2
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 오류 (title/routineType 누락, 또는 routineDate 범위 오류)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "title 누락",
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
                          """),
                  @ExampleObject(
                      name = "routineDate 범위 오류",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "ROUTINE_INVALID_DATE",
                              "message": "유효하지 않은 반복 날짜입니다.",
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
                })),
    @ApiResponse(
        responseCode = "404",
        description = "유저, 태그 또는 목표를 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "태그 없음",
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
                          """),
                  @ExampleObject(
                      name = "목표 없음",
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
                          """)
                }))
  })
  ResponseEntity<ApiResult<RoutineResponseDto>> createRoutine(
      @AuthenticationPrincipal Long userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "WEEKLY — 전체 필드",
                            value =
                                """
                                {
                                  "title": "영어 단어 외우기",
                                  "dueDate": "2025-12-31T00:00:00",
                                  "routineTime": "09:00:00",
                                  "routineType": "WEEKLY",
                                  "routineDate": 21,
                                  "tagId": 1,
                                  "goalId": 2
                                }
                                """),
                        @ExampleObject(
                            name = "DAILY — 필수 필드만",
                            summary = "DAILY는 routineDate 불필요",
                            value =
                                """
                                {
                                  "title": "아침 스트레칭",
                                  "routineType": "DAILY"
                                }
                                """),
                        @ExampleObject(
                            name = "MONTHLY — 매월 15일",
                            value =
                                """
                                {
                                  "title": "월간 회고",
                                  "dueDate": "2025-12-31T00:00:00",
                                  "routineType": "MONTHLY",
                                  "routineDate": 15
                                }
                                """)
                      }))
          @RequestBody
          RoutineCreateRequestDto request);

  @Operation(
      summary = "하위 루틴 추가",
      description =
          """
          엄마 루틴 아래에 하위 루틴을 추가합니다.

          ### 정책
          - 하위 루틴은 `title`만 자체 값입니다. 스케줄(`routineType`/`routineDate`), `dueDate`, `tag`, `goal`, `user`는 모두 엄마 루틴을 따릅니다.
          - 1단계 깊이만 허용합니다. 하위 루틴 ID를 `parentId`로 넘기면 404로 거부됩니다.
          - 호출 시점에 엄마 루틴의 살아있는 다음 발생일 Todo가 있으면, 해당 엄마 Todo 아래에 하위 Todo가 즉시 매달립니다. 없으면 다음 스케줄러 사이클에서 함께 생성됩니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ---

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | parentId | ✅ 필수 | integer | 엄마 루틴 ID | `1` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 하위 루틴 제목 | `"스트레칭"` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "하위 루틴 생성 성공",
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
                                "routineId": 11,
                                "title": "스트레칭",
                                "parentId": 1
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "title 누락",
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
        description = "엄마 루틴을 찾을 수 없거나, 본인 소유가 아니거나, 하위 루틴 ID를 parentId로 넘긴 경우 (정책상 모두 동일 응답)",
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
                                "code": "ROUTINE_NOT_FOUND",
                                "message": "루틴을 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<SubRoutineResponseDto>> createChildRoutine(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "엄마 루틴 ID", example = "1") @PathVariable Long parentId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples =
                          @ExampleObject(
                              name = "하위 루틴 생성",
                              value =
                                  """
                              {
                                "title": "스트레칭"
                              }
                              """)))
          @RequestBody
          SubRoutineCreateRequestDto request);

  @Operation(
      summary = "엄마 루틴 수정",
      description =
          """
          엄마 루틴의 스케줄, 제목, 태그, 종료일을 전체 수정합니다. goalId는 수정할 수 없습니다.

          - 이미 생성된 과거/오늘 Todo row는 변경되지 않습니다. 미래에 생성될 Todo부터 새 값을 따라갑니다.
          - 하위 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET) — 하위 루틴 제목 변경은 `PATCH /api/routines/children/{id}` 사용.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ---

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | id | ✅ 필수 | integer | 엄마 루틴 ID | `1` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 루틴 제목 | `"영어 단어 외우기"` |
          | routineType | ✅ 필수 | string | 반복 유형 (`DAILY` / `WEEKLY` / `MONTHLY`) | `"WEEKLY"` |
          | dueDate | ❌ 선택 | string | 반복 종료 마감일 (ISO 8601 형식). null이면 종료일 제거 | `"2025-12-31T00:00:00"` |
          | routineTime | ❌ 선택 | string | 반복되는 날의 마감 시각 (HH:mm:ss 형식). null이면 23:59:59로 처리 | `"09:00:00"` |
          | routineDate | ❌ 선택 | integer | 반복 날짜. WEEKLY: 요일 bitmask (월=1~일=64). MONTHLY: 일자 (1~31). DAILY: 불필요 | `21` |
          | tagId | ❌ 선택 | integer | 태그 ID. null이면 태그 제거 | `1` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### routineDate 규칙

          | routineType | routineDate 의미 | 유효 범위 |
          |-------------|-----------------|-----------|
          | `DAILY` | 사용 안 함 (무시) | — |
          | `WEEKLY` | 요일 bitmask (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64) | 1 ~ 127 |
          | `MONTHLY` | 반복할 일자 | 1 ~ 31 |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "수정 성공",
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
                                "routineId": 1,
                                "title": "영어 단어 외우기",
                                "dueDate": "2025-12-31T00:00:00",
                                "routineTime": "09:00:00",
                                "routineType": "WEEKLY",
                                "routineDate": 21,
                                "tagId": 1,
                                "tagTitle": "영어",
                                "tagColor": "BLUE",
                                "goalId": 2
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 오류 (title/routineType 누락, 또는 routineDate 범위 오류, 또는 하위 루틴 ID 사용)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "title 누락",
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
                          """),
                  @ExampleObject(
                      name = "routineDate 범위 오류",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "ROUTINE_INVALID_DATE",
                              "message": "유효하지 않은 반복 날짜입니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "하위 루틴 ID 사용",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "ROUTINE_INVALID_TARGET",
                              "message": "이 API는 해당 루틴 종류(엄마/하위)에만 사용할 수 있습니다.",
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
                })),
    @ApiResponse(
        responseCode = "404",
        description = "루틴을 찾을 수 없거나 본인 소유가 아님, 또는 태그를 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "루틴 없음",
                      value =
                          """
                          {
                            "status": 404,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "ROUTINE_NOT_FOUND",
                              "message": "루틴을 찾을 수 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "태그 없음",
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
  ResponseEntity<ApiResult<RoutineResponseDto>> updateMotherRoutine(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "엄마 루틴 ID", example = "1") @PathVariable Long id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "WEEKLY — 전체 필드",
                            value =
                                """
                                {
                                  "title": "영어 단어 외우기",
                                  "dueDate": "2025-12-31T00:00:00",
                                  "routineTime": "09:00:00",
                                  "routineType": "WEEKLY",
                                  "routineDate": 21,
                                  "tagId": 1
                                }
                                """),
                        @ExampleObject(
                            name = "DAILY — 필수 필드만",
                            summary = "DAILY는 routineDate 불필요, optional 필드 생략",
                            value =
                                """
                                {
                                  "title": "아침 스트레칭",
                                  "routineType": "DAILY"
                                }
                                """)
                      }))
          @RequestBody
          RoutineUpdateRequestDto request);

  @Operation(
      summary = "엄마 루틴 삭제",
      description =
          """
          엄마 루틴을 삭제합니다 (soft delete). 매달려 있던 모든 하위 루틴도 함께 사라집니다.

          - 이미 생성된 Todo는 함께 삭제되지 않습니다. 다만 삭제된 루틴을 참조하던 살아있는 Todo는 **`routine` 참조가 null로 끊겨** 일반 Todo처럼 남습니다. 그래서 조회 시 해당 Todo의 `routineType`이 `null`로 나옵니다. 다음 스케줄러 사이클부터는 새 Todo가 생성되지 않습니다.
          - 하위 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET) — 하위 루틴은 `/children/{id}`로 삭제하세요.

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | id | ✅ 필수 | integer | 엄마 루틴 ID | `1` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "하위 루틴 ID를 넘긴 경우",
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
                                "code": "ROUTINE_INVALID_TARGET",
                                "message": "이 API는 해당 루틴 종류(엄마/하위)에만 사용할 수 있습니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(
        responseCode = "404",
        description = "루틴을 찾을 수 없거나 본인 소유가 아님",
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
                                "code": "ROUTINE_NOT_FOUND",
                                "message": "루틴을 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<Void>> deleteMotherRoutine(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "엄마 루틴 ID", example = "1") @PathVariable Long id);

  @Operation(
      summary = "하위 루틴 삭제",
      description =
          """
          하위 루틴 하나를 삭제합니다 (soft delete). 엄마 루틴은 영향 없습니다.

          - 이미 생성된 Todo는 함께 삭제되지 않습니다. 다만 삭제된 하위 루틴을 참조하던 살아있는 Todo는 **`routine` 참조가 null로 끊겨** 남으며, 조회 시 `routineType`이 `null`로 나옵니다. (엄마 Todo와의 `parent` 관계는 그대로 유지됩니다.)
          - 엄마 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET) — 엄마 루틴은 `/{id}`로 삭제하세요.

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | id | ✅ 필수 | integer | 하위 루틴 ID | `11` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(responseCode = "400", description = "엄마 루틴 ID를 넘긴 경우"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "404", description = "루틴을 찾을 수 없거나 본인 소유가 아님")
  })
  ResponseEntity<ApiResult<Void>> deleteChildRoutine(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "하위 루틴 ID", example = "11") @PathVariable Long id);

  @Operation(
      summary = "하위 루틴 수정",
      description =
          """
          하위 루틴의 `title`만 수정합니다. 스케줄/태그/목표는 엄마를 따라가므로 수정 대상이 아닙니다.

          - 엄마 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET).

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | id | ✅ 필수 | integer | 하위 루틴 ID | `11` |

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 변경할 하위 루틴 제목 | `"유산소 30분"` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "400", description = "title 누락 또는 엄마 루틴 ID 사용"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "404", description = "루틴을 찾을 수 없거나 본인 소유가 아님")
  })
  ResponseEntity<ApiResult<SubRoutineResponseDto>> updateChildRoutine(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "하위 루틴 ID", example = "11") @PathVariable Long id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples =
                          @ExampleObject(
                              name = "title 수정",
                              value =
                                  """
                              {
                                "title": "유산소 30분"
                              }
                              """)))
          @RequestBody
          SubRoutineUpdateRequestDto request);
}
