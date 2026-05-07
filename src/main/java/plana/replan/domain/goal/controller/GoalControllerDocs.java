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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.goal.dto.GoalCreateRequest;
import plana.replan.domain.goal.dto.GoalPageResponse;
import plana.replan.domain.goal.dto.GoalResponse;
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
                schema = @Schema(implementation = GoalResponse.class),
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
                                "reference": "https://toeic.ets.org",
                                "updatedAt": "2025-05-07T12:00:00"
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
  ResponseEntity<ApiResult<GoalResponse>> createGoal(
      @AuthenticationPrincipal Long userId, @RequestBody GoalCreateRequest request);

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
      summary = "목표 목록 조회 (커서 기반 무한스크롤)",
      description =
          """
          목표 목록을 최신순(id 내림차순)으로 반환합니다. 커서 기반 페이지네이션으로 무한스크롤을 구현합니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | String | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Query Parameters

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | cursor | ❌ 선택 | Long | 없음 (첫 페이지) | 이전 응답의 `nextCursor` 값. 없으면 첫 번째 페이지 반환 | `37` |
          | size | ❌ 선택 | int | `10` | 한 번에 가져올 목표 수 | `10` |
          | year | ❌ 선택 | int | 없음 (전체) | 조회할 연도 (`dueDate` 기준). 없으면 전체 목표 반환 | `2025` |

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | goals | List\\<GoalResponse\\> | 목표 목록 |
          | goals[].id | Long | 목표 ID |
          | goals[].title | String | 목표 제목 |
          | goals[].dueDate | LocalDateTime | 목표 기한 (null 가능) |
          | goals[].reference | String | 참고 자료 (null 가능) |
          | goals[].updatedAt | LocalDateTime | 마지막 수정 시간 |
          | nextCursor | Long | 다음 페이지 요청 시 cursor로 사용할 값. **마지막 페이지이면 null** |
          | hasNext | boolean | 다음 페이지 존재 여부. false이면 더 이상 데이터 없음 |

          ---

          ### 무한스크롤 구현 방법

          **Step 1. 첫 번째 요청** — cursor 없이 호출
          ```
          GET /api/goals?size=10
          GET /api/goals?year=2025&size=10
          ```

          **Step 2. 다음 페이지 요청** — 응답의 `nextCursor`를 cursor에 전달
          ```
          GET /api/goals?cursor=37&size=10
          GET /api/goals?year=2025&cursor=37&size=10
          ```

          **Step 3. 종료 조건** — 응답의 `hasNext`가 `false`이거나 `nextCursor`가 `null`이면 마지막 페이지

          ---

          ### 주의사항
          - `cursor`는 **마지막으로 받은 목표의 id** (응답의 `nextCursor` 값 그대로 사용)
          - `year` 파라미터가 없으면 `dueDate`가 null인 목표도 포함되어 반환됨
          - `year` 파라미터를 사용하면 해당 연도에 `dueDate`가 설정된 목표만 반환 (`dueDate`가 null인 목표는 제외)
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @Parameters({
    @Parameter(name = "cursor", description = "이전 응답의 nextCursor 값. 없으면 첫 페이지.", example = "37"),
    @Parameter(name = "size", description = "한 번에 가져올 목표 수. 기본값 10.", example = "10"),
    @Parameter(name = "year", description = "조회할 연도 (dueDate 기준). 없으면 전체 조회.", example = "2025")
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                schema = @Schema(implementation = GoalPageResponse.class),
                examples = {
                  @ExampleObject(
                      name = "중간 페이지 (hasNext=true)",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "goals": [
                                {
                                  "id": 42,
                                  "title": "토익 900점 달성",
                                  "dueDate": "2025-12-31T00:00:00",
                                  "reference": "https://toeic.ets.org",
                                  "updatedAt": "2025-05-07T12:00:00"
                                },
                                {
                                  "id": 38,
                                  "title": "운동 습관 만들기",
                                  "dueDate": "2025-06-30T00:00:00",
                                  "reference": null,
                                  "updatedAt": "2025-05-01T09:00:00"
                                }
                              ],
                              "nextCursor": 38,
                              "hasNext": true
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "마지막 페이지 (hasNext=false)",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "goals": [
                                {
                                  "id": 12,
                                  "title": "독서 50권",
                                  "dueDate": null,
                                  "reference": null,
                                  "updatedAt": "2025-01-10T08:00:00"
                                }
                              ],
                              "nextCursor": null,
                              "hasNext": false
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
                }))
  })
  ResponseEntity<ApiResult<GoalPageResponse>> getGoals(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) Integer year);
}
