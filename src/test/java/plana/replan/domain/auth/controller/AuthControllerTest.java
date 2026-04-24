package plana.replan.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
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
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
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
        .andExpect(jsonPath("$.data").doesNotExist());
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
        .andExpect(jsonPath("$.data").doesNotExist());
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
        .andExpect(jsonPath("$.error").doesNotExist());
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
        .andExpect(jsonPath("$.data").doesNotExist());
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
}
