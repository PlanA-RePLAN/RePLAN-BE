package plana.replan.domain.todo.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(TodoController.class)
@Import(SecurityConfig.class)
class TodoControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TodoService todoService;

  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  @DisplayName("인증 없이 투두 생성 호출: Security가 차단, 401 반환")
  void createTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/todos/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "테스트 투두" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("투두 생성 성공 (tagId 없음): status=201, success=true, data 필드 검증")
  void createTodo_success_withoutTag() throws Exception {
    given(todoService.createTodo(any(), any()))
        .willReturn(new TodoResponseDto(1L, "테스트 투두", null, false, null, null));

    mockMvc
        .perform(
            post("/api/todos/create")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "테스트 투두" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoId").value(1))
        .andExpect(jsonPath("$.data.title").value("테스트 투두"))
        .andExpect(jsonPath("$.data.dueDate").value(nullValue()))
        .andExpect(jsonPath("$.data.tagId").value(nullValue()))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("투두 생성 성공 (tagId 있음): status=201, data.tagId 포함")
  void createTodo_success_withTag() throws Exception {
    given(todoService.createTodo(any(), any()))
        .willReturn(new TodoResponseDto(1L, "테스트 투두", null, false, 5L, null));

    mockMvc
        .perform(
            post("/api/todos/create")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "테스트 투두", "tagId": 5 }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tagId").value(5))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("title 누락: status=400, error.code=INVALID_INPUT")
  void createTodo_missingTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/todos/create")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("title 빈 문자열: status=400, error.code=INVALID_INPUT")
  void createTodo_blankTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/todos/create")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않는 tagId: status=404, error.code=TAG_NOT_FOUND")
  void createTodo_tagNotFound() throws Exception {
    willThrow(new CustomException(TagErrorCode.TAG_NOT_FOUND))
        .given(todoService)
        .createTodo(any(), any());

    mockMvc
        .perform(
            post("/api/todos/create")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "테스트 투두", "tagId": 999 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("TAG_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("userId가 DB에 없는 경우: status=404, error.code=USER_NOT_FOUND")
  void createTodo_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(todoService)
        .createTodo(any(), any());

    mockMvc
        .perform(
            post("/api/todos/create")
                .with(authentication(authToken(999L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "테스트 투두" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }
}
