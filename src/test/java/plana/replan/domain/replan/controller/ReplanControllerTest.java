package plana.replan.domain.replan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import plana.replan.domain.replan.dto.QuestionType;
import plana.replan.domain.replan.dto.ReplanAnchorTodo;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.service.ReplanService;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(ReplanController.class)
@Import(SecurityConfig.class)
class ReplanControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReplanService replanService;
  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  void 추천_엔드포인트_추천반환() throws Exception {
    given(replanService.recommend(eq(1L), any()))
        .willReturn(
            ReplanRecommendResponse.recommendation(
                List.of(), List.of("예상치 못한 방해 발생", "돌발 상황이 발생했어요")));

    mockMvc
        .perform(
            post("/api/replans/recommend")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"anchorTodoId":42,"reasonCodes":["INTERRUPT_SUDDEN"],"answers":[]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.needsMoreInfo").value(false))
        .andExpect(jsonPath("$.data.reasonLabels[0]").value("예상치 못한 방해 발생"));
  }

  @Test
  void 추천_엔드포인트_질문반환() throws Exception {
    given(replanService.recommend(eq(1L), any()))
        .willReturn(
            ReplanRecommendResponse.askQuestions(
                List.of(
                    new ReplanQuestion(
                        "priority_targets", QuestionType.TODO_SELECT, "투두 선택", null)),
                new ReplanAnchorTodo(
                    42L,
                    "데이터 분석 공부하기",
                    LocalDateTime.of(2026, 6, 25, 11, 0),
                    3L,
                    "Study",
                    "#FAD7A0",
                    null)));

    mockMvc
        .perform(
            post("/api/replans/recommend")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"anchorTodoId":42,"reasonCodes":["GOAL_NO_PRIORITY"],"answers":[]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.needsMoreInfo").value(true))
        .andExpect(jsonPath("$.data.questions[0].type").value("TODO_SELECT"))
        .andExpect(jsonPath("$.data.questions[0].key").value("priority_targets"))
        // 질문 단계엔 reasonLabels 대신 앵커 투두의 기존 정보가 내려간다
        .andExpect(jsonPath("$.data.reasonLabels").doesNotExist())
        .andExpect(jsonPath("$.data.anchorTodo.todoId").value(42))
        .andExpect(jsonPath("$.data.anchorTodo.title").value("데이터 분석 공부하기"))
        .andExpect(jsonPath("$.data.anchorTodo.tagTitle").value("Study"));
  }

  @Test
  void 저장_엔드포인트_성공() throws Exception {
    willDoNothing().given(replanService).save(eq(1L), any());

    mockMvc
        .perform(
            post("/api/replans")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"anchorTodoId":42,"reasonCodes":["GOAL_NO_PRIORITY"],"acceptedOperations":[]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
