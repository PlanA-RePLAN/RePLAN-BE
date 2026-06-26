package plana.replan.domain.notification.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import plana.replan.domain.notification.service.NotificationTriggerService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(NotificationDevController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("local")
class NotificationDevControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotificationTriggerService notificationTriggerService;

  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  @DisplayName("인증된 사용자가 trigger-due-soon 호출 → 200 반환, sendDueSoon 실행됨")
  void triggerDueSoon_authenticated_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/dev/trigger-due-soon").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(notificationTriggerService, times(1)).sendDueSoon();
  }

  @Test
  @DisplayName("인증된 사용자가 trigger-failed-replan 호출 → 200 반환, sendFailedReplan 실행됨")
  void triggerFailedReplan_authenticated_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/dev/trigger-failed-replan")
                .with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(notificationTriggerService, times(1)).sendFailedReplan();
  }

  @Test
  @DisplayName("인증 없이 trigger-due-soon 호출 → 401 반환")
  void triggerDueSoon_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/api/notifications/dev/trigger-due-soon"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("인증 없이 trigger-failed-replan 호출 → 401 반환")
  void triggerFailedReplan_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/api/notifications/dev/trigger-failed-replan"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }
}
