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
import plana.replan.domain.user.dto.ProfileUpdateRequestDto;
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

  @Operation(
      summary = "내 프로필 수정",
      description =
          """
          현재 로그인한 유저의 닉네임과 프로필 이미지를 수정합니다. 두 필드 모두 선택이며, 전달된 값만 수정됩니다.

          프로필 이미지는 먼저 presigned URL로 S3 임시 경로(`profiles/temp/...`)에 업로드한 뒤,
          해당 key를 `profileImageKey`로 전달하면 확정 경로로 이동되어 최종 URL로 저장됩니다.

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
          | nickname | ❌ 선택 | string | 변경할 닉네임. 현재 닉네임과 같으면 중복 검사 없이 통과 | `"새닉네임"` |
          | profileImageKey | ❌ 선택 | string | 변경할 프로필 이미지의 S3 임시 key | `"profiles/temp/4f3a_avatar.png"` |

          > ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          ---

          ### 주의사항
          - `nickname`이 이미 사용 중인 닉네임이면 409 반환 (단, 본인의 현재 닉네임과 같으면 통과)
          - `profileImageKey`가 `profiles/temp/`로 시작하지 않으면 400 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "프로필 수정 성공",
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
                                "nickname": "새닉네임",
                                "role": "ROLE_USER",
                                "provider": "LOCAL",
                                "profileImage": "https://cdn.example.com/profiles/confirmed/4f3a_avatar.png"
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "유효하지 않은 S3 key",
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
                              "error": { "code": "INVALID_S3_KEY", "message": "유효하지 않은 S3 키입니다.", "detail": null }
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
                            """))),
    @ApiResponse(
        responseCode = "409",
        description = "이미 사용 중인 닉네임",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 409,
                              "success": false,
                              "data": null,
                              "error": { "code": "DUPLICATE_NICKNAME", "message": "이미 사용 중인 닉네임입니다.", "detail": null }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<UserResponseDto>> updateMyProfile(
      @AuthenticationPrincipal Long userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "닉네임 + 이미지 모두 수정",
                            value =
                                """
                                { "nickname": "새닉네임", "profileImageKey": "profiles/temp/4f3a_avatar.png" }
                                """),
                        @ExampleObject(
                            name = "닉네임만 수정 (이미지 생략)",
                            summary = "생략한 필드는 변경되지 않음",
                            value =
                                """
                                { "nickname": "새닉네임" }
                                """)
                      }))
          ProfileUpdateRequestDto request);
}
