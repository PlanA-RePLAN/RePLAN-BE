package plana.replan.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.user.dto.UserResponseDto;
import plana.replan.domain.user.service.UserService;
import plana.replan.global.common.ApiResult;

@Tag(name = "User", description = "유저 관련 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @Operation(
      summary = "내 정보 조회",
      description =
          """
                    **호출 주체**: AccessToken을 보유한 인증 사용자

                    **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

                    **비즈니스 로직**
                    1. JwtFilter에서 AccessToken 검증 후 userId를 SecurityContext에 저장
                    2. @AuthenticationPrincipal 로 userId 주입
                    3. userId로 DB에서 유저 조회
                    4. 이메일, 닉네임, 역할(Role), 가입 경로(Provider) 반환

                    **참고**: userId가 null이거나 DB에 존재하지 않으면 404 반환
                    """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "내 정보 조회 성공",
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
                                      "provider": "LOCAL"
                                    }
                                  }
                                  """))),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 유효하지 않은 토큰",
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
                                    "error": {
                                      "code": "USER_NOT_FOUND",
                                      "message": "유저를 찾을 수 없습니다.",
                                      "detail": null
                                    }
                                  }
                                  """)))
  })
  @GetMapping("/me")
  public ResponseEntity<ApiResult<UserResponseDto>> getMyInfo(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(userService.getMyInfo(userId)));
  }
}
