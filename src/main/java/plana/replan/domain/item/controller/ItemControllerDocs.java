package plana.replan.domain.item.controller;

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
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.item.dto.ItemCompleteRequestDto;
import plana.replan.domain.item.dto.ItemContentRequestDto;
import plana.replan.domain.item.dto.ItemDeleteRequestDto;
import plana.replan.domain.item.dto.ItemDetailResponseDto;
import plana.replan.domain.item.dto.ItemKind;
import plana.replan.domain.item.dto.ItemOrderRequestDto;
import plana.replan.domain.item.dto.ItemPinRequestDto;
import plana.replan.domain.item.dto.ItemResponseDto;
import plana.replan.domain.item.dto.ItemSubTodoCreateRequestDto;
import plana.replan.domain.item.dto.ItemSubTodoDeleteRequestDto;
import plana.replan.domain.item.dto.ItemSubTodoUpdateRequestDto;
import plana.replan.global.common.ApiResult;

@Tag(
    name = "Home (홈 통합)",
    description =
        """
        홈 화면용 통합 아이템 API. 일회성 투두와 반복 루틴의 날짜별 회차를 "아이템" 하나의 개념으로 다룬다.

        - 아이템 구분: `kind` = `TODO`(일회성 투두) / `ROUTINE`(루틴의 특정 날짜 회차)
        - 아이템 주소: TODO는 `todoId`, ROUTINE은 `routineId` + `date`
        - ROUTINE 수정/삭제 범위: `scope` = `THIS`(이 회차만) / `ALL`(반복 전체)
        - 기존 투두/루틴 API를 감싸는 창구이며, 동작 규칙(검증·에러)은 기존 API와 동일하다.
        """)
public interface ItemControllerDocs {

  // ── 공통 에러 응답 예시 ──
  String ERR_EMPTY_TOKEN =
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
      """;

  String ERR_EXPIRED_TOKEN =
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
      """;

  String ERR_INVALID_FILTER =
      """
      {
        "status": 400,
        "success": false,
        "data": null,
        "error": {
          "code": "INVALID_FILTER",
          "message": "유효하지 않은 필터 값입니다. (all, day, week, month 중 하나)",
          "detail": null
        }
      }
      """;

  String ERR_INVALID_SORT =
      """
      {
        "status": 400,
        "success": false,
        "data": null,
        "error": {
          "code": "INVALID_SORT",
          "message": "유효하지 않은 정렬 값입니다. (priority, dueDate 중 하나)",
          "detail": null
        }
      }
      """;

  String ERR_INVALID_INPUT =
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
      """;

  String ERR_ROUTINE_TODO_USE_ROUTINE_API =
      """
      {
        "status": 400,
        "success": false,
        "data": null,
        "error": {
          "code": "ROUTINE_TODO_USE_ROUTINE_API",
          "message": "반복 todo는 루틴 API를 통해 수정해주세요.",
          "detail": null
        }
      }
      """;

  String ERR_ROUTINE_INVALID_TARGET =
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
      """;

  String ERR_ROUTINE_INVALID_DATE =
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
      """;

  String ERR_CANNOT_SKIP_COMPLETED =
      """
      {
        "status": 400,
        "success": false,
        "data": null,
        "error": {
          "code": "ROUTINE_OVERRIDE_CANNOT_SKIP_COMPLETED",
          "message": "이미 완료된 Todo가 있는 날짜는 건너뜀 처리할 수 없습니다.",
          "detail": null
        }
      }
      """;

  String ERR_OVERRIDE_SKIPPED =
      """
      {
        "status": 400,
        "success": false,
        "data": null,
        "error": {
          "code": "ROUTINE_OVERRIDE_SKIPPED",
          "message": "건너뛴 날짜에는 하위 투두를 추가할 수 없습니다.",
          "detail": null
        }
      }
      """;

  String ERR_TODO_NOT_FOUND =
      """
      {
        "status": 404,
        "success": false,
        "data": null,
        "error": {
          "code": "TODO_NOT_FOUND",
          "message": "투두를 찾을 수 없습니다.",
          "detail": null
        }
      }
      """;

  String ERR_ROUTINE_NOT_FOUND =
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
      """;

  String ERR_OVERRIDE_SUBTODO_NOT_FOUND =
      """
      {
        "status": 404,
        "success": false,
        "data": null,
        "error": {
          "code": "ROUTINE_OVERRIDE_SUBTODO_NOT_FOUND",
          "message": "예약된 하위 투두를 찾을 수 없습니다.",
          "detail": null
        }
      }
      """;

