package plana.replan.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import plana.replan.domain.user.dto.UserResponseDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "User", description = "유저 관련 API")
public interface UserControllerDocs {

  @Operation(
      summary = "내 프로필 조회",
      description =
          """
          현재 로그인한 유저의 프로필 정보를 조회합니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | userId | integer | 유저 ID |
          | email | string | 이메일 |
          | nickname | string | 닉네임 |
          | role | string | 역할 (ROLE_USER / ROLE_ADMIN) |
          | provider | string | 가입 경로 (LOCAL / KAKAO / GOOGLE / NAVER) |
          | profileImage | string | 프로필 이미지 URL. 미설정 시 null |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "프로필 조회 성공",
        content =
            @Content(
                schema = @Schema(implementation = UserResponseDto.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "userId": 1,
                                "email": "user@example.com",
                                "nickname": "일규",
                                "role": "ROLE_USER",
                                "provider": "LOCAL",
                                "profileImage": "https://cdn.example.com/profiles/confirmed/abc.png"
                              },
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
        description = "토큰은 유효하나 해당 유저가 DB에 없는 경우",
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
                              "error": { "code": "USER_NOT_FOUND", "message": "유저를 찾을 수 없습니다.", "detail": null }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<UserResponseDto>> getMyProfile(@AuthenticationPrincipal Long userId);
}
