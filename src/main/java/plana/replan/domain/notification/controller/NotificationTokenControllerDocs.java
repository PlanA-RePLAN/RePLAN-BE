package plana.replan.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.global.common.ApiResult;

@Tag(name = "Notification Token", description = "푸시 알림 기기 토큰 등록/삭제 API")
public interface NotificationTokenControllerDocs {

  @Operation(
      summary = "기기 토큰 등록",
      description =
          "알림 권한 허용 후 프론트가 받은 FCM 토큰을 등록한다. 같은 토큰을 다시 보내면 갱신(upsert)된다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n"
              + "| Content-Type | ✅ 필수 | string | `application/json` |\n\n"
              + "### Request Body\n"
              + "| 필드명 | 필수 여부 | 타입 | 설명 | 예시 |\n"
              + "|--------|-----------|------|------|------|\n"
              + "| token | ✅ 필수 | string | FCM 토큰 | `\"fcm-token-xyz\"` |\n"
              + "| platform | ✅ 필수 | string | 기기 종류 (WEB/ANDROID/IOS) | `\"WEB\"` |")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "등록 성공",
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
        description = "사용자를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"사용자를 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<Void>> registerToken(
      Long userId, @Valid DeviceTokenRegisterRequest request);

  @Operation(
      summary = "기기 토큰 삭제",
      description =
          "로그아웃 시 해당 기기의 FCM 토큰을 삭제한다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n"
              + "| Content-Type | ✅ 필수 | string | `application/json` |\n\n"
              + "### Request Body\n"
              + "| 필드명 | 필수 여부 | 타입 | 설명 | 예시 |\n"
              + "|--------|-----------|------|------|------|\n"
              + "| token | ✅ 필수 | string | 삭제할 FCM 토큰 | `\"fcm-token-xyz\"` |")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "삭제 성공",
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
        description = "토큰 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"TOKEN_NOT_FOUND\",\"message\":\"등록된 기기 토큰을 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<Void>> deleteToken(Long userId, @Valid DeviceTokenDeleteRequest request);
}
