package plana.replan.domain.routine.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(RoutineController.class)
@Import(SecurityConfig.class)
class RoutineControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RoutineService routineService;

  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  @DisplayName("인증 없이 루틴 생성 호출: Security가 차단, 401 반환")
  void createRoutine_unauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/routines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "루틴", "routineType": "DAILY" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("DAILY 루틴 생성 성공: status=201, 필수 필드 검증")
  void createRoutine_daily_success() throws Exception {
    given(routineService.createRoutine(any(), any()))
        .willReturn(
            new RoutineResponseDto(
                1L,
                "아침 스트레칭",
                null,
                null,
                null,
                RoutineType.DAILY,
                null,
                null,
                null,
                null,
                null,
                null,
                10000.0,
                false,
                false,
                false,
                false));

    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "아침 스트레칭", "routineType": "DAILY" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.routineId").value(1))
        .andExpect(jsonPath("$.data.title").value("아침 스트레칭"))
        .andExpect(jsonPath("$.data.routineType").value("DAILY"))
        .andExpect(jsonPath("$.data.routineDays").value(nullValue()))
        .andExpect(jsonPath("$.data.tagId").value(nullValue()))
        .andExpect(jsonPath("$.data.goalId").value(nullValue()))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("WEEKLY 루틴 생성 성공 (전체 필드): routineDays=[0,2,4], tagId/goalId 포함")
  void createRoutine_weekly_success_fullFields() throws Exception {
    LocalDateTime dueDate = LocalDateTime.of(2025, 12, 31, 0, 0);
    given(routineService.createRoutine(any(), any()))
        .willReturn(
            new RoutineResponseDto(
                2L,
                "영어 단어",
                null,
                dueDate,
                null,
                RoutineType.WEEKLY,
                java.util.List.of(0, 2, 4),
                5L,
                null,
                null,
                2L,
                null,
                10000.0,
                false,
                false,
                false,
                false));

    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "영어 단어",
                      "dueDate": "2025-12-31T00:00:00",
                      "routineType": "WEEKLY",
                      "routineDays": [0, 2, 4],
                      "tagId": 5,
                      "goalId": 2
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.routineId").value(2))
        .andExpect(jsonPath("$.data.routineType").value("WEEKLY"))
        .andExpect(jsonPath("$.data.routineDays[0]").value(0))
        .andExpect(jsonPath("$.data.tagId").value(5))
        .andExpect(jsonPath("$.data.goalId").value(2))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("MONTHLY 루틴 생성 성공: routineDays=[15]")
  void createRoutine_monthly_success() throws Exception {
    given(routineService.createRoutine(any(), any()))
        .willReturn(
            new RoutineResponseDto(
                3L,
                "월간 회고",
                null,
                null,
                null,
                RoutineType.MONTHLY,
                java.util.List.of(15),
                null,
                null,
                null,
                null,
                null,
                10000.0,
                false,
                false,
                false,
                false));

    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "월간 회고", "routineType": "MONTHLY", "routineDays": [15] }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.routineType").value("MONTHLY"))
        .andExpect(jsonPath("$.data.routineDays[0]").value(15));
  }

  @Test
  @DisplayName("title 누락: status=400, error.code=INVALID_INPUT")
  void createRoutine_missingTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "routineType": "DAILY" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("title 빈 문자열: status=400, error.code=INVALID_INPUT")
  void createRoutine_blankTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "", "routineType": "DAILY" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("routineType 누락: status=400, error.code=INVALID_INPUT")
  void createRoutine_missingRoutineType() throws Exception {
    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "루틴" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("routineType에 잘못된 값: JSON 파싱 실패, status=400, error.code=INVALID_INPUT")
  void createRoutine_invalidRoutineType() throws Exception {
    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "루틴", "routineType": "INVALID_TYPE" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("routineDays 범위 오류: status=400, error.code=ROUTINE_INVALID_DATE")
  void createRoutine_invalidRoutineDate() throws Exception {
    willThrow(new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE))
        .given(routineService)
        .createRoutine(any(), any());

    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "루틴", "routineType": "WEEKLY", "routineDays": [7] }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_DATE"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않는 tagId: status=404, error.code=TAG_NOT_FOUND")
  void createRoutine_tagNotFound() throws Exception {
    willThrow(new CustomException(TagErrorCode.TAG_NOT_FOUND))
        .given(routineService)
        .createRoutine(any(), any());

    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "루틴", "routineType": "DAILY", "tagId": 999 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TAG_NOT_FOUND"));
  }

  @Test
  @DisplayName("존재하지 않는 goalId: status=404, error.code=GOAL_NOT_FOUND")
  void createRoutine_goalNotFound() throws Exception {
    willThrow(new CustomException(GoalErrorCode.GOAL_NOT_FOUND))
        .given(routineService)
        .createRoutine(any(), any());

    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "루틴", "routineType": "DAILY", "goalId": 999 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("GOAL_NOT_FOUND"));
  }

  @Test
  @DisplayName("userId가 DB에 없는 경우: status=404, error.code=USER_NOT_FOUND")
  void createRoutine_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(routineService)
        .createRoutine(any(), any());

    mockMvc
        .perform(
            post("/api/routines")
                .with(authentication(authToken(999L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "루틴", "routineType": "DAILY" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
  }

  // ========== updateMotherRoutine (PUT /api/routines/{id}) ==========

  @Test
  @DisplayName("인증 없이 엄마 루틴 수정 호출: 401 반환")
  void updateMotherRoutine_unauthenticated() throws Exception {
    mockMvc
        .perform(
            put("/api/routines/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정", "routineType": "DAILY" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("엄마 루틴 수정 성공: status=200, 수정된 필드 반환")
  void updateMotherRoutine_success() throws Exception {
    LocalDateTime dueDate = LocalDateTime.of(2025, 12, 31, 0, 0);
    given(routineService.updateMotherRoutine(any(), any(), any()))
        .willReturn(
            new RoutineResponseDto(
                1L,
                "수정된 루틴",
                null,
                dueDate,
                null,
                RoutineType.WEEKLY,
                java.util.List.of(0, 2, 4),
                5L,
                "영어",
                "BLUE",
                null,
                null,
                10000.0,
                false,
                false,
                false,
                false));

    mockMvc
        .perform(
            put("/api/routines/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "수정된 루틴",
                      "dueDate": "2025-12-31T00:00:00",
                      "routineType": "WEEKLY",
                      "routineDays": [0, 2, 4],
                      "tagId": 5
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.title").value("수정된 루틴"))
        .andExpect(jsonPath("$.data.routineType").value("WEEKLY"))
        .andExpect(jsonPath("$.data.routineDays[0]").value(0))
        .andExpect(jsonPath("$.data.tagId").value(5))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("하위 루틴 ID로 수정 시도: status=400, error.code=ROUTINE_INVALID_TARGET")
  void updateMotherRoutine_childRoutine_throws() throws Exception {
    willThrow(new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET))
        .given(routineService)
        .updateMotherRoutine(any(), any(), any());

    mockMvc
        .perform(
            put("/api/routines/11")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정", "routineType": "DAILY" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_TARGET"));
  }

  @Test
  @DisplayName("routineDays 범위 오류: status=400, error.code=ROUTINE_INVALID_DATE")
  void updateMotherRoutine_invalidRoutineDate() throws Exception {
    willThrow(new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE))
        .given(routineService)
        .updateMotherRoutine(any(), any(), any());

    mockMvc
        .perform(
            put("/api/routines/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정", "routineType": "WEEKLY", "routineDays": [7] }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_DATE"));
  }

  @Test
  @DisplayName("존재하지 않는 tagId: status=404, error.code=TAG_NOT_FOUND")
  void updateMotherRoutine_tagNotFound() throws Exception {
    willThrow(new CustomException(TagErrorCode.TAG_NOT_FOUND))
        .given(routineService)
        .updateMotherRoutine(any(), any(), any());

    mockMvc
        .perform(
            put("/api/routines/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정", "routineType": "DAILY", "tagId": 999 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TAG_NOT_FOUND"));
  }

  @Test
  @DisplayName("루틴을 찾을 수 없음: status=404, error.code=ROUTINE_NOT_FOUND")
  void updateMotherRoutine_routineNotFound() throws Exception {
    willThrow(new CustomException(RoutineErrorCode.ROUTINE_NOT_FOUND))
        .given(routineService)
        .updateMotherRoutine(any(), any(), any());

    mockMvc
        .perform(
            put("/api/routines/999")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정", "routineType": "DAILY" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ROUTINE_NOT_FOUND"));
  }

  // ========== getRoutinesByFilter: date 파라미터 형식 오류 ==========

  @Test
  @DisplayName("filter=day, date 형식 오류: status=400, error.code=INVALID_INPUT (500 아님)")
  void getRoutinesByFilter_day_malformedDate_returns400NotInternalServerError() throws Exception {
    mockMvc
        .perform(get("/api/routines?filter=day&date=notadate").with(authentication(authToken(1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("filter=all, date 형식 오류: status=400, error.code=INVALID_INPUT (500 아님)")
  void getRoutinesByFilter_all_malformedDate_returns400NotInternalServerError() throws Exception {
    mockMvc
        .perform(get("/api/routines?filter=all&date=notadate").with(authentication(authToken(1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }
}
