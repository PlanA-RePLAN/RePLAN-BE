package plana.replan.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import plana.replan.domain.notification.dto.NotificationListResponse;
import plana.replan.domain.notification.dto.NotificationSettingResponse;
import plana.replan.domain.notification.dto.NotificationSettingUpdateRequest;
import plana.replan.domain.notification.dto.UnreadCountResponse;
import plana.replan.domain.notification.entity.NotificationCategory;
import plana.replan.global.common.ApiResult;

@Tag(name = "Notification", description = "알림함 조회·읽음 처리 및 설정 API")
public interface NotificationControllerDocs {

  @Operation(
      summary = "알림함 목록 조회",
      description =
          "내 알림 목록을 최신 순으로 가져온다. 카테고리(탭)로 필터링할 수 있다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n\n"
              + "### Query Parameters\n"
              + "| 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |\n"
              + "|-----------|-----------|------|--------|------|------|\n"
              + "| category | ❌ 선택 | string | 없음 | 탭 분류 필터 (TODO/STATS/ETC). 생략 시 전체 조회 | `TODO` |\n"
              + "| cursor | ❌ 선택 | integer | 없음 | 이전 응답의 nextCursor 값 | `37` |\n"
              + "| size | ❌ 선택 | integer | `10` | 한 페이지에 가져올 알림 수 | `10` |\n\n"
              + "### Response Elements\n"
              + "| 필드명 | 타입 | 설명 |\n"
              + "|--------|------|------|\n"
              + "| items | array | 알림 목록 |\n"
              + "| nextCursor | integer | 다음 cursor. 마지막이면 null |\n"
              + "| hasNext | boolean | 다음 페이지 존재 여부 |\n\n"
              + "**Step 1. 첫 번째 요청** — cursor 없이 호출\n\n"
              + "GET /api/notifications?size=10\n\n"
              + "**Step 2. 다음 페이지 요청** — 응답의 nextCursor를 cursor에 전달\n\n"
              + "GET /api/notifications?cursor=37&size=10\n\n"
              + "**Step 3. 종료 조건** — hasNext=false 또는 nextCursor=null이면 마지막 페이지")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":200,\"success\":true,\"data\":{\"items\":[{\"id\":12,\"category\":\"TODO\",\"type\":\"TODO_DUE_SOON\",\"title\":\"'영단어 100개 암기' 투두\",\"body\":\"마감 시간이 하루 남았어요.\",\"targetType\":\"TODO\",\"targetId\":9,\"read\":false,\"createdAt\":\"2026-06-19T00:00:00\"}],\"nextCursor\":12,\"hasNext\":false},\"error\":null}"))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EMPTY_TOKEN\",\"message\":\"인증 토큰이 없습니다.\"}}"),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EXPIRED_TOKEN\",\"message\":\"만료된 토큰입니다.\"}}")
                })),
    @ApiResponse(
        responseCode = "404",
        description = "사용자를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"유저를 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<NotificationListResponse>> getNotifications(
      Long userId,
      @Parameter(description = "탭 분류 필터 (TODO/STATS/ETC). 생략 시 전체 조회", example = "TODO")
          NotificationCategory category,
      @Parameter(description = "이전 응답의 nextCursor 값", example = "37") Long cursor,
      @Parameter(description = "한 페이지에 가져올 알림 수", example = "10") int size);

  @Operation(
      summary = "안 읽은 알림 개수 조회",
      description =
          "읽지 않은 알림의 총 개수를 반환한다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n\n"
              + "### Response Elements\n"
              + "| 필드명 | 타입 | 설명 |\n"
              + "|--------|------|------|\n"
              + "| count | integer | 안 읽은 알림 개수 |")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":200,\"success\":true,\"data\":{\"count\":3},\"error\":null}"))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EMPTY_TOKEN\",\"message\":\"인증 토큰이 없습니다.\"}}"),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EXPIRED_TOKEN\",\"message\":\"만료된 토큰입니다.\"}}")
                })),
    @ApiResponse(
        responseCode = "404",
        description = "사용자를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"유저를 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<UnreadCountResponse>> getUnreadCount(Long userId);

  @Operation(
      summary = "알림 읽음 처리",
      description =
          "특정 알림 1건을 읽음으로 표시한다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n\n"
              + "### Path Variable\n"
              + "| 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |\n"
              + "|-----------|-----------|------|------|------|\n"
              + "| notificationId | ✅ 필수 | integer | 읽음 처리할 알림 id | `12` |")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "읽음 처리 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value = "{\"status\":200,\"success\":true,\"data\":null,\"error\":null}"))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EMPTY_TOKEN\",\"message\":\"인증 토큰이 없습니다.\"}}"),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EXPIRED_TOKEN\",\"message\":\"만료된 토큰입니다.\"}}")
                })),
    @ApiResponse(
        responseCode = "404",
        description = "사용자 또는 알림을 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "사용자 없음",
                      value =
                          "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"유저를 찾을 수 없습니다.\"}}"),
                  @ExampleObject(
                      name = "알림 없음 (내 알림이 아닌 경우 포함)",
                      value =
                          "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"NOTIFICATION_NOT_FOUND\",\"message\":\"알림을 찾을 수 없습니다.\"}}")
                }))
  })
  ResponseEntity<ApiResult<Void>> readOne(Long userId, Long notificationId);

  @Operation(
      summary = "알림 설정 조회",
      description =
          "내 알림 수신 설정을 조회한다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n\n"
              + "### Response Elements\n"
              + "| 필드명 | 타입 | 설명 |\n"
              + "|--------|------|------|\n"
              + "| todoDue | boolean | 마감 임박 알림 받기 |\n"
              + "| todoFailed | boolean | 실패 리플랜 알림 받기 |\n"
              + "| report | boolean | 리포트 도착 알림 받기 |")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":200,\"success\":true,\"data\":{\"todoDue\":true,\"todoFailed\":true,\"report\":true},\"error\":null}"))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EMPTY_TOKEN\",\"message\":\"인증 토큰이 없습니다.\"}}"),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EXPIRED_TOKEN\",\"message\":\"만료된 토큰입니다.\"}}")
                })),
    @ApiResponse(
        responseCode = "404",
        description = "사용자를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"유저를 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<NotificationSettingResponse>> getSettings(Long userId);

  @Operation(
      summary = "알림 설정 변경",
      description =
          "내 알림 수신 설정을 변경한다. 보내지 않은 필드(null)는 기존 값을 유지한다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n"
              + "| Content-Type | ✅ 필수 | string | `application/json` |\n\n"
              + "### Request Body\n"
              + "| 필드명 | 필수 여부 | 타입 | 설명 | 예시 |\n"
              + "|--------|-----------|------|------|------|\n"
              + "| todoDue | ❌ 선택 | boolean | 마감 임박 알림 받기 | `false` |\n"
              + "| todoFailed | ❌ 선택 | boolean | 실패 리플랜 알림 받기 | `true` |\n"
              + "| report | ❌ 선택 | boolean | 리포트 도착 알림 받기 | `true` |\n\n"
              + "❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.\n\n"
              + "### Response Elements\n"
              + "| 필드명 | 타입 | 설명 |\n"
              + "|--------|------|------|\n"
              + "| todoDue | boolean | 변경 후 마감 임박 알림 받기 여부 |\n"
              + "| todoFailed | boolean | 변경 후 실패 리플랜 알림 받기 여부 |\n"
              + "| report | boolean | 변경 후 리포트 도착 알림 받기 여부 |")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "설정 변경 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":200,\"success\":true,\"data\":{\"todoDue\":false,\"todoFailed\":true,\"report\":true},\"error\":null}"))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EMPTY_TOKEN\",\"message\":\"인증 토큰이 없습니다.\"}}"),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EXPIRED_TOKEN\",\"message\":\"만료된 토큰입니다.\"}}")
                })),
    @ApiResponse(
        responseCode = "404",
        description = "사용자를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"유저를 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<NotificationSettingResponse>> updateSettings(
      Long userId, NotificationSettingUpdateRequest request);
}
