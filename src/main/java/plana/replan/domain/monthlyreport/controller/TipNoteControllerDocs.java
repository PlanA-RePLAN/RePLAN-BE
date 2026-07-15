package plana.replan.domain.monthlyreport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyRequest;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyResponse;
import plana.replan.domain.monthlyreport.dto.TipNoteResponse;
import plana.replan.global.common.ApiResult;

@Tag(name = "TipNote (통계 팁노트)", description = "통계 탭 팁노트 — AI 투두리스트 제안 조회/반영/끝내기")
public interface TipNoteControllerDocs {

  @Operation(
      summary = "팁노트 조회",
      description =
          """
          해당 월 통계를 근거로 만들어진 팁노트(투두리스트 작성 팁 + 추천 투두 카드)를 조회합니다.
          팁노트는 매월 1일 통계 배치에서 지난달 데이터로 자동 생성됩니다.

          **추천 카드(items)가 내려오는 조건** — 아래를 전부 만족하는 카드만 내려갑니다:
          1. 조회한 달이 이 유저의 **가장 최근 팁노트**일 것 (지난 달들은 `items: []`, 작성 팁만)
          2. 아직 처리 안 된 카드일 것 (반영했거나 "반영 없이 끝내기"로 접은 카드 제외)
          3. 기한이 안 지났을 것 — 새 투두 카드는 마감일시, 루틴 카드는 반복 종료일 기준
          4. 루틴 수정 카드는 대상 루틴이 살아있고, 팁노트 생성 후 사용자가 직접 수정하지 않았을 것

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Query Parameters

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | year  | ✅ 필수 | integer | 없음 | 조회할 연도 | `2026` |
          | month | ✅ 필수 | integer | 없음 | 조회할 월 (1~12) | `6` |

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | noteId | integer | 팁노트 ID. 반영/끝내기 요청에 사용 |
          | year | integer | 팁노트가 근거로 삼은 연도 |
          | month | integer | 팁노트가 근거로 삼은 월 |
          | tip | string | 투두리스트 작성 팁 카드 텍스트 |
          | items | array | 추천 투두 카드 목록. 최신 팁노트가 아니면 항상 빈 배열 |
          | items[].id | integer | 카드 ID. 반영 요청의 itemIds에 사용 |
          | items[].action | string | `ADD_TODO`(새 일반 투두) / `ADD_ROUTINE`(새 루틴) / `MODIFY_ROUTINE`(기존 루틴 수정) |
          | items[].title | string | 제목 (수정 카드는 수정 후 제목) |
          | items[].tagName | string | 태그 이름. 없으면 null |
          | items[].tagColor | string | 태그 색상. 없으면 null |
          | items[].todoDueAt | string | 새 일반 투두의 마감일시 (ISO 8601 형식). ADD_TODO에만 값이 있음 |
          | items[].routineEndAt | string | 루틴 반복 종료일 (ISO 8601 형식). 루틴 카드 전용, 무기한이면 null |
          | items[].routineTime | string | 루틴 반복 시각 (HH:mm:ss). 루틴 카드 전용 |
          | items[].routineType | string | `DAILY`/`WEEKLY`/`MONTHLY`. 루틴 카드 전용 |
          | items[].routineDays | array | 반복 날짜. WEEKLY=요일 인덱스(월=0…일=6), MONTHLY=일자(1~31), DAILY=null |
          | items[].changedFields | array | 수정 카드의 변경 내역(변경된 필드만: field/before/after). 추가 카드는 빈 배열 |

          ---

          ### 주의사항
          - 지난달 활동이 없어 팁노트가 만들어지지 않았으면 404 (`TIP_NOTE_NOT_FOUND`) → "아직 데이터가 없어요" 화면
          - 카드의 기한이 지나면 조회 시점부터 자동으로 목록에서 사라집니다 (별도 요청 불필요)
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                schema = @Schema(implementation = TipNoteResponse.class),
                examples = {
                  @ExampleObject(
                      name = "최신 팁노트 (추천 카드 포함)",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "noteId": 17,
                              "year": 2026,
                              "month": 6,
                              "tip": "과부하 + 컨디션 저하 + 계획력 부족이 지난달의 주된 패턴이에요. 다음 달에는 무리하지 않고, 집중도와 회복력을 높이는 구조의 투두리스트를 제안합니다.",
                              "items": [
                                {
                                  "id": 3,
                                  "action": "ADD_ROUTINE",
                                  "title": "11시 이전 취침",
                                  "tagName": "Study",
                                  "tagColor": "#FF5733",
                                  "todoDueAt": null,
                                  "routineEndAt": null,
                                  "routineTime": "23:00:00",
                                  "routineType": "DAILY",
                                  "routineDays": null,
                                  "changedFields": []
                                },
                                {
                                  "id": 4,
                                  "action": "MODIFY_ROUTINE",
                                  "title": "모의고사 1회분 풀이",
                                  "tagName": "Study",
                                  "tagColor": "#FF5733",
                                  "todoDueAt": null,
                                  "routineEndAt": null,
                                  "routineTime": "11:00:00",
                                  "routineType": "MONTHLY",
                                  "routineDays": [15],
                                  "changedFields": [
                                    { "field": "title", "before": "모의고사 풀이", "after": "모의고사 1회분 풀이" },
                                    { "field": "routineTime", "before": "10:00", "after": "11:00" },
                                    { "field": "routineType", "before": "WEEKLY", "after": "MONTHLY" },
                                    { "field": "routineDays", "before": "[5]", "after": "[15]" },
                                    { "field": "tag", "before": "Project", "after": "Study" }
                                  ]
                                }
                              ]
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "지난 달 팁노트 (작성 팁만)",
                      summary = "최신이 아닌 달은 items가 항상 빈 배열",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "noteId": 12,
                              "year": 2026,
                              "month": 5,
                              "tip": "5월에는 마감 직전 몰아치기 패턴이 반복됐어요. 마감을 하루 앞당겨 잡는 구조를 제안했습니다.",
                              "items": []
                            },
                            "error": null
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "요청 파라미터 오류 (year/month 누락 또는 범위 밖)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {"status":400,"success":false,"data":null,"error":{"code":"INVALID_INPUT","message":"요청 값이 올바르지 않습니다.","detail":null}}
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
        description = "팁노트 없음(지난달 활동 없음) 또는 유저 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "팁노트 없음",
                      summary = "\"아직 데이터가 없어요\" 화면으로 처리",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"TIP_NOTE_NOT_FOUND","message":"해당 월의 팁노트가 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "유저 없음",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"리소스를 찾을 수 없습니다.","detail":null}}
                          """)
                }))
  })
  ResponseEntity<ApiResult<TipNoteResponse>> getTipNote(
      Long userId,
      @Parameter(name = "year", description = "조회할 연도", example = "2026", required = true) int year,
      @Parameter(name = "month", description = "조회할 월 (1~12)", example = "6", required = true)
          int month);

  @Operation(
      summary = "팁노트 반영 (투두 반영하기)",
      description =
          """
          체크한 추천 카드만 실제 투두/루틴에 반영합니다. 카드 종류별 동작:
          - `ADD_TODO`: 새 일반 투두 생성 (투두 생성 API와 동일한 처리)
          - `ADD_ROUTINE`: 새 루틴 생성 + 가까운 회차 투두까지 자동 생성 (루틴 생성 API와 동일한 처리)
          - `MODIFY_ROUTINE`: 기존 루틴을 카드의 "수정 후 모습"으로 변경 (루틴 수정 API와 동일한 처리)

          반영된 카드는 다음 조회부터 목록에서 빠지고, 체크하지 않은 카드는 남아서 다음에 다시 보입니다.

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
          | noteId | ✅ 필수 | integer | 팁노트 ID (조회 응답의 noteId) | `17` |

          ---

          ### Request Body

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | itemIds | ✅ 필수 | array | 반영할 카드 ID 목록 (조회 응답의 items[].id). 1개 이상 | `[3, 5]` |

          ---

          ### 주의사항
          - **가장 최근 팁노트만** 반영할 수 있습니다. 지난 달 팁노트면 400 (`TIP_NOTE_NOT_LATEST`)
          - 조회에서 숨겨지는 카드(이미 처리됨/기한 지남/루틴 삭제·직접 수정)는 반영도 거부됩니다 (`TIP_NOTE_ITEM_NOT_APPLICABLE`)
          - 여러 카드 중 하나라도 실패하면 **전체가 반영되지 않습니다** (전부 성공 또는 전부 취소)
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "반영 성공 — 반영된 카드 목록 반환 (\"내 Todo 리스트에 추가했어요\" 화면용)",
        content =
            @Content(
                schema = @Schema(implementation = TipNoteApplyResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "appliedItems": [
                                  {
                                    "id": 3,
                                    "action": "ADD_ROUTINE",
                                    "title": "11시 이전 취침",
                                    "tagName": "Study",
                                    "tagColor": "#FF5733",
                                    "todoDueAt": null,
                                    "routineEndAt": null,
                                    "routineTime": "23:00:00",
                                    "routineType": "DAILY",
                                    "routineDays": null,
                                    "changedFields": []
                                  }
                                ]
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "반영 불가 — 카드 미선택 / 최신 팁노트 아님 / 처리·만료된 카드 / 반복 날짜 오류",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "카드 미선택",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"INVALID_INPUT","message":"반영할 카드를 1개 이상 선택해야 합니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "최신 팁노트 아님",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"TIP_NOTE_NOT_LATEST","message":"가장 최근 팁노트에서만 반영하거나 끝낼 수 있습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "처리됐거나 기한 지난 카드",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"TIP_NOTE_ITEM_NOT_APPLICABLE","message":"이미 처리됐거나 기한이 지나 반영할 수 없는 카드입니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "반복 날짜 오류 (드묾)",
                      value =
                          """
                          {"status":400,"success":false,"data":null,"error":{"code":"ROUTINE_INVALID_DATE","message":"유효하지 않은 반복 날짜입니다.","detail":null}}
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
        description = "팁노트/카드/유저/루틴 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "팁노트 없음 (남의 것 포함)",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"TIP_NOTE_NOT_FOUND","message":"해당 월의 팁노트가 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "카드 없음",
                      summary = "itemIds에 이 팁노트의 카드가 아닌 ID가 섞임",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"TIP_NOTE_ITEM_NOT_FOUND","message":"팁노트 추천 카드를 찾을 수 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "루틴 없음 (드묾)",
                      summary = "반영 직전에 대상 루틴이 삭제된 경우",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"ROUTINE_NOT_FOUND","message":"루틴을 찾을 수 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "유저 없음",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"리소스를 찾을 수 없습니다.","detail":null}}
                          """)
                }))
  })
  ResponseEntity<ApiResult<TipNoteApplyResponse>> applyTipNote(
      Long userId,
      @Parameter(name = "noteId", description = "팁노트 ID", example = "17", required = true)
          Long noteId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples =
                          @ExampleObject(
                              name = "카드 2장 반영",
                              value =
                                  """
                                  { "itemIds": [3, 5] }
                                  """)))
          TipNoteApplyRequest request);

  @Operation(
      summary = "팁노트 반영 없이 끝내기",
      description =
          """
          남아 있는(아직 반영 안 한) 추천 카드를 전부 접습니다. 접힌 카드는 다시 보이지 않고,
          작성 팁 카드만 남습니다. ("반영 없이 끝내기" 버튼)

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Path Variable

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | noteId | ✅ 필수 | integer | 팁노트 ID (조회 응답의 noteId) | `17` |

          ---

          ### 주의사항
          - 가장 최근 팁노트만 끝낼 수 있습니다 (지난 달이면 400 `TIP_NOTE_NOT_LATEST`)
          - 이미 반영(APPLIED)된 카드는 건드리지 않습니다
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "끝내기 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {"status":200,"success":true,"data":null,"error":null}
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "최신 팁노트가 아님",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {"status":400,"success":false,"data":null,"error":{"code":"TIP_NOTE_NOT_LATEST","message":"가장 최근 팁노트에서만 반영하거나 끝낼 수 있습니다.","detail":null}}
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
        description = "팁노트 또는 유저 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "팁노트 없음 (남의 것 포함)",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"TIP_NOTE_NOT_FOUND","message":"해당 월의 팁노트가 없습니다.","detail":null}}
                          """),
                  @ExampleObject(
                      name = "유저 없음",
                      value =
                          """
                          {"status":404,"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"리소스를 찾을 수 없습니다.","detail":null}}
                          """)
                }))
  })
  ResponseEntity<ApiResult<Void>> dismissTipNote(
      Long userId,
      @Parameter(name = "noteId", description = "팁노트 ID", example = "17", required = true)
          Long noteId);
}
