package plana.replan.domain.routine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import plana.replan.domain.routine.dto.RoutineOverrideCompleteRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideContentRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideOrderRequestDto;
import plana.replan.domain.routine.dto.RoutineOverridePinRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideResponseDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "Routine", description = "루틴 인스턴스 단건 수정 API. 반복 todo의 특정 날짜 인스턴스만 변경한다.")
public interface RoutineOverrideControllerDocs {

  @Operation(
      summary = "루틴 인스턴스 내용 수정",
      description =
          """
          특정 날짜의 루틴 인스턴스 제목/태그를 수정한다 (단건).
          전체 루틴을 수정하려면 `PUT /api/routines/{id}` 사용.

          - 이미 배치로 Todo가 생성된 날짜라면 해당 Todo에도 즉시 반영된다.
          - 전체 루틴 수정(`PUT /api/routines/{id}`) 시 오늘 이후 override는 일괄 삭제된다.

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          ### Path Variables

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | routineId | ✅ 필수 | integer | 루틴 ID | `1` |
          | date | ✅ 필수 | string | 수정할 날짜 (yyyy-MM-dd 형식) | `2026-07-01` |

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ❌ 선택 | string | 제목 override. null이면 루틴 기본 제목 유지 | `"아침 스트레칭 (특별)"` |
          | tagId | ❌ 선택 | integer | 태그 ID override. null이면 루틴 기본 태그 유지 | `5` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
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
                                "overrideDate": "2026-07-01",
                                "effectiveTitle": "아침 스트레칭 (특별)",
                                "effectiveTagId": 5,
                                "effectiveTagTitle": "운동",
                                "effectiveTagColor": "GREEN",
                                "effectiveSortOrder": 10000.0,
                                "isSkipped": false,
                                "isPinned": false,
                                "isCompleted": false,
                                "hasOverride": true,
                                "todoId": null
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "제목이 빈 문자열, 또는 루틴이 발생하지 않는 날짜(요일/일자 불일치, 반복 종료일 이후)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "빈 제목",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"INVALID_INPUT","message":"잘못된 입력입니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "발생하지 않는 날짜",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"ROUTINE_INVALID_DATE","message":"유효하지 않은 반복 날짜입니다.","detail":null}}
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
                          {"status":401,"success":false,"data":null,"error":{"code":"EMPTY_TOKEN","message":"토큰이 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {"status":401,"success":false,"data":null,"error":{"code":"EXPIRED_TOKEN","message":"만료된 토큰입니다.","detail":null}}
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "루틴 또는 태그를 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "루틴 없음",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"ROUTINE_NOT_FOUND","message":"루틴을 찾을 수 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "태그 없음",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"TAG_NOT_FOUND","message":"태그를 찾을 수 없습니다.","detail":null}}
                          """)
                }))
  })
  ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updateContent(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "루틴 ID", example = "1") @PathVariable Long routineId,
      @Parameter(description = "수정할 날짜 (yyyy-MM-dd)", example = "2026-07-01") @PathVariable
          LocalDate date,
      @RequestBody RoutineOverrideContentRequestDto request);

  @Operation(
      summary = "루틴 인스턴스 정렬 변경",
      description =
          """
          특정 날짜의 루틴 인스턴스 정렬 순서를 변경한다.

          - `sortOrder`는 프론트가 인접 항목의 값을 기반으로 계산해서 전달한다.
          - 이미 배치로 Todo가 생성된 날짜라면 해당 Todo의 sortOrder에도 즉시 반영된다.

          ### Path Variables

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | routineId | ✅ 필수 | integer | 루틴 ID | `1` |
          | date | ✅ 필수 | string | 변경할 날짜 (yyyy-MM-dd 형식) | `2026-07-01` |

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | sortOrder | ✅ 필수 | number | 정렬 순서 | `5000.0` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "정렬 변경 성공",
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
                                "overrideDate": "2026-07-01",
                                "effectiveTitle": "아침 스트레칭",
                                "effectiveTagId": null,
                                "effectiveTagTitle": null,
                                "effectiveTagColor": null,
                                "effectiveSortOrder": 5000.0,
                                "isSkipped": false,
                                "isPinned": false,
                                "isCompleted": false,
                                "hasOverride": true,
                                "todoId": null
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description =
            "sortOrder 누락, 또는 루틴이 발생하지 않는 날짜(요일/일자 불일치, 반복 종료일 이후) — ROUTINE_INVALID_DATE"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "404", description = "루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updateOrder(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "루틴 ID", example = "1") @PathVariable Long routineId,
      @Parameter(description = "변경할 날짜 (yyyy-MM-dd)", example = "2026-07-01") @PathVariable
          LocalDate date,
      @Valid @RequestBody RoutineOverrideOrderRequestDto request);

  @Operation(
      summary = "루틴 인스턴스 완료/미완료 처리",
      description =
          """
          특정 날짜의 루틴 인스턴스를 완료 또는 미완료 처리한다.

          - `isCompleted: true` 시 서버에서 `completedTime`을 현재 시각으로 자동 기록한다.
          - 이미 배치로 Todo가 생성된 날짜라면 해당 Todo에도 즉시 반영된다.

          ### Path Variables

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | routineId | ✅ 필수 | integer | 루틴 ID | `1` |
          | date | ✅ 필수 | string | 처리할 날짜 (yyyy-MM-dd 형식) | `2026-07-01` |

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | isCompleted | ✅ 필수 | boolean | `true`면 완료, `false`면 미완료 | `true` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "완료/미완료 처리 성공",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "완료",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "routineId": 1,
                              "overrideDate": "2026-07-01",
                              "effectiveTitle": "아침 스트레칭",
                              "effectiveTagId": null,
                              "effectiveTagTitle": null,
                              "effectiveTagColor": null,
                              "effectiveSortOrder": 10000.0,
                              "isSkipped": false,
                              "isPinned": false,
                              "isCompleted": true,
                              "hasOverride": true,
                              "todoId": null
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "미완료",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "routineId": 1,
                              "overrideDate": "2026-07-01",
                              "effectiveTitle": "아침 스트레칭",
                              "effectiveTagId": null,
                              "effectiveTagTitle": null,
                              "effectiveTagColor": null,
                              "effectiveSortOrder": 10000.0,
                              "isSkipped": false,
                              "isPinned": false,
                              "isCompleted": false,
                              "hasOverride": true,
                              "todoId": null
                            },
                            "error": null
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "400",
        description =
            "isCompleted 누락, 또는 루틴이 발생하지 않는 날짜(요일/일자 불일치, 반복 종료일 이후) — ROUTINE_INVALID_DATE"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "404", description = "루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updateComplete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "루틴 ID", example = "1") @PathVariable Long routineId,
      @Parameter(description = "처리할 날짜 (yyyy-MM-dd)", example = "2026-07-01") @PathVariable
          LocalDate date,
      @Valid @RequestBody RoutineOverrideCompleteRequestDto request);

  @Operation(
      summary = "루틴 인스턴스 핀/언핀",
      description =
          """
          특정 날짜의 루틴 인스턴스를 핀 또는 언핀 처리한다.

          - 이미 배치로 Todo가 생성된 날짜라면 해당 Todo에도 즉시 반영된다.

          ### Path Variables

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | routineId | ✅ 필수 | integer | 루틴 ID | `1` |
          | date | ✅ 필수 | string | 처리할 날짜 (yyyy-MM-dd 형식) | `2026-07-01` |

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | isPinned | ✅ 필수 | boolean | `true`면 핀, `false`면 언핀 | `true` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "핀/언핀 성공"),
    @ApiResponse(
        responseCode = "400",
        description =
            "isPinned 누락, 또는 루틴이 발생하지 않는 날짜(요일/일자 불일치, 반복 종료일 이후) — ROUTINE_INVALID_DATE"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "404", description = "루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<RoutineOverrideResponseDto>> updatePin(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "루틴 ID", example = "1") @PathVariable Long routineId,
      @Parameter(description = "처리할 날짜 (yyyy-MM-dd)", example = "2026-07-01") @PathVariable
          LocalDate date,
      @Valid @RequestBody RoutineOverridePinRequestDto request);

  @Operation(
      summary = "루틴 인스턴스 건너뜀 (삭제)",
      description =
          """
          특정 날짜의 루틴 인스턴스를 건너뜀 처리한다. 해당 날짜에 Todo가 생성되지 않는다.

          - 이미 배치로 Todo가 생성된 날짜라면 해당 Todo를 soft delete 한다.
          - 이미 완료된 Todo가 있는 날짜는 건너뜀 처리할 수 없다 (400).
          - 건너뜀을 취소하려면 `PATCH /api/routines/{routineId}/overrides/{date}/undo`을 호출한다.

          ### Path Variables

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | routineId | ✅ 필수 | integer | 루틴 ID | `1` |
          | date | ✅ 필수 | string | 건너뜀 처리할 날짜 (yyyy-MM-dd 형식) | `2026-07-01` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "건너뜀 처리 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "이미 완료된 Todo가 있는 날짜, 또는 루틴이 발생하지 않는 날짜(요일/일자 불일치, 반복 종료일 이후)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "완료된 날짜 건너뜀 시도",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"ROUTINE_OVERRIDE_CANNOT_SKIP_COMPLETED","message":"이미 완료된 Todo가 있는 날짜는 건너뜀 처리할 수 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "발생하지 않는 날짜",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"ROUTINE_INVALID_DATE","message":"유효하지 않은 반복 날짜입니다.","detail":null}}
                          """)
                })),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "404", description = "루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<Void>> skip(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "루틴 ID", example = "1") @PathVariable Long routineId,
      @Parameter(description = "건너뜀 처리할 날짜 (yyyy-MM-dd)", example = "2026-07-01") @PathVariable
          LocalDate date);

  @Operation(
      summary = "루틴 인스턴스 건너뜀 실행 취소 (undo)",
      description =
          """
          건너뜀 처리된 날짜를 다시 활성화한다.

          - override의 `isSkipped`를 `false`로 되돌린다.
          - soft-delete된 Todo가 있으면 함께 복구한다.
          - 건너뜀 상태가 아닌 날짜에 호출해도 에러 없이 idempotent하게 처리된다.

          ### Path Variables

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | routineId | ✅ 필수 | integer | 루틴 ID | `1` |
          | date | ✅ 필수 | string | 취소할 날짜 (yyyy-MM-dd 형식) | `2026-07-01` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "건너뜀 취소 성공"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "404", description = "루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<Void>> undo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "루틴 ID", example = "1") @PathVariable Long routineId,
      @Parameter(description = "건너뜀을 취소할 날짜 (yyyy-MM-dd)", example = "2026-07-01") @PathVariable
          LocalDate date);

  @Operation(
      summary = "루틴 인스턴스 디테일 조회",
      description =
          """
          특정 날짜의 루틴 인스턴스 정보를 조회한다.

          - Todo가 이미 생성된 날짜라면 `todoId`가 포함된다.
          - 미래 날짜(아직 Todo가 없는 경우)라면 `todoId`는 null이다.
          - override가 없는 경우에도 루틴 기본값으로 응답한다.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Path Variables**

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | routineId | ✅ 필수 | integer | 루틴 ID | `1` |
          | date | ✅ 필수 | string | 조회할 날짜 (yyyy-MM-dd 형식) | `2026-07-01` |

          **Response Elements**

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | routineId | integer | 루틴 ID |
          | overrideDate | string | 조회 날짜 (yyyy-MM-dd 형식) |
          | effectiveTitle | string | 실제 적용될 제목 (override 우선) |
          | effectiveTagId | integer | 실제 적용될 태그 ID. 없으면 null |
          | effectiveTagTitle | string | 실제 적용될 태그 제목. 없으면 null |
          | effectiveTagColor | string | 실제 적용될 태그 색상. 없으면 null |
          | effectiveSortOrder | number | 실제 적용될 정렬 순서 |
          | isSkipped | boolean | 건너뜀 여부 |
          | isPinned | boolean | 핀 여부 |
          | isCompleted | boolean | 완료 여부 |
          | hasOverride | boolean | override 존재 여부 |
          | todoId | integer | 생성된 Todo ID. 없으면 null |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "루틴 인스턴스 디테일 조회 성공",
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
                                "overrideDate": "2026-07-01",
                                "effectiveTitle": "아침 스트레칭",
                                "effectiveTagId": 3,
                                "effectiveTagTitle": "운동",
                                "effectiveTagColor": "GREEN",
                                "effectiveSortOrder": 10000.0,
                                "isSkipped": false,
                                "isPinned": false,
                                "isCompleted": false,
                                "hasOverride": false,
                                "todoId": 42
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
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
        description = "루틴을 찾을 수 없음",
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
  ResponseEntity<ApiResult<RoutineOverrideResponseDto>> getOverride(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "루틴 ID", example = "1") @PathVariable Long routineId,
      @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", example = "2026-07-01") @PathVariable
          LocalDate date);
}
