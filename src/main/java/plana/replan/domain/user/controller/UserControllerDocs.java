package plana.replan.domain.user.controller;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import plana.replan.domain.auth.dto.PresignedUrlResponseDto;
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
          - `nickname`이 공백(빈 문자열/스페이스만)이면 400(INVALID_INPUT) 반환
          - `nickname`이 이미 사용 중인 닉네임이면 409 반환 (단, 본인의 현재 닉네임과 같으면 통과)
          - `profileImageKey`가 `profiles/temp/`로 시작하지 않으면 400(INVALID_S3_KEY) 반환
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
        description = "잘못된 입력값 (유효하지 않은 S3 key 또는 공백 닉네임)",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "유효하지 않은 S3 key",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": { "code": "INVALID_S3_KEY", "message": "유효하지 않은 S3 키입니다.", "detail": null }
                          }
                          """),
                  @ExampleObject(
                      name = "공백 닉네임",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": { "code": "INVALID_INPUT", "message": "잘못된 입력입니다.", "detail": null }
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

  @Operation(
      summary = "계정 삭제(회원 탈퇴)",
      description =
          """
          현재 로그인한 유저를 탈퇴 처리합니다. 처리 내용:
          - 개인정보(이메일·닉네임·비밀번호·프로필 이미지) 익명화/파기
          - 회원이 만든 데이터(투두·목표·루틴·태그) 함께 삭제(soft delete)
          - 발급된 refresh token 무효화

          탈퇴한 이메일로는 다시 가입할 수 있습니다. 삭제 후에는 동일 토큰으로 다른 API를 호출해도 유저를 찾을 수 없어 404가 반환됩니다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "계정 삭제 성공",
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
  ResponseEntity<ApiResult<Void>> deleteMyAccount(@AuthenticationPrincipal Long userId);

  @Operation(
      summary = "프로필 이미지 업로드용 Presigned URL 발급 (로그인 유저)",
      description =
          """
          로그인한 유저가 프로필 이미지를 변경하기 위해 S3 임시 경로(`profiles/temp/...`)에 직접 업로드할
          presigned URL을 발급받습니다.

          **사용 흐름**
          1. 이 API로 presignedUrl과 s3Key를 발급받는다.
          2. presignedUrl로 이미지 파일을 직접 PUT 업로드한다 (Content-Type 헤더 필수).
          3. 발급받은 s3Key를 `PATCH /api/users/profile`의 `profileImageKey`로 전달한다.

          ---

          ### Request Headers

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          ---

          ### Query Parameters

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | filename | ✅ 필수 | string | 없음 | 업로드할 파일명. `/`, `..` 포함 불가 | `avatar.png` |
          | contentType | ✅ 필수 | string | 없음 | 이미지 MIME 타입 (jpeg/png/webp/gif) | `image/png` |

          ---

          ### Response Elements

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | presignedUrl | string | 이미지 PUT 업로드용 presigned URL (10분 유효) |
          | s3Key | string | 업로드 경로 key. PATCH 프로필 수정 시 profileImageKey로 사용 |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Presigned URL 발급 성공",
        content =
            @Content(
                schema = @Schema(implementation = PresignedUrlResponseDto.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "presignedUrl": "https://bucket.s3.amazonaws.com/profiles/temp/uuid_avatar.png?...",
                                "s3Key": "profiles/temp/uuid_avatar.png"
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 파일명 또는 지원하지 않는 이미지 형식",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "잘못된 파일명",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": { "code": "INVALID_FILENAME", "message": "유효하지 않은 파일명입니다.", "detail": null }
                          }
                          """),
                  @ExampleObject(
                      name = "지원하지 않는 형식",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": { "code": "UNSUPPORTED_CONTENT_TYPE", "message": "지원하지 않는 파일 형식입니다.", "detail": null }
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
  ResponseEntity<ApiResult<PresignedUrlResponseDto>> getProfileImagePresignedUrl(
      @AuthenticationPrincipal Long userId,
      @Parameter(
              name = "filename",
              description = "업로드할 파일명",
              example = "avatar.png",
              required = true)
          String filename,
      @Parameter(
              name = "contentType",
              description = "이미지 MIME 타입",
              example = "image/png",
              required = true)
          String contentType);
}