  String ERR_TAG_NOT_FOUND =
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
      """;

  @Operation(
      summary = "통합 아이템 목록 조회 (투두 + 루틴 회차)",
      description =
          """
          투두 목록과 루틴 회차 목록을 합쳐 한 배열로 반환한다. 두 목록은 서로 겹치지 않는다.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Query Parameters**

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | filter | ❌ 선택 | string | `all` | all / day / week / month | `day` |
          | sort | ❌ 선택 | string | `priority` | priority(정렬 순서) / dueDate(마감 빠른 순) | `priority` |
          | date | ❌ 선택 | string | 오늘 | 기준 날짜 (yyyy-MM-dd 형식) | `2026-07-10` |

          **Response Elements** (배열 원소)

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | kind | string | TODO / ROUTINE |
          | todoId | integer | 투두 ID. ROUTINE인데 null이면 아직 생성되지 않은 미래 회차 |
          | routineId | integer | 루틴 ID. TODO면 null |
          | date | string | 날짜 (yyyy-MM-dd 형식). all 필터의 "다음 할 일" 회차는 null일 수 있음 |
          | title | string | 제목 (ROUTINE이면 회차 예외 적용값) |
          | dueDate | string | 마감 일시 (ISO 8601 형식). 없으면 null |
          | repeatEndDate | string | 반복 종료일 (ISO 8601 형식). ROUTINE만, 없으면 null |
          | routineType | string | DAILY / WEEKLY / MONTHLY. TODO면 null |
          | routineDays | array | 반복 요일(월=0…일=6) 또는 일자(1~31) 배열. 없으면 null |
          | tagId | integer | 태그 ID. 없으면 null |
          | tagTitle | string | 태그 제목. 없으면 null |
          | tagColor | string | 태그 색상. 없으면 null |
          | goalId | integer | 목표 ID. 없으면 null |
          | sortOrder | number | 정렬 순서 |
          | isPinned | boolean | 핀 여부 |
          | isCompleted | boolean | 완료 여부 |
          | isOverdue | boolean | 마감 지남 여부 (미완료 + 마감 경과) |
          | hasOverride | boolean | 회차 예외 존재 여부. ROUTINE만 |

          **정렬**: 완료 아이템을 뒤로 보낸 뒤, sort 기준으로 정렬.

          **응답 아이템 구분**: `kind`가 `TODO`면 `todoId`로, `ROUTINE`이면 `routineId`+`date`로 이후 조작.
          `ROUTINE`인데 `todoId`가 null이면 아직 그날 투두가 생성되지 않은 미래 회차이다(조작 방법은 동일).
          핀 아이템은 별도 API 없이 `isPinned`로 구분한다.

