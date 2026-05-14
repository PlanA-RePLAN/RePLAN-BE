package plana.replan.domain.goal.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import plana.replan.domain.goal.dto.GoalSingleResponseDto;
import plana.replan.domain.goal.dto.GoalsByDateResponseDto;
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
    GoalSingleResponseDto mockResponse =
        new GoalSingleResponseDto(
            42L, "토익 900점 달성", LocalDateTime.of(2025, 12, 31, 0, 0), "https://toeic.ets.org");
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
  void 목표_조회_성공_전체() throws Exception {
    GoalSingleResponseDto goal =
        new GoalSingleResponseDto(10L, "토익 900점", LocalDateTime.of(2026, 5, 26, 20, 0), null);
    GoalsByDateResponseDto dateGroup =
        new GoalsByDateResponseDto(LocalDate.of(2026, 5, 4), List.of(goal));
    given(goalService.getGoals(any(), any(), any())).willReturn(List.of(dateGroup));

    mockMvc
        .perform(get("/api/goals").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].date").value("2026-05-04"))
        .andExpect(jsonPath("$.data[0].goals[0].id").value(10))
        .andExpect(jsonPath("$.data[0].goals[0].title").value("토익 900점"));
  }

  @Test
  void 목표_조회_성공_연도_월_파라미터() throws Exception {
    given(goalService.getGoals(any(), any(), any())).willReturn(List.of());

    mockMvc
        .perform(
            get("/api/goals")
                .param("year", "2026")
                .param("month", "5")
                .with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200));
  }

  @Test
  void 목표_조회_year없이_month만_전달하면_400() throws Exception {
    willThrow(new CustomException(GoalErrorCode.GOAL_INVALID_FILTER))
        .given(goalService)
        .getGoals(any(), any(), any());

    mockMvc
        .perform(get("/api/goals").param("month", "5").with(authentication(authToken(1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error.code").value("GOAL_INVALID_FILTER"));
  }

  @Test
  void 목표_조회_month_범위_벗어나면_400() throws Exception {
    willThrow(new CustomException(GoalErrorCode.GOAL_INVALID_MONTH))
        .given(goalService)
        .getGoals(any(), any(), any());

    mockMvc
        .perform(
            get("/api/goals")
                .param("year", "2026")
                .param("month", "13")
                .with(authentication(authToken(1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error.code").value("GOAL_INVALID_MONTH"));
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
