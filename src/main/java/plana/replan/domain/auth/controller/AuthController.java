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
import plana.replan.domain.auth.dto.LoginRequestDto;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.SignUpRequestDto;
import plana.replan.domain.auth.service.AuthService;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.ErrorResponse;
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
    @ApiResponse(responseCode = "200", description = "회원가입 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "요청 값 유효성 검사 실패 (이메일 형식 오류, 비밀번호 8자 미만 등)",
        content =
            @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 400,
                                  "code": "INVALID_INPUT",
                                  "message": "이메일은 필수입니다.",
                                  "detail": null,
                                  "timestamp": "2026-03-31T12:00:00"
                                }
                                """))),
    @ApiResponse(
        responseCode = "409",
        description = "이미 사용 중인 이메일",
        content =
            @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 409,
                                  "code": "DUPLICATE_EMAIL",
                                  "message": "이미 사용 중인 이메일입니다.",
                                  "detail": null,
                                  "timestamp": "2026-03-31T12:00:00"
                                }
                                """)))
  })
  @PostMapping("/signup")
  public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequestDto request) {
    authService.signUp(request);
    return ResponseEntity.ok().build();
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
                                  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
                                }
                                """))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 값 유효성 검사 실패 (이메일 형식 오류 등)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "이메일 또는 비밀번호 불일치",
        content =
            @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 401,
                                  "code": "LOGIN_FAILED",
                                  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
                                  "detail": null,
                                  "timestamp": "2026-03-31T12:00:00"
                                }
                                """)))
  })
  @PostMapping("/login")
  public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
    return ResponseEntity.ok(authService.login(request));
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
                  """)
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
                                  "accessToken": "eyJhbGciOiJIUzI1NiJ9...(new)",
                                  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...(new)"
                                }
                                """))),
    @ApiResponse(
        responseCode = "401",
        description = "토큰 인증 실패 (여러 케이스)",
        content =
            @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "code": "EMPTY_TOKEN",
                                                "message": "토큰이 없습니다.",
                                                "detail": null,
                                                "timestamp": "2026-03-31T12:00:00"
                                              }
                                              """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "code": "EXPIRED_TOKEN",
                                                "message": "만료된 토큰입니다.",
                                                "detail": null,
                                                "timestamp": "2026-03-31T12:00:00"
                                              }
                                              """),
                  @ExampleObject(
                      name = "Redis에 RefreshToken 없음 (로그아웃 상태)",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "code": "REFRESH_TOKEN_NOT_FOUND",
                                                "message": "Refresh Token이 존재하지 않습니다.",
                                                "detail": null,
                                                "timestamp": "2026-03-31T12:00:00"
                                              }
                                              """),
                  @ExampleObject(
                      name = "토큰 불일치 (탈취 의심)",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "code": "INVALID_REFRESH_TOKEN",
                                                "message": "유효하지 않은 Refresh Token입니다.",
                                                "detail": null,
                                                "timestamp": "2026-03-31T12:00:00"
                                              }
                                              """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "토큰은 유효하나 해당 유저가 DB에 없는 경우",
        content =
            @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                {
                                  "status": 404,
                                  "code": "USER_NOT_FOUND",
                                  "message": "유저를 찾을 수 없습니다.",
                                  "detail": null,
                                  "timestamp": "2026-03-31T12:00:00"
                                }
                                """)))
  })
  @PostMapping("/reissue")
  public ResponseEntity<LoginResponseDto> reissue(
      @RequestHeader("Authorization") String authHeader) {

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
    String refreshToken = authHeader.substring(7);
    return ResponseEntity.ok(authService.reissue(refreshToken));
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
                  """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그아웃 성공 - Redis에서 RefreshToken 삭제 완료"),
    @ApiResponse(
        responseCode = "401",
        description = "토큰 인증 실패",
        content =
            @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "code": "EMPTY_TOKEN",
                                                "message": "토큰이 없습니다.",
                                                "detail": null,
                                                "timestamp": "2026-03-31T12:00:00"
                                              }
                                              """),
                  @ExampleObject(
                      name = "유효하지 않은 토큰",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "code": "INVALID_TOKEN",
                                                "message": "유효하지 않은 토큰입니다.",
                                                "detail": null,
                                                "timestamp": "2026-03-31T12:00:00"
                                              }
                                              """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                                              {
                                                "status": 401,
                                                "code": "EXPIRED_TOKEN",
                                                "message": "만료된 토큰입니다.",
                                                "detail": null,
                                                "timestamp": "2026-03-31T12:00:00"
                                              }
                                              """)
                }))
  })
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
    String accessToken = authHeader.substring(7);
    authService.logout(accessToken);
    return ResponseEntity.ok().build();
  }
}