          **주의**: `filter=all`에서는 루틴의 "다음 할 일" 회차에 `date`가 null일 수 있다(회차 날짜 미확정).
          이 경우 해당 아이템은 THIS 범위 조작(완료/핀 등)의 주소를 만들 수 없으므로 day/week/month 뷰에서 조작한다.
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
                              "data": [
                                {
                                  "kind": "TODO",
                                  "todoId": 42,
                                  "routineId": null,
                                  "date": "2026-07-10",
                                  "title": "회의 준비",
                                  "dueDate": "2026-07-10T18:00:00",
                                  "repeatEndDate": null,
                                  "routineType": null,
                                  "routineDays": null,
                                  "tagId": 3,
                                  "tagTitle": "업무",
                                  "tagColor": "BLUE",
                                  "goalId": null,
                                  "sortOrder": 10000.0,
                                  "isPinned": false,
                                  "isCompleted": false,
                                  "isOverdue": false,
                                  "hasOverride": false
                                },
                                {
                                  "kind": "ROUTINE",
                                  "todoId": null,
                                  "routineId": 7,
                                  "date": "2026-07-10",
                                  "title": "영어 단어 외우기",
                                  "dueDate": "2026-07-10T08:00:00",
                                  "repeatEndDate": "2026-12-31T00:00:00",
                                  "routineType": "WEEKLY",
                                  "routineDays": [0, 2, 4],
                                  "tagId": 1,
                                  "tagTitle": "영어",
                                  "tagColor": "GREEN",
                                  "goalId": null,
                                  "sortOrder": 5000.0,
                                  "isPinned": false,
                                  "isCompleted": false,
                                  "isOverdue": false,
                                  "hasOverride": false
                                }
                              ],
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "유효하지 않은 filter 또는 sort 값",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "filter 오류", value = ERR_INVALID_FILTER),
                  @ExampleObject(name = "sort 오류", value = ERR_INVALID_SORT)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                }))
  })
  ResponseEntity<ApiResult<List<ItemResponseDto>>> getItems(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "필터 (all/day/week/month)", example = "day")
          @RequestParam(defaultValue = "all")
          String filter,
      @Parameter(description = "정렬 (priority/dueDate)", example = "priority")
          @RequestParam(defaultValue = "priority")
          String sort,
      @Parameter(description = "기준 날짜 (yyyy-MM-dd 형식). 생략하면 오늘", example = "2026-07-10")
          @RequestParam(required = false)
          LocalDate date);

  @Operation(
      summary = "통합 아이템 상세 조회",
      description =
          """
          아이템 하나의 상세(하위 투두 포함)를 조회한다.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Query Parameters**

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | kind | ✅ 필수 | string | 없음 | TODO / ROUTINE | `ROUTINE` |
          | todoId | kind=TODO일 때 ✅ | integer | 없음 | 투두 ID | `42` |
          | routineId | kind=ROUTINE일 때 ✅ | integer | 없음 | 루틴 ID | `7` |
          | date | kind=ROUTINE일 때 ✅ | string | 없음 | 회차 날짜 (yyyy-MM-dd 형식) | `2026-07-10` |

          **Response Elements**

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | kind | string | TODO / ROUTINE |
          | todoId | integer | 투두 ID. ROUTINE이면 그날 투두가 생성된 경우에만 존재 |
          | routineId | integer | 루틴 ID. ROUTINE일 때만 |
          | date | string | 날짜 (yyyy-MM-dd 형식). ROUTINE=회차 날짜, TODO=마감일의 날짜 |
          | title | string | 제목 (ROUTINE이면 회차 예외 적용값) |
          | dueDate | string | 마감 일시 (ISO 8601 형식). TODO=본인 마감(없으면 null), ROUTINE=그날의 실제 마감일시(회차 예외 시간 > 루틴 기본 시간 > 23:59:59) |
          | isCompleted | boolean | 완료 여부 |
          | isPinned | boolean | 핀 여부. TODO 상세에서는 null |
          | isSkipped | boolean | 건너뜀 여부. ROUTINE만 |
          | hasOverride | boolean | 회차 예외 존재 여부. ROUTINE만 |
          | tagId | integer | 태그 ID (ROUTINE이면 회차 예외 적용값). 없으면 null |
          | tagTitle | string | 태그 제목. 없으면 null |
          | tagColor | string | 태그 색상. 없으면 null |
          | routineType | string | 반복 유형 (DAILY/WEEKLY/MONTHLY). 반복 아니면 null |
          | routineDays | array | 반복 날짜 배열. WEEKLY=요일 인덱스(월0…일6), MONTHLY=일자(1~31). 아니면 null |
          | routineTime | string | 루틴 기본 반복시간 (HH:mm:ss 형식). ROUTINE만, 설정 안 했으면 null |
          | repeatEndDate | string | 반복 종료일 (ISO 8601 형식). ROUTINE만 |
          | subTodos | array | 하위 투두 목록. 원소는 아래 표 참고 |

          **subTodos 원소**

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | todoId | integer | 하위 투두 ID. 행이 없는 회차의 하위(예정분·예약분)는 null |
          | title | string | 제목 |
          | isCompleted | boolean | 완료 여부 |
          | reservedIndex | integer | 예약 하위의 배열 위치(수정/삭제 시 지목용). 예약 하위가 아니면 null |
          | subRoutineId | integer | 하위 루틴 ID(반복 전체 수정/삭제 시 지목용). 하위 루틴과 무관한 하위면 null |

          **subTodos 원소 구분**: `todoId` 있음=행 하위(그날만 조작) / `reservedIndex` 있음=예약 하위(그날만 조작) /
          `subRoutineId`만 있음=하위 루틴 예정분(반복 전체로만 조작). 하위 루틴 출신 행 하위는 `todoId`와 `subRoutineId`가 둘 다 있어
          "그날만"과 "반복 전체" 조작을 모두 지원한다.

          **참고**: ROUTINE 상세의 `dueDate`는 그날의 실제 마감일시(회차 예외 반영), `routineTime`은 루틴의 기본 반복시간이다.
          둘을 비교하면 그날만 시간이 바뀌었는지 알 수 있다.
          행(Todo)이 아직 없는 미래 회차의 `subTodos`에는 하위 루틴 예정분(읽기 전용)과 예약된 하위가 병합되어 내려온다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "kind에 필요한 대상 정보 누락, 또는 하위 루틴 ID를 지정한 경우",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "대상 정보 누락", value = ERR_INVALID_INPUT),
                  @ExampleObject(name = "하위 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 루틴을 찾을 수 없음 (존재하지 않거나 본인 소유가 아님)",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<ItemDetailResponseDto>> getItemDetail(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "아이템 종류 (TODO/ROUTINE)", example = "ROUTINE") @RequestParam
          ItemKind kind,
      @Parameter(description = "투두 ID (kind=TODO일 때 필수)", example = "42")
          @RequestParam(required = false)
          Long todoId,
      @Parameter(description = "루틴 ID (kind=ROUTINE일 때 필수)", example = "7")
          @RequestParam(required = false)
          Long routineId,
      @Parameter(description = "회차 날짜 (yyyy-MM-dd 형식, kind=ROUTINE일 때 필수)", example = "2026-07-10")
          @RequestParam(required = false)
          LocalDate date);

  @Operation(
      summary = "통합 아이템 완료/미완료 처리",
      description =
          """
          TODO는 투두 완료 처리, ROUTINE은 해당 날짜 회차만 완료 처리(override)한다. 범위 선택(scope) 없음 — 완료는 항상 그 회차만.

          - kind=TODO → 기존 `PATCH /api/todos/{id}/complete`와 동일 동작
          - kind=ROUTINE → 기존 `PATCH /api/routines/{routineId}/overrides/{date}/complete`와 동일 동작 (그날 투두가 이미 있으면 함께 갱신)

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | kind | ✅ 필수 | string | TODO / ROUTINE | `"TODO"` |
          | todoId | kind=TODO일 때 ✅ | integer | 투두 ID | `42` |
          | routineId | kind=ROUTINE일 때 ✅ | integer | 루틴 ID | `7` |
          | date | kind=ROUTINE일 때 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-10"` |
          | isCompleted | ✅ 필수 | boolean | true면 완료, false면 미완료 | `true` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "투두 완료",
                    value =
                        """
                        {"kind": "TODO", "todoId": 42, "isCompleted": true}
                        """),
                @ExampleObject(
                    name = "루틴 회차 완료",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "date": "2026-07-10", "isCompleted": true}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상 정보 누락 / 반복에 연결된 투두를 TODO로 지목 / 하위 루틴 ID 지정 / 발생하지 않는 날짜",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "대상 정보 누락", value = ERR_INVALID_INPUT),
                  @ExampleObject(
                      name = "반복 연결 투두를 TODO로 지목",
                      value = ERR_ROUTINE_TODO_USE_ROUTINE_API),
                  @ExampleObject(name = "하위 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET),
                  @ExampleObject(
                      name = "발생하지 않는 날짜(요일/일자 불일치, 종료일 이후)",
                      value = ERR_ROUTINE_INVALID_DATE)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 루틴을 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> completeItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemCompleteRequestDto request);

  @Operation(
      summary = "통합 아이템 핀 설정/해제",
      description =
          """
          TODO는 투두 핀 처리, ROUTINE은 해당 날짜 회차만 핀 처리(override)한다. 범위 선택(scope) 없음.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | kind | ✅ 필수 | string | TODO / ROUTINE | `"TODO"` |
          | todoId | kind=TODO일 때 ✅ | integer | 투두 ID | `42` |
          | routineId | kind=ROUTINE일 때 ✅ | integer | 루틴 ID | `7` |
          | date | kind=ROUTINE일 때 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-10"` |
          | isPinned | ✅ 필수 | boolean | true면 핀, false면 언핀 | `true` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "투두 핀",
                    value =
                        """
                        {"kind": "TODO", "todoId": 42, "isPinned": true}
                        """),
                @ExampleObject(
                    name = "루틴 회차 핀",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "date": "2026-07-10", "isPinned": true}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상 정보 누락 / 반복에 연결된 투두를 TODO로 지목 / 하위 루틴 ID 지정 / 발생하지 않는 날짜",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "대상 정보 누락", value = ERR_INVALID_INPUT),
                  @ExampleObject(
                      name = "반복 연결 투두를 TODO로 지목",
                      value = ERR_ROUTINE_TODO_USE_ROUTINE_API),
                  @ExampleObject(name = "하위 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET),
                  @ExampleObject(
                      name = "발생하지 않는 날짜(요일/일자 불일치, 종료일 이후)",
                      value = ERR_ROUTINE_INVALID_DATE)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 루틴을 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> pinItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemPinRequestDto request);

  @Operation(
      summary = "통합 아이템 정렬 변경 (드래그)",
      description =
          """
          합쳐진 목록에서의 드래그 정렬. 프론트가 화면상 앞뒤 아이템의 sortOrder 중간값을 계산해 보낸다.

          - 이웃 아이템이 아직 투두로 생성되지 않은 루틴 회차(id 없음)일 수 있어, 이웃 id 방식이 아니라 순서값(sortOrder)을 직접 받는다.
          - kind=TODO → 투두의 sortOrder를 직접 갱신 (신규 동작)
          - kind=ROUTINE → 기존 `PATCH /api/routines/{routineId}/overrides/{date}/order`와 동일 동작

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | kind | ✅ 필수 | string | TODO / ROUTINE | `"TODO"` |
          | todoId | kind=TODO일 때 ✅ | integer | 투두 ID | `42` |
          | routineId | kind=ROUTINE일 때 ✅ | integer | 루틴 ID | `7` |
          | date | kind=ROUTINE일 때 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-10"` |
          | sortOrder | ✅ 필수 | number | 새 정렬 순서 (앞뒤 아이템 sortOrder의 중간값) | `5000.0` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "투두 정렬 변경",
                    value =
                        """
                        {"kind": "TODO", "todoId": 42, "sortOrder": 5000.0}
                        """),
                @ExampleObject(
                    name = "루틴 회차 정렬 변경",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "date": "2026-07-10", "sortOrder": 5000.0}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상 정보 누락 / 반복에 연결된 투두를 TODO로 지목 / 하위 루틴 ID 지정 / 발생하지 않는 날짜",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "대상 정보 누락", value = ERR_INVALID_INPUT),
                  @ExampleObject(
                      name = "반복 연결 투두를 TODO로 지목",
                      value = ERR_ROUTINE_TODO_USE_ROUTINE_API),
                  @ExampleObject(name = "하위 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET),
                  @ExampleObject(
                      name = "발생하지 않는 날짜(요일/일자 불일치, 종료일 이후)",
                      value = ERR_ROUTINE_INVALID_DATE)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 루틴을 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> reorderItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemOrderRequestDto request);

  @Operation(
      summary = "통합 아이템 내용 수정",
      description =
          """
          **kind=TODO** (todoId 필수): 기존 `PUT /api/todos/{id}`와 동일 — title은 null이면 유지,
          dueDate/tagId는 null이면 제거. 반복 필드(routineType 등)를 주면 투두가 반복으로 전환된다.

          **kind=ROUTINE + scope=THIS** (routineId·date 필수): 그 날짜 회차만 title/tagId/routineTime 수정.
          (routineTime은 그 회차만의 마감시간이 됨.) null인 필드는 루틴 기본값 유지.
          기존 `PATCH /api/routines/{routineId}/overrides/{date}`와 동일.

          **kind=ROUTINE + scope=ALL** (routineId 필수): 반복 전체(엄마 루틴) 수정. title·routineType 필수.
          기존 `PUT /api/routines/{id}`와 동일 — 오늘 이후의 회차 예외(override)가 모두 삭제되고 새 값으로 통일된다.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | kind | ✅ 필수 | string | TODO / ROUTINE | `"TODO"` |
          | todoId | kind=TODO일 때 ✅ | integer | 투두 ID | `42` |
          | routineId | kind=ROUTINE일 때 ✅ | integer | 루틴 ID | `7` |
          | date | ROUTINE+THIS일 때 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-10"` |
          | scope | kind=ROUTINE일 때 ✅ | string | THIS(이 회차만) / ALL(반복 전체) | `"THIS"` |
          | title | ROUTINE+ALL일 때 ✅, 그 외 ❌ 선택 | string | 제목 | `"영어 단어 100개 외우기"` |
          | dueDate | ❌ 선택 | string | 마감 일시 (ISO 8601 형식, TODO 전용). null이면 마감일 제거 | `"2026-07-10T18:00:00"` |
          | tagId | ❌ 선택 | integer | 태그 ID. null이면 태그 제거(TODO/전체수정) 또는 기본값 유지(회차수정) | `5` |
          | routineType | ROUTINE+ALL일 때 ✅, 그 외 ❌ 선택 | string | DAILY / WEEKLY / MONTHLY | `"WEEKLY"` |
          | routineDays | ❌ 선택 | array | 반복 요일(월=0…일=6) 또는 일자(1~31) 배열 | `[0, 2, 4]` |
          | routineTime | ❌ 선택 | string | 반복 시각 (HH:mm:ss). ROUTINE+THIS일 땐 그 회차만의 마감시간 | `"19:00:00"` |
          | repeatEndDate | ❌ 선택 | string | 반복 종료일 (ISO 8601 형식, ROUTINE+ALL 전용). null이면 종료일 제거 | `"2026-12-31T00:00:00"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "투두 수정 (전체 필드)",
                    value =
                        """
                        {"kind": "TODO", "todoId": 42, "title": "회의 준비", "dueDate": "2026-07-10T18:00:00", "tagId": 3}
                        """),
                @ExampleObject(
                    name = "루틴 이 회차만 수정 (제목/태그/시간)",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "date": "2026-07-10", "scope": "THIS", "title": "영어 단어 (특별)", "routineTime": "19:00:00"}
                        """),
                @ExampleObject(
                    name = "루틴 전체 수정 (필수 필드만)",
                    summary = "optional 필드를 생략하면 null로 처리됨",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "scope": "ALL", "title": "영어 단어 외우기", "routineType": "DAILY"}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(
        responseCode = "400",
        description =
            "대상/scope 누락, ALL인데 title·routineType 없음, 제목이 빈 문자열, 반복 날짜 배열이 유형과 안 맞음,"
                + " 반복에 연결된 투두를 TODO로 지목, 하위 루틴 ID 지정",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "대상/scope/필수값 누락 또는 빈 제목", value = ERR_INVALID_INPUT),
                  @ExampleObject(name = "반복 날짜 배열이 유형과 안 맞음", value = ERR_ROUTINE_INVALID_DATE),
                  @ExampleObject(
                      name = "반복 연결 투두를 TODO로 지목",
                      value = ERR_ROUTINE_TODO_USE_ROUTINE_API),
                  @ExampleObject(name = "하위 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두/루틴/태그를 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND),
                  @ExampleObject(name = "태그 없음", value = ERR_TAG_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> updateItemContent(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemContentRequestDto request);

  @Operation(
      summary = "통합 아이템 삭제",
      description =
          """
          **kind=TODO** (todoId 필수): 투두 삭제 (하위 투두 포함). 기존 `DELETE /api/todos/{id}`와 동일.

          **kind=ROUTINE + scope=THIS** (routineId·date 필수): 그 날짜 회차만 건너뛰기(skip).
          이미 완료된 회차는 건너뛸 수 없다. 기존 `DELETE /api/routines/{routineId}/overrides/{date}`와 동일.

          **kind=ROUTINE + scope=ALL** (routineId 필수): 반복 전체(엄마 루틴 + 하위 루틴) 삭제.
          기존 `DELETE /api/routines/{id}`와 동일.

          **요청 본문 주의**: DELETE지만 본문(body)이 필수다. axios는 `axios.delete(url, { data: {...} })`처럼
          설정 객체의 `data`로 본문을 전달해야 한다.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | kind | ✅ 필수 | string | TODO / ROUTINE | `"TODO"` |
          | todoId | kind=TODO일 때 ✅ | integer | 투두 ID | `42` |
          | routineId | kind=ROUTINE일 때 ✅ | integer | 루틴 ID | `7` |
          | date | ROUTINE+THIS일 때 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-10"` |
          | scope | kind=ROUTINE일 때 ✅ | string | THIS(이 회차만 건너뛰기) / ALL(반복 전체 삭제) | `"THIS"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "투두 삭제",
                    value =
                        """
                        {"kind": "TODO", "todoId": 42}
                        """),
                @ExampleObject(
                    name = "루틴 이 회차만 건너뛰기",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "date": "2026-07-10", "scope": "THIS"}
                        """),
                @ExampleObject(
                    name = "루틴 반복 전체 삭제",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "scope": "ALL"}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상/scope 누락, 이미 완료된 회차를 건너뛰려 한 경우, 반복에 연결된 투두를 TODO로 지목, 하위 루틴 ID 지정",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "대상/scope 누락", value = ERR_INVALID_INPUT),
                  @ExampleObject(name = "완료된 회차 건너뛰기 시도", value = ERR_CANNOT_SKIP_COMPLETED),
                  @ExampleObject(
                      name = "반복 연결 투두를 TODO로 지목",
                      value = ERR_ROUTINE_TODO_USE_ROUTINE_API),
                  @ExampleObject(name = "하위 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET),
                  @ExampleObject(
                      name = "발생하지 않는 날짜(요일/일자 불일치, 종료일 이후)",
                      value = ERR_ROUTINE_INVALID_DATE)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 루틴을 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> deleteItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemDeleteRequestDto request);

  @Operation(
      summary = "통합 아이템 하위 투두 추가",
      description =
          """
          **kind=TODO** (todoId 필수): 그 투두에 하위 투두 추가. 기존 `POST /api/todos/{parentId}/sub-todos`와 동일.

          **kind=ROUTINE + scope=THIS** (routineId·date 필수): 그 날짜 회차에만 하위 투두 추가.
          - 그날 행(Todo)이 이미 있으면 행에 바로 생성된다.
          - 아직 없으면(미래 회차) 회차 예외에 **예약**해 뒀다가 배치가 행을 만들 때 실체화한다.
          - 예약 하위는 상세 응답 `subTodos`에 `reservedIndex`가 채워진 항목으로 내려온다.

          **kind=ROUTINE + scope=ALL** (routineId 필수): **하위 루틴 생성** — 이후 모든 회차에 반복되는 하위.
          기존 `POST /api/routines/{parentId}/children`과 동일.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | kind | ✅ 필수 | string | TODO / ROUTINE | `"ROUTINE"` |
          | todoId | kind=TODO일 때 ✅ | integer | 투두 ID | `42` |
          | routineId | kind=ROUTINE일 때 ✅ | integer | 루틴 ID | `7` |
          | date | ROUTINE+THIS일 때 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-20"` |
          | scope | kind=ROUTINE일 때 ✅ | string | THIS(그 날짜 회차만) / ALL(하위 루틴 생성) | `"THIS"` |
          | title | ✅ 필수 | string | 하위 투두 제목 | `"단어 50개 외우기"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "투두에 하위 추가",
                    value =
                        """
                        {"kind": "TODO", "todoId": 42, "title": "단어 50개 외우기"}
                        """),
                @ExampleObject(
                    name = "루틴 그 날짜 회차에만 추가 (미래 회차면 예약)",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "date": "2026-07-20", "scope": "THIS", "title": "단어 50개 외우기"}
                        """),
                @ExampleObject(
                    name = "하위 루틴 생성 (모든 회차에 반복)",
                    value =
                        """
                        {"kind": "ROUTINE", "routineId": 7, "scope": "ALL", "title": "매일 스트레칭"}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "추가 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상 누락/빈 제목, 발생하지 않는 날짜, 건너뛴 날짜",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "대상 누락/빈 제목", value = ERR_INVALID_INPUT),
                  @ExampleObject(
                      name = "발생하지 않는 날짜(요일/일자 불일치, 종료일 이후)",
                      value = ERR_ROUTINE_INVALID_DATE),
                  @ExampleObject(name = "건너뛴 날짜", value = ERR_OVERRIDE_SKIPPED)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 루틴을 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> addItemSubTodo(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ItemSubTodoCreateRequestDto request);

  @Operation(
      summary = "통합 아이템 하위 투두 제목 수정",
      description =
          """
          하위 투두의 제목을 수정한다. 아래 세 가지 지목 방법 중 정확히 하나를 사용한다.

          - **행 하위 (그날만)**: `parentTodoId` + `subTodoId` — 기존 `PUT /api/todos/{parentId}/sub-todos/{subTodoId}`와 동일
          - **예약 하위 (그날만, 행이 아직 없는 회차)**: `routineId` + `date` + `index` (상세 응답 `subTodos`의 `reservedIndex`)
          - **하위 루틴 (반복 전체)**: `subRoutineId` (상세 응답 `subTodos`의 `subRoutineId`) — 기존 `PATCH /api/routines/children/{id}`와 동일

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | parentTodoId | 행 하위 지목 시 ✅ | integer | 부모 투두 ID | `42` |
          | subTodoId | 행 하위 지목 시 ✅ | integer | 하위 투두 ID | `128` |
          | routineId | 예약 하위 지목 시 ✅ | integer | 루틴 ID | `7` |
          | date | 예약 하위 지목 시 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-20"` |
          | index | 예약 하위 지목 시 ✅ | integer | 예약 배열 위치 (상세 응답의 reservedIndex) | `0` |
          | subRoutineId | 반복 전체 지목 시 ✅ | integer | 하위 루틴 ID (상세 응답의 subRoutineId) | `11` |
          | title | ✅ 필수 | string | 새 제목 | `"단어 100개 외우기"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "행 하위 수정 (그날만)",
                    value =
                        """
                        {"parentTodoId": 42, "subTodoId": 128, "title": "단어 100개 외우기"}
                        """),
                @ExampleObject(
                    name = "예약 하위 수정 (그날만, 미래 회차)",
                    value =
                        """
                        {"routineId": 7, "date": "2026-07-20", "index": 0, "title": "단어 100개 외우기"}
                        """),
                @ExampleObject(
                    name = "하위 루틴 수정 (반복 전체)",
                    value =
                        """
                        {"subRoutineId": 11, "title": "유산소 30분"}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "빈 제목, 지목 필드 누락, 엄마 루틴 ID를 subRoutineId로 지정",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "빈 제목/지목 누락", value = ERR_INVALID_INPUT),
                  @ExampleObject(name = "엄마 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "대상 없음 — 투두/루틴/예약(index 범위 밖)",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND),
                  @ExampleObject(name = "예약 하위 없음", value = ERR_OVERRIDE_SUBTODO_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> updateItemSubTodo(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ItemSubTodoUpdateRequestDto request);

  @Operation(
      summary = "통합 아이템 하위 투두 삭제",
      description =
          """
          하위 투두를 삭제한다. 아래 세 가지 지목 방법 중 정확히 하나를 사용한다.

          - **행 하위 (그날만)**: `parentTodoId` + `subTodoId` — 기존 `DELETE /api/todos/{parentId}/sub-todos/{subTodoId}`와 동일
          - **예약 하위 (그날만, 행이 아직 없는 회차)**: `routineId` + `date` + `index` (상세 응답 `subTodos`의 `reservedIndex`)
          - **하위 루틴 (반복 전체 — 이후 모든 회차에서 사라짐)**: `subRoutineId` — 기존 `DELETE /api/routines/children/{id}`와 동일

          **요청 본문 주의**: DELETE지만 본문(body)이 필수다. axios는 `axios.delete(url, { data: {...} })`처럼
          설정 객체의 `data`로 본문을 전달해야 한다.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | parentTodoId | 행 하위 지목 시 ✅ | integer | 부모 투두 ID | `42` |
          | subTodoId | 행 하위 지목 시 ✅ | integer | 하위 투두 ID | `128` |
          | routineId | 예약 하위 지목 시 ✅ | integer | 루틴 ID | `7` |
          | date | 예약 하위 지목 시 ✅ | string | 회차 날짜 (yyyy-MM-dd 형식) | `"2026-07-20"` |
          | index | 예약 하위 지목 시 ✅ | integer | 예약 배열 위치 (상세 응답의 reservedIndex) | `0` |
          | subRoutineId | 반복 전체 지목 시 ✅ | integer | 하위 루틴 ID (상세 응답의 subRoutineId) | `11` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "행 하위 삭제 (그날만)",
                    value =
                        """
                        {"parentTodoId": 42, "subTodoId": 128}
                        """),
                @ExampleObject(
                    name = "예약 하위 삭제 (그날만, 미래 회차)",
                    value =
                        """
                        {"routineId": 7, "date": "2026-07-20", "index": 0}
                        """),
                @ExampleObject(
                    name = "하위 루틴 삭제 (반복 전체)",
                    value =
                        """
                        {"subRoutineId": 11}
                        """)
              }))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "지목 필드 누락, 엄마 루틴 ID를 subRoutineId로 지정",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "지목 누락", value = ERR_INVALID_INPUT),
                  @ExampleObject(name = "엄마 루틴 ID 지정", value = ERR_ROUTINE_INVALID_TARGET)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "토큰 없음", value = ERR_EMPTY_TOKEN),
                  @ExampleObject(name = "만료된 토큰", value = ERR_EXPIRED_TOKEN)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "대상 없음 — 투두/루틴/예약(index 범위 밖)",
        content =
            @Content(
                examples = {
                  @ExampleObject(name = "투두 없음", value = ERR_TODO_NOT_FOUND),
                  @ExampleObject(name = "루틴 없음", value = ERR_ROUTINE_NOT_FOUND),
                  @ExampleObject(name = "예약 하위 없음", value = ERR_OVERRIDE_SUBTODO_NOT_FOUND)
                }))
  })
  ResponseEntity<ApiResult<Void>> deleteItemSubTodo(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ItemSubTodoDeleteRequestDto request);
}
