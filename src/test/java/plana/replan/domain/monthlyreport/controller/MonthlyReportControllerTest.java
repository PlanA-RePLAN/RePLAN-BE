package plana.replan.domain.monthlyreport.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import plana.replan.domain.monthlyreport.dto.MonthlyReportResponse;
import plana.replan.domain.monthlyreport.exception.MonthlyReportErrorCode;
import plana.replan.domain.monthlyreport.service.MonthlyReportService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(MonthlyReportController.class)
@Import(SecurityConfig.class)
class MonthlyReportControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MonthlyReportService monthlyReportService;
  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken auth(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  private MonthlyReportResponse sampleResponse() {
    return new MonthlyReportResponse(2025, 5, 30, 21, 70.00, 5.50, 3, 66.67, null, null);
  }

  // ── 인증 ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 호출: status=401, error.code=EMPTY_TOKEN")
  void getReport_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/monthly-reports").param("year", "2025").param("month", "5"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  // ── 성공 ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("조회 성공: status=200, data 필드 검증")
  void getReport_success_returns200() throws Exception {
    given(monthlyReportService.getReport(any(), anyInt(), anyInt())).willReturn(sampleResponse());

    mockMvc
        .perform(
            get("/api/monthly-reports")
                .with(authentication(auth(1L)))
                .param("year", "2025")
                .param("month", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.year").value(2025))
        .andExpect(jsonPath("$.data.month").value(5))
        .andExpect(jsonPath("$.data.totalTodos").value(30))
        .andExpect(jsonPath("$.data.completedTodos").value(21))
        .andExpect(jsonPath("$.data.achievementRate").value(70.00))
        .andExpect(jsonPath("$.data.prevMonthDiff").value(5.50))
        .andExpect(jsonPath("$.data.replanCount").value(3))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("analysisData, aiInsight 모두 null인 응답: data.analysisData=null, data.aiInsight=null")
  void getReport_nullAnalysis_nullFieldsReturned() throws Exception {
    given(monthlyReportService.getReport(any(), anyInt(), anyInt()))
        .willReturn(new MonthlyReportResponse(2025, 5, 0, 0, 0.0, null, 0, null, null, null));

    mockMvc
        .perform(
            get("/api/monthly-reports")
                .with(authentication(auth(1L)))
                .param("year", "2025")
                .param("month", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.analysisData").value(nullValue()))
        .andExpect(jsonPath("$.data.aiInsight").value(nullValue()))
        .andExpect(jsonPath("$.data.prevMonthDiff").value(nullValue()));
  }

  // ── 에러 ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("userId DB에 없음: status=404, error.code=NOT_FOUND")
  void getReport_userNotFound_returns404() throws Exception {
    willThrow(new CustomException(GlobalErrorCode.NOT_FOUND))
        .given(monthlyReportService)
        .getReport(any(), anyInt(), anyInt());

    mockMvc
        .perform(
            get("/api/monthly-reports")
                .with(authentication(auth(999L)))
                .param("year", "2025")
                .param("month", "5"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("해당 월 리포트 없음: status=404, error.code=REPORT_NOT_FOUND")
  void getReport_reportNotFound_returns404() throws Exception {
    willThrow(new CustomException(MonthlyReportErrorCode.REPORT_NOT_FOUND))
        .given(monthlyReportService)
        .getReport(any(), anyInt(), anyInt());

    mockMvc
        .perform(
            get("/api/monthly-reports")
                .with(authentication(auth(1L)))
                .param("year", "2025")
                .param("month", "5"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }
}
