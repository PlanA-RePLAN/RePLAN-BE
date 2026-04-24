package plana.replan.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
  @DisplayName("인증 없이 /me 호출: Security가 차단, 401 반환")
  void getMyInfo_unauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("인증 후 /me 호출 성공: status=200, success=true, data 필드 검증, error 필드 없음")
  void getMyInfo_success() throws Exception {
    UserResponseDto mockUser =
        new UserResponseDto(1L, "test@test.com", "테스트", Role.ROLE_USER, Provider.LOCAL);
    given(userService.getMyInfo(1L)).willReturn(mockUser);

    mockMvc
        .perform(get("/api/users/me").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(1))
        .andExpect(jsonPath("$.data.email").value("test@test.com"))
        .andExpect(jsonPath("$.data.nickname").value("테스트"))
        .andExpect(jsonPath("$.data.role").value("ROLE_USER"))
        .andExpect(jsonPath("$.data.provider").value("LOCAL"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  @DisplayName("userId가 DB에 없는 경우: USER_NOT_FOUND 예외 발생, status=404 반환")
  void getMyInfo_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(userService)
        .getMyInfo(any());

    mockMvc
        .perform(get("/api/users/me").with(authentication(authToken(999L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}
