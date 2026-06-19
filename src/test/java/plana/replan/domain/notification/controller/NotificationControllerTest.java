package plana.replan.domain.notification.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import plana.replan.domain.notification.dto.NotificationListResponse;
import plana.replan.domain.notification.dto.NotificationResponse;
import plana.replan.domain.notification.dto.NotificationSettingResponse;
import plana.replan.domain.notification.dto.UnreadCountResponse;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.domain.notification.service.NotificationSettingService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotificationService notificationService;

  @MockitoBean private NotificationSettingService notificationSettingService;

  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  // ── GET /api/notifications ────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 알림 목록 조회: Security가 차단, 401 반환")
  void getNotifications_unauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/notifications"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("알림 목록 조회 성공: status=200, items·nextCursor·hasNext 필드 반환")
  void getNotifications_success() throws Exception {
    NotificationResponse item =
        new NotificationResponse(
            12L,
            "TODO",
            "TODO_DUE_SOON",
            "테스트 알림 제목",
            "내용",
            "TODO",
            9L,
            false,
            "2026-06-19T00:00:00");
    NotificationListResponse response = new NotificationListResponse(List.of(item), null, false);
    given(notificationService.getList(any(), any(), any(), anyInt())).willReturn(response);

    mockMvc
        .perform(get("/api/notifications").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items[0].id").value(12))
        .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  // ── GET /api/notifications/unread-count ───────────────────────────────────

  @Test
  @DisplayName("안 읽은 알림 개수 조회 성공: status=200, data.count 반환")
  void getUnreadCount_success() throws Exception {
    given(notificationService.getUnreadCount(any())).willReturn(new UnreadCountResponse(3L));

    mockMvc
        .perform(get("/api/notifications/unread-count").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.count").value(3))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  // ── PATCH /api/notifications/{id}/read ───────────────────────────────────

  @Test
  @DisplayName("존재하지 않는 알림 읽음 처리: status=404, error.code=NOTIFICATION_NOT_FOUND")
  void readOne_notFound() throws Exception {
    willThrow(new CustomException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
        .given(notificationService)
        .markRead(any(), any());

    mockMvc
        .perform(patch("/api/notifications/999/read").with(authentication(authToken(1L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  // ── PATCH /api/notifications/read-all ────────────────────────────────────

  @Test
  @DisplayName("전체 읽음 처리 성공: status=200")
  void readAll_success() throws Exception {
    willDoNothing().given(notificationService).markAllRead(any());

    mockMvc
        .perform(patch("/api/notifications/read-all").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  // ── GET /api/notifications/settings ──────────────────────────────────────

  @Test
  @DisplayName("알림 설정 조회 성공: status=200, todoDue·todoFailed·report 필드 반환")
  void getSettings_success() throws Exception {
    given(notificationSettingService.get(any()))
        .willReturn(new NotificationSettingResponse(true, false, true));

    mockMvc
        .perform(get("/api/notifications/settings").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoDue").value(true))
        .andExpect(jsonPath("$.data.todoFailed").value(false))
        .andExpect(jsonPath("$.data.report").value(true))
        .andExpect(jsonPath("$.error").doesNotExist());
  }
}
