package plana.replan.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import plana.replan.domain.auth.dto.GoogleLoginRequestDto;
import plana.replan.domain.auth.dto.LoginRequestDto;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.SignUpRequestDto;
import plana.replan.domain.auth.service.AuthService;
import plana.replan.global.common.ApiResult;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtErrorCode;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Operation(
      summary = "회원가입",
      description =
          """
                  **호출 주체**: 비인증 사용자 (누구나 호출 가능)

                  **비즈니스 로직**
                  1. 이메일 중복 여부 확인 → 중복이면 409 반환
                  2. 비밀번호 BCrypt 암호화
                  3. LOCAL Provider 유저 엔티티 생성 후 DB 저장

                  **요청 조건**
                  - 이메일: 이메일 형식 필수
                  - 비밀번호: 8자 이상 필수
                  - 닉네임: 필수
                  """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "회원가입 성공",
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
        responseCode = "400",
        description = "요청 값 유효성 검사 실패 (이메일 형식 오류, 비밀번호 8자 미만 등)",
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
                                    "message": "잘못된 입력입니다.",
                                    "detail": "email: 이메일 형식이 아닙니다."
                                  }
                                }
                                """))),
    @ApiResponse(
        responseCode = "409",
        description = "이미 사용 중인 이메일",
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
                                  "error": {
                                    "code": "DUPLICATE_EMAIL",
                                    "message": "이미 사용 중인 이메일입니다.",
                                    "detail": null
                                  }
                                }
                                """)))
  })
  @PostMapping("/signup")
  public ResponseEntity<ApiResult<Void>> signUp(@Valid @RequestBody SignUpRequestDto request) {
    authService.signUp(request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Operation(
      summary = "로그인",
      description =
          """
                  **호출 주체**: 비인증 사용자 (누구나 호출 가능)

                  **비즈니스 로직**
                  1. 이메일로 유저 조회 → 없으면 401 반환
                  2. 입력한 비밀번호와 저장된 해시 비교 → 불일치 시 401 반환
                  3. AccessToken (단기) + RefreshToken (7일) 발급
                  4. RefreshToken을 Redis에 저장 (`refresh:{email}` 키)

                  **보안 참고**: 이메일/비밀번호 오류를 구분하지 않고 동일한 에러로 반환 (사용자 열거 공격 방지)
                  """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "로그인 성공 - AccessToken, RefreshToken 반환",
        content =
            @Content(
                schema = @Schema(implementation = LoginResponseDto.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 200,
                                  "success": true,
                                  "data": {
                                    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
                                  },
                                  "error": null
                                }
                                """))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 값 유효성 검사 실패 (이메일 형식 오류 등)",
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
                                    "message": "잘못된 입력입니다.",
                                    "detail": "email: 이메일 형식이 아닙니다."
                                  }
                                }
                                """))),
    @ApiResponse(
        responseCode = "401",
        description = "이메일 또는 비밀번호 불일치",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 401,
                                  "success": false,
                                  "data": null,
                                  "error": {
                                    "code": "LOGIN_FAILED",
                                    "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
                                    "detail": null
                                  }
                                }
                                """)))
  })
  @PostMapping("/login")
  public ResponseEntity<ApiResult<LoginResponseDto>> login(
      @Valid @RequestBody LoginRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(authService.login(request)));
  }

  @Operation(
      summary = "토큰 재발급",
      description =
          """
                  **호출 주체**: RefreshToken을 보유한 인증 사용자

                  **요청 방법**: `Authorization: Bearer {refreshToken}` 헤더 필수

                  **비즈니스 로직**
                  1. Authorization 헤더에서 RefreshToken 추출
                  2. JWT 서명 및 만료 여부 검증
                  3. Redis에서 해당 이메일의 RefreshToken 조회 → 없으면 401 (로그아웃 혹은 만료)
                  4. 클라이언트 토큰과 Redis 저장 토큰 비교 → 불일치 시 401
                  5. 유저 조회 후 새 AccessToken + RefreshToken 발급
                  6. Redis에 새 RefreshToken 덮어쓰기 (Token Rotation 전략)

                  **Token Rotation**: 재발급 시마다 RefreshToken도 교체되어 탈취 시 피해 최소화
                  """,
      security =
          @io.swagger.v3.oas.annotations.security.SecurityRequirement(
              name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "토큰 재발급 성공 - 새 AccessToken, RefreshToken 반환",
        content =
            @Content(
                schema = @Schema(implementation = LoginResponseDto.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 200,
                                  "success": true,
                                  "data": {
                                    "accessToken": "eyJhbGciOiJIUzI1NiJ9...(new)",
                                    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...(new)"
                                  },
                                  "error": null
                                }
                                """))),
    @ApiResponse(
        responseCode = "401",
        description = "토큰 인증 실패 (여러 케이스)",
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
                                              """),
                  @ExampleObject(
                      name = "Redis에 RefreshToken 없음 (로그아웃 상태)",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "success": false,
                                                "data": null,
                                                "error": {
                                                  "code": "REFRESH_TOKEN_NOT_FOUND",
                                                  "message": "Refresh Token이 존재하지 않습니다.",
                                                  "detail": null
                                                }
                                              }
                                              """),
                  @ExampleObject(
                      name = "토큰 불일치 (탈취 의심)",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "success": false,
                                                "data": null,
                                                "error": {
                                                  "code": "INVALID_REFRESH_TOKEN",
                                                  "message": "유효하지 않은 Refresh Token입니다.",
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
                                  "data": null,
                                  "error": {
                                    "code": "USER_NOT_FOUND",
                                    "message": "유저를 찾을 수 없습니다.",
                                    "detail": null
                                  }
                                }
                                """)))
  })
  @PostMapping("/reissue")
  public ResponseEntity<ApiResult<LoginResponseDto>> reissue(
      @RequestHeader(value = "Authorization", required = false) String authHeader) {

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
    String refreshToken = authHeader.substring(7);
    if (refreshToken.isBlank()) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
    return ResponseEntity.ok(ApiResult.ok(authService.reissue(refreshToken)));
  }

  @Operation(
      summary = "로그아웃",
      description =
          """
                  **호출 주체**: AccessToken을 보유한 인증 사용자

                  **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

                  **비즈니스 로직**
                  1. Authorization 헤더에서 AccessToken 추출
                  2. AccessToken 서명 및 만료 여부 검증
                  3. 이메일 추출 후 Redis에서 `refresh:{email}` 키 삭제

                  **참고**: AccessToken 자체는 서버에서 무효화 불가 (Stateless).
                  RefreshToken을 Redis에서 삭제함으로써 재발급을 차단하는 방식으로 로그아웃 처리.
                  """,
      security =
          @io.swagger.v3.oas.annotations.security.SecurityRequirement(
              name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "로그아웃 성공 - Redis에서 RefreshToken 삭제 완료",
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
        description = "토큰 인증 실패",
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
                      name = "유효하지 않은 토큰",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "success": false,
                                                "data": null,
                                                "error": {
                                                  "code": "INVALID_TOKEN",
                                                  "message": "유효하지 않은 토큰입니다.",
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
                }))
  })
  @PostMapping("/logout")
  public ResponseEntity<ApiResult<Void>> logout(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
    String accessToken = authHeader.substring(7);
    if (accessToken.isBlank()) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
    authService.logout(accessToken);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Operation(
      summary = "Google 소셜 로그인",
      description =
          """
                  **호출 주체**: 비인증 사용자 (누구나 호출 가능)

                  **지원 플랫폼**: 웹(GIS SDK) / Android(GoogleSignIn) / iOS(GIDSignIn) 공통 사용

                  **비즈니스 로직**
                  1. Google SDK에서 발급받은 credential(ID Token)을 전달
                  2. 서버에서 Google ID Token 서명·audience·만료 검증
                  3. 이메일 인증이 완료된 구글 계정인지 확인
                  4. 동일 이메일이 다른 방식으로 가입된 경우 409 반환
                  5. GOOGLE 유저가 있으면 로그인, 없으면 자동 회원가입 후 로그인
                  6. 자체 AccessToken + RefreshToken 발급하여 반환
                  """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Google 로그인 성공 - AccessToken, RefreshToken 반환",
        content =
            @Content(
                schema = @Schema(implementation = LoginResponseDto.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 200,
                                  "success": true,
                                  "data": {
                                    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
                                  },
                                  "error": null
                                }
                                """))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 값 유효성 검사 실패 (credential 누락/공백)",
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
                                    "message": "잘못된 입력입니다.",
                                    "detail": "credential: Google ID Token은 필수입니다."
                                  }
                                }
                               """))),
    @ApiResponse(
        responseCode = "401",
        description = "Google ID Token 검증 실패 (만료, 위조, 이메일 미인증)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 401,
                                  "success": false,
                                  "data": null,
                                  "error": {
                                    "code": "GOOGLE_TOKEN_INVALID",
                                    "message": "Google ID Token 검증에 실패했습니다.",
                                    "detail": null
                                  }
                                }
                                """))),
    @ApiResponse(
        responseCode = "409",
        description = "동일 이메일이 이미 다른 방식으로 가입됨",
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
                                  "error": {
                                    "code": "OAUTH_PROVIDER_CONFLICT",
                                    "message": "해당 이메일은 이미 다른 방식으로 가입되어 있습니다.",
                                    "detail": null
                                  }
                                }
                                """)))
  })
  @PostMapping("/oauth/google")
  public ResponseEntity<ApiResult<LoginResponseDto>> googleLogin(
      @Valid @RequestBody GoogleLoginRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(authService.googleLogin(request)));
  }
}
