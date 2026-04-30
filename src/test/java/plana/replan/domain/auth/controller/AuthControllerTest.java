package plana.replan.domain.auth.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.OAuthLoginResponseDto;
import plana.replan.domain.auth.service.AuthService;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;

  @MockitoBean private JwtUtil jwtUtil;

  @Test
  @DisplayName("회원가입 성공: status=200, success=true, data/error 필드 없음")
  void signUp_success() throws Exception {
    willDoNothing().given(authService).signUp(any());

    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {
                  "email": "test@test.com",
                  "password": "12345678",
                  "nickname": "테스트"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("회원가입 실패 - 이메일 형식 오류: status=400, success=false, error.code=INVALID_INPUT")
  void signUp_invalidEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {
                  "email": "notanemail",
                  "password": "12345678",
                  "nickname": "테스트"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("회원가입 실패 - 중복 이메일: status=409, success=false, error.code=DUPLICATE_EMAIL")
  void signUp_duplicateEmail() throws Exception {
    willThrow(new CustomException(UserErrorCode.DUPLICATE_EMAIL)).given(authService).signUp(any());

    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {
                  "email": "test@test.com",
                  "password": "12345678",
                  "nickname": "테스트"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("로그인 성공: status=200, success=true, data.accessToken 존재")
  void login_success() throws Exception {
    given(authService.login(any()))
        .willReturn(new LoginResponseDto("access-token-value", "refresh-token-value"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {
                  "email": "test@test.com",
                  "password": "12345678"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-value"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("로그인 실패 - 이메일/비밀번호 불일치: status=401, success=false, error.code=LOGIN_FAILED")
  void login_failed() throws Exception {
    given(authService.login(any())).willThrow(new CustomException(UserErrorCode.LOGIN_FAILED));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {
                  "email": "test@test.com",
                  "password": "wrongpassword"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("reissue - Authorization 헤더 없음: status=401, error.code=EMPTY_TOKEN")
  void reissue_noToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/reissue"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("reissue - Bearer 뒤 토큰이 빈 문자열: status=401, error.code=EMPTY_TOKEN")
  void reissue_blankToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/reissue").header("Authorization", "Bearer "))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("logout - Authorization 헤더 없음: status=401, error.code=EMPTY_TOKEN")
  void logout_noToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("logout - Bearer 뒤 토큰이 빈 문자열: status=401, error.code=EMPTY_TOKEN")
  void logout_blankToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer "))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  // ── Google OAuth ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("Google 로그인 성공 (기존유저): status=200, success=true, data.accessToken 존재")
  void googleLogin_success() throws Exception {
    given(authService.googleLogin(any()))
        .willReturn(
            OAuthLoginResponseDto.existingUser("access-token-value", "refresh-token-value"));

    mockMvc
        .perform(
            post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "credential": "valid-google-id-token" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-value"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("Google 로그인 실패 - credential 빈 문자열: status=400, error.code=INVALID_INPUT")
  void googleLogin_blankCredential() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "credential": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("Google 로그인 실패 - credential 필드 누락: status=400, error.code=INVALID_INPUT")
  void googleLogin_missingCredential() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("Google 로그인 실패 - 유효하지 않은 ID Token: status=401, error.code=GOOGLE_TOKEN_INVALID")
  void googleLogin_invalidToken() throws Exception {
    given(authService.googleLogin(any()))
        .willThrow(new CustomException(UserErrorCode.GOOGLE_TOKEN_INVALID));

    mockMvc
        .perform(
            post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "credential": "invalid-token" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("GOOGLE_TOKEN_INVALID"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("Google 로그인 실패 - 이메일 미인증 계정: status=401, error.code=GOOGLE_TOKEN_INVALID")
  void googleLogin_emailNotVerified() throws Exception {
    given(authService.googleLogin(any()))
        .willThrow(new CustomException(UserErrorCode.GOOGLE_TOKEN_INVALID));

    mockMvc
        .perform(
            post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "credential": "unverified-email-token" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("GOOGLE_TOKEN_INVALID"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName(
      "Google 로그인 실패 - 다른 Provider로 가입된 이메일: status=409, error.code=OAUTH_PROVIDER_CONFLICT")
  void googleLogin_providerConflict() throws Exception {
    given(authService.googleLogin(any()))
        .willThrow(new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT));

    mockMvc
        .perform(
            post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "credential": "valid-google-id-token" }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("OAUTH_PROVIDER_CONFLICT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── Naver OAuth ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("Naver 로그인 성공 (기존유저): status=200, success=true, data.accessToken 존재")
  void naverLogin_success() throws Exception {
    given(authService.naverLogin(any()))
        .willReturn(
            OAuthLoginResponseDto.existingUser("access-token-value", "refresh-token-value"));

    mockMvc
        .perform(
            post("/api/auth/oauth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "accessToken": "valid-naver-access-token" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-value"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("Naver 로그인 실패 - accessToken 빈 문자열: status=400, error.code=INVALID_INPUT")
  void naverLogin_blankAccessToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "accessToken": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("Naver 로그인 실패 - accessToken 필드 누락: status=400, error.code=INVALID_INPUT")
  void naverLogin_missingAccessToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/naver").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("Naver 로그인 실패 - 유효하지 않은 Access Token: status=401, error.code=NAVER_TOKEN_INVALID")
  void naverLogin_invalidToken() throws Exception {
    given(authService.naverLogin(any()))
        .willThrow(new CustomException(UserErrorCode.NAVER_TOKEN_INVALID));

    mockMvc
        .perform(
            post("/api/auth/oauth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "accessToken": "invalid-token" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NAVER_TOKEN_INVALID"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName(
      "Naver 로그인 실패 - 다른 Provider로 가입된 이메일: status=409, error.code=OAUTH_PROVIDER_CONFLICT")
  void naverLogin_providerConflict() throws Exception {
    given(authService.naverLogin(any()))
        .willThrow(new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT));

    mockMvc
        .perform(
            post("/api/auth/oauth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "accessToken": "valid-naver-access-token" }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("OAUTH_PROVIDER_CONFLICT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── Kakao OAuth ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("Kakao 로그인 성공 (기존유저): status=200, success=true, data.accessToken 존재")
  void kakaoLogin_success() throws Exception {
    given(authService.kakaoLogin(any()))
        .willReturn(
            OAuthLoginResponseDto.existingUser("access-token-value", "refresh-token-value"));

    mockMvc
        .perform(
            post("/api/auth/oauth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "accessToken": "valid-kakao-access-token" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-value"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("Kakao 로그인 실패 - accessToken 빈 문자열: status=400, error.code=INVALID_INPUT")
  void kakaoLogin_blankAccessToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "accessToken": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("Kakao 로그인 실패 - accessToken 필드 누락: status=400, error.code=INVALID_INPUT")
  void kakaoLogin_missingAccessToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/kakao").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("Kakao 로그인 실패 - 유효하지 않은 Access Token: status=401, error.code=KAKAO_TOKEN_INVALID")
  void kakaoLogin_invalidToken() throws Exception {
    given(authService.kakaoLogin(any()))
        .willThrow(new CustomException(UserErrorCode.KAKAO_TOKEN_INVALID));

    mockMvc
        .perform(
            post("/api/auth/oauth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "accessToken": "invalid-token" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("KAKAO_TOKEN_INVALID"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName(
      "Kakao 로그인 실패 - 다른 Provider로 가입된 이메일: status=409, error.code=OAUTH_PROVIDER_CONFLICT")
  void kakaoLogin_providerConflict() throws Exception {
    given(authService.kakaoLogin(any()))
        .willThrow(new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT));

    mockMvc
        .perform(
            post("/api/auth/oauth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "accessToken": "valid-kakao-access-token" }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("OAUTH_PROVIDER_CONFLICT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── OAuth Register ────────────────────────────────────────────────────────

  @Test
  @DisplayName("register 성공: status=200, success=true, data.accessToken 존재")
  void register_success() throws Exception {
    given(authService.register(any(), anyString()))
        .willReturn(new LoginResponseDto("access-token-value", "refresh-token-value"));

    mockMvc
        .perform(
            post("/api/auth/oauth/register")
                .header("Authorization", "Bearer valid-temp-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "nickname": "홍길동" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-value"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("register 실패 - nickname 누락: status=400, error.code=INVALID_INPUT")
  void register_missingNickname() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/register")
                .header("Authorization", "Bearer valid-temp-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("register 실패 - tempToken 헤더 없음: status=401, error.code=EMPTY_TOKEN")
  void register_noTempToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "nickname": "홍길동" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("register 실패 - tempToken 만료: status=401, error.code=INVALID_TEMP_TOKEN")
  void register_invalidTempToken() throws Exception {
    given(authService.register(any(), anyString()))
        .willThrow(new CustomException(UserErrorCode.INVALID_TEMP_TOKEN));

    mockMvc
        .perform(
            post("/api/auth/oauth/register")
                .header("Authorization", "Bearer expired-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "nickname": "홍길동" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_TEMP_TOKEN"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("register 실패 - 닉네임 중복: status=409, error.code=DUPLICATE_NICKNAME")
  void register_duplicateNickname() throws Exception {
    given(authService.register(any(), anyString()))
        .willThrow(new CustomException(UserErrorCode.DUPLICATE_NICKNAME));

    mockMvc
        .perform(
            post("/api/auth/oauth/register")
                .header("Authorization", "Bearer valid-temp-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "nickname": "중복닉네임" }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_NICKNAME"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── Nickname Check ────────────────────────────────────────────────────────

  @Test
  @DisplayName("nickname/check - 사용 가능: status=200, available=true")
  void nicknameCheck_available() throws Exception {
    given(authService.checkNickname("홍길동")).willReturn(true);

    mockMvc
        .perform(get("/api/auth/nickname/check").param("nickname", "홍길동"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.available").value(true));
  }

  @Test
  @DisplayName("nickname/check - 중복: status=200, available=false")
  void nicknameCheck_duplicate() throws Exception {
    given(authService.checkNickname("중복닉네임")).willReturn(false);

    mockMvc
        .perform(get("/api/auth/nickname/check").param("nickname", "중복닉네임"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.available").value(false));
  }
}
