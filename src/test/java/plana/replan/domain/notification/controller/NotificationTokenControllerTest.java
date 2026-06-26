package plana.replan.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.service.DeviceTokenService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(NotificationTokenController.class)
@Import(SecurityConfig.class)
class NotificationTokenControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DeviceTokenService deviceTokenService;

  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  // ── POST /api/notifications/tokens ───────────────────────────────────────

  @Test
  @DisplayName("기기 토큰 등록 성공: status=200")
  void registerToken_success() throws Exception {
    willDoNothing().given(deviceTokenService).register(any(), any());

    mockMvc
        .perform(
            post("/api/notifications/tokens")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token": "fcm-token-xyz", "platform": "WEB"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  @DisplayName("토큰 빈 문자열로 등록 요청: status=400, error.code=INVALID_INPUT")
  void registerToken_blankToken() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/tokens")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token": "", "platform": "WEB"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  // ── DELETE /api/notifications/tokens ─────────────────────────────────────

  @Test
  @DisplayName("등록되지 않은 토큰 삭제 요청: status=404, error.code=TOKEN_NOT_FOUND")
  void deleteToken_notFound() throws Exception {
    willThrow(new CustomException(NotificationErrorCode.TOKEN_NOT_FOUND))
        .given(deviceTokenService)
        .delete(any(), any());

    mockMvc
        .perform(
            delete("/api/notifications/tokens")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token": "nonexistent-token"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("TOKEN_NOT_FOUND"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}
