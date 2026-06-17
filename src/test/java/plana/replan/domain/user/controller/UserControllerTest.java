package plana.replan.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import plana.replan.domain.user.dto.UserResponseDto;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.service.UserService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  @DisplayName("인증 없이 /profile 호출: Security가 차단, 401 반환")
  void getMyProfile_unauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/users/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("인증 후 /profile 호출 성공: status=200, success=true, data 필드 검증, error 필드 없음")
  void getMyProfile_success() throws Exception {
    UserResponseDto mockUser =
        new UserResponseDto(
            1L,
            "test@test.com",
            "테스트",
            Role.ROLE_USER,
            Provider.LOCAL,
            "https://cdn.example.com/profiles/confirmed/abc.png");
    given(userService.getMyInfo(1L)).willReturn(mockUser);

    mockMvc
        .perform(get("/api/users/profile").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(1))
        .andExpect(jsonPath("$.data.email").value("test@test.com"))
        .andExpect(jsonPath("$.data.nickname").value("테스트"))
        .andExpect(jsonPath("$.data.role").value("ROLE_USER"))
        .andExpect(jsonPath("$.data.provider").value("LOCAL"))
        .andExpect(
            jsonPath("$.data.profileImage")
                .value("https://cdn.example.com/profiles/confirmed/abc.png"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  @DisplayName("userId가 DB에 없는 경우: USER_NOT_FOUND 예외 발생, status=404 반환")
  void getMyProfile_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(userService)
        .getMyInfo(any());

    mockMvc
        .perform(get("/api/users/profile").with(authentication(authToken(999L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("인증 없이 PATCH /profile 호출: 401 반환")
  void updateMyProfile_unauthenticated() throws Exception {
    mockMvc
        .perform(
            patch("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("인증 후 PATCH /profile 성공: 수정된 정보 반환")
  void updateMyProfile_success() throws Exception {
    UserResponseDto updated =
        new UserResponseDto(
            1L,
            "test@test.com",
            "새닉네임",
            Role.ROLE_USER,
            Provider.LOCAL,
            "https://cdn.example.com/profiles/confirmed/new.png");
    given(userService.updateProfile(any(), any())).willReturn(updated);

    mockMvc
        .perform(
            patch("/api/users/profile")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\",\"profileImageKey\":\"profiles/temp/new.png\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
        .andExpect(
            jsonPath("$.data.profileImage")
                .value("https://cdn.example.com/profiles/confirmed/new.png"));
  }

  @Test
  @DisplayName("PATCH /profile 닉네임 중복: 409 반환")
  void updateMyProfile_duplicateNickname() throws Exception {
    willThrow(new CustomException(UserErrorCode.DUPLICATE_NICKNAME))
        .given(userService)
        .updateProfile(any(), any());

    mockMvc
        .perform(
            patch("/api/users/profile")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"중복닉네임\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_NICKNAME"));
  }

  @Test
  @DisplayName("인증 없이 DELETE /api/users 호출: 401 반환")
  void deleteMyAccount_unauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("인증 후 DELETE /api/users 성공: 200, data null")
  void deleteMyAccount_success() throws Exception {
    willDoNothing().given(userService).deleteAccount(1L);

    mockMvc
        .perform(delete("/api/users").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}
