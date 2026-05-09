package plana.replan.domain.goal.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import plana.replan.domain.goal.dto.GoalPageResponse;
import plana.replan.domain.goal.dto.GoalResponse;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.service.GoalService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(GoalController.class)
@Import(SecurityConfig.class)
class GoalControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GoalService goalService;
  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  // ========== POST /api/goals ==========

  @Test
  void 목표_생성_성공() throws Exception {
    GoalResponse mockResponse =
        new GoalResponse(
            42L,
            "토익 900점 달성",
            LocalDateTime.of(2025, 12, 31, 0, 0),
            "https://toeic.ets.org",
            LocalDateTime.of(2025, 5, 7, 12, 0));
    given(goalService.createGoal(any(), any())).willReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/goals")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "토익 900점 달성",
                      "dueDate": "2025-12-31T00:00:00",
                      "reference": "https://toeic.ets.org"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42))
        .andExpect(jsonPath("$.data.title").value("토익 900점 달성"))
        .andExpect(jsonPath("$.data.reference").value("https://toeic.ets.org"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  void 목표_생성_title_없으면_400() throws Exception {
    mockMvc
        .perform(
            post("/api/goals")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").exists());
  }

  @Test
  void 목표_생성_인증_없으면_401() throws Exception {
    mockMvc
        .perform(
            post("/api/goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "토익 900점 달성"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  // ========== DELETE /api/goals/{id} ==========

  @Test
  void 목표_삭제_성공() throws Exception {
    willDoNothing().given(goalService).deleteGoal(any(), any());

    mockMvc
        .perform(delete("/api/goals/42").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  void 목표_삭제_목표_없으면_404() throws Exception {
    willThrow(new CustomException(GoalErrorCode.GOAL_NOT_FOUND))
        .given(goalService)
        .deleteGoal(any(), any());

    mockMvc
        .perform(delete("/api/goals/999").with(authentication(authToken(1L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("GOAL_NOT_FOUND"));
  }

  @Test
  void 목표_삭제_타인_목표_403() throws Exception {
    willThrow(new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED))
        .given(goalService)
        .deleteGoal(any(), any());

    mockMvc
        .perform(delete("/api/goals/42").with(authentication(authToken(2L))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("GOAL_ACCESS_DENIED"));
  }

  @Test
  void 목표_삭제_인증_없으면_401() throws Exception {
    mockMvc
        .perform(delete("/api/goals/42"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  // ========== GET /api/goals ==========

  @Test
  void 목표_조회_성공_기본() throws Exception {
    GoalResponse goal = new GoalResponse(42L, "토익 900점", null, null, LocalDateTime.now());
    GoalPageResponse mockResponse = new GoalPageResponse(List.of(goal), null, false);
    given(goalService.getGoals(any(), any(), anyInt(), any())).willReturn(mockResponse);

    mockMvc
        .perform(get("/api/goals").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.goals[0].id").value(42))
        .andExpect(jsonPath("$.data.goals[0].title").value("토익 900점"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
  }

  @Test
  void 목표_조회_성공_커서와_연도_파라미터() throws Exception {
    GoalPageResponse mockResponse = new GoalPageResponse(List.of(), null, false);
    given(goalService.getGoals(any(), anyLong(), anyInt(), anyInt())).willReturn(mockResponse);

    mockMvc
        .perform(
            get("/api/goals")
                .param("cursor", "37")
                .param("size", "5")
                .param("year", "2025")
                .with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200));
  }

  @Test
  void 목표_조회_인증_없으면_401() throws Exception {
    mockMvc
        .perform(get("/api/goals"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }
}
