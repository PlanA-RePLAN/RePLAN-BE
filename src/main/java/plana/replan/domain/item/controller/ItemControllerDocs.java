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
import plana.replan.global.common.ApiResult;

@Tag(
    name = "Item",
    description =
        """
        홈 화면용 통합 아이템 API. 일회성 투두와 반복 루틴의 날짜별 회차를 "아이템" 하나의 개념으로 다룬다.

        - 아이템 구분: `kind` = `TODO`(일회성 투두) / `ROUTINE`(루틴의 특정 날짜 회차)
        - 아이템 주소: TODO는 `todoId`, ROUTINE은 `routineId` + `date`
        - ROUTINE 수정/삭제 범위: `scope` = `THIS`(이 회차만) / `ALL`(반복 전체)
        - 기존 투두/루틴 API를 감싸는 창구이며, 동작 규칙(검증·에러)은 기존 API와 동일하다.
        """)
public interface ItemControllerDocs {

  @Operation(
      summary = "통합 아이템 목록 조회 (투두 + 루틴 회차)",
      description =
          """
          투두 목록과 루틴 회차 목록을 합쳐 한 배열로 반환한다. 두 목록은 서로 겹치지 않는다.

          **Query Parameters**

          | 이름 | 필수 | 타입 | 설명 |
          |------|------|------|------|
          | filter | ❌ (기본 all) | string | all / day / week / month |
          | sort | ❌ (기본 priority) | string | priority(정렬 순서) / dueDate(마감 빠른 순) |
          | date | ❌ (기본 오늘) | string(yyyy-MM-dd) | 기준 날짜 |

          **정렬**: 완료 아이템을 뒤로 보낸 뒤, sort 기준으로 정렬.

          **응답 아이템 구분**: `kind`가 `TODO`면 `todoId`로, `ROUTINE`이면 `routineId`+`date`로 이후 조작.
          `ROUTINE`인데 `todoId`가 null이면 아직 그날 투두가 생성되지 않은 미래 회차이다(조작 방법은 동일).
          핀 아이템은 별도 API 없이 `isPinned`로 구분한다.
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
                examples =
                    @ExampleObject(
                        value =
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
                            """)))
  })
  ResponseEntity<ApiResult<List<ItemResponseDto>>> getItems(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "all") String filter,
      @RequestParam(defaultValue = "priority") String sort,
      @RequestParam(required = false) LocalDate date);

  @Operation(
      summary = "통합 아이템 상세 조회",
      description =
          """
          아이템 하나의 상세(하위 투두 포함)를 조회한다.

          **Query Parameters**

          | 이름 | 필수 | 타입 | 설명 |
          |------|------|------|------|
          | kind | ✅ | string | TODO / ROUTINE |
          | todoId | kind=TODO일 때 ✅ | number | 투두 ID |
          | routineId | kind=ROUTINE일 때 ✅ | number | 루틴 ID |
          | date | kind=ROUTINE일 때 ✅ | string(yyyy-MM-dd) | 회차 날짜 |

          **참고**: ROUTINE 상세의 반복 유형/요일 정보는 목록 응답에 이미 포함돼 있어 여기서는 null로 반환된다.
          ROUTINE 회차의 하위 아이템은 그날 투두가 이미 생성된 경우에만 존재한다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "kind에 필요한 대상 정보 누락 (TODO인데 todoId 없음 / ROUTINE인데 routineId·date 없음)",
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
                                "message": "입력값이 올바르지 않습니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 루틴을 찾을 수 없음 (존재하지 않거나 본인 소유가 아님)",
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
                                "code": "TODO_NOT_FOUND",
                                "message": "투두를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<ItemDetailResponseDto>> getItemDetail(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "아이템 종류 (TODO/ROUTINE)") @RequestParam ItemKind kind,
      @RequestParam(required = false) Long todoId,
      @RequestParam(required = false) Long routineId,
      @RequestParam(required = false) LocalDate date);

  @Operation(
      summary = "통합 아이템 완료/미완료 처리",
      description =
          """
          TODO는 투두 완료 처리, ROUTINE은 해당 날짜 회차만 완료 처리(override)한다. 범위 선택(scope) 없음 — 완료는 항상 그 회차만.

          - kind=TODO → 기존 `PATCH /api/todos/{id}/complete`와 동일 동작
          - kind=ROUTINE → 기존 `PATCH /api/routines/{routineId}/overrides/{date}/complete`와 동일 동작 (그날 투두가 이미 있으면 함께 갱신)
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상 정보 누락 또는 반복에 연결된 투두를 TODO로 지목한 경우",
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
                                "code": "ROUTINE_TODO_USE_ROUTINE_API",
                                "message": "반복 todo는 루틴 API를 통해 수정해주세요.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(responseCode = "404", description = "투두 또는 루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<Void>> completeItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemCompleteRequestDto request);

  @Operation(
      summary = "통합 아이템 핀 설정/해제",
      description =
          """
          TODO는 투두 핀 처리, ROUTINE은 해당 날짜 회차만 핀 처리(override)한다. 범위 선택(scope) 없음.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(responseCode = "400", description = "대상 정보 누락 또는 반복 연결 투두를 TODO로 지목"),
    @ApiResponse(responseCode = "404", description = "투두 또는 루틴을 찾을 수 없음")
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
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(responseCode = "400", description = "대상 정보 누락 또는 반복 연결 투두를 TODO로 지목"),
    @ApiResponse(responseCode = "404", description = "투두 또는 루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<Void>> reorderItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemOrderRequestDto request);

  @Operation(
      summary = "통합 아이템 내용 수정",
      description =
          """
          **kind=TODO** (todoId 필수): 기존 `PUT /api/todos/{id}`와 동일 — title은 null이면 유지,
          dueDate/tagId는 null이면 제거. 반복 필드(routineType 등)를 주면 투두가 반복으로 전환된다.

          **kind=ROUTINE + scope=THIS** (routineId·date 필수): 그 날짜 회차만 title/tagId 수정.
          null인 필드는 루틴 기본값 유지. 기존 `PATCH /api/routines/{routineId}/overrides/{date}`와 동일.

          **kind=ROUTINE + scope=ALL** (routineId 필수): 반복 전체(엄마 루틴) 수정. title·routineType 필수.
          기존 `PUT /api/routines/{id}`와 동일 — 오늘 이후의 회차 예외(override)가 모두 삭제되고 새 값으로 통일된다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상/scope 누락, ALL인데 title·routineType 없음, 반복 날짜 배열이 유형과 안 맞음 등"),
    @ApiResponse(responseCode = "404", description = "투두/루틴/태그를 찾을 수 없음")
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
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "대상/scope 누락, 이미 완료된 회차를 건너뛰려 한 경우 등",
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
                                "code": "ROUTINE_OVERRIDE_CANNOT_SKIP_COMPLETED",
                                "message": "이미 완료된 회차는 건너뛸 수 없습니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(responseCode = "404", description = "투두 또는 루틴을 찾을 수 없음")
  })
  ResponseEntity<ApiResult<Void>> deleteItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemDeleteRequestDto request);
}
