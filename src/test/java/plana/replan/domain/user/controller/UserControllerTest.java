package plana.replan.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import plana.replan.domain.user.service.UserService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @MockitoBean private JwtUtil jwtUtil;

  @Test
  @DisplayName("인증 없이 /me 호출: Security가 차단, 403 반환")
  void getMyInfo_unauthenticated() throws Exception {
    mockMvc.perform(get("/api/users/me")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("인증 후 /me 호출 성공: status=200, success=true, data.email 존재, error 필드 없음")
  void getMyInfo_success() throws Exception {
    UserResponseDto mockUser =
        new UserResponseDto(1L, "test@test.com", "테스트", Role.ROLE_USER, Provider.LOCAL);
    given(userService.getMyInfo(any())).willReturn(mockUser);

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

    mockMvc
        .perform(get("/api/users/me").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("test@test.com"))
        .andExpect(jsonPath("$.data.userId").value(1))
        .andExpect(jsonPath("$.error").doesNotExist());
  }
}
