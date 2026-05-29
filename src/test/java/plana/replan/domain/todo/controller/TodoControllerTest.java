package plana.replan.domain.todo.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.todo.dto.TodoDetailResponseDto;
import plana.replan.domain.todo.dto.TodoDetailResponseDto.SubTodoDto;
import plana.replan.domain.todo.dto.TodoListResponseDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.exception.TodoErrorCode;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;
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

  @Test
  @DisplayName("인증 없이 하위 투두 생성 호출: Security가 차단, 401 반환")
  void createSubTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/todos/10/sub-todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "하위 투두" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("하위 투두 생성 성공: status=201, parentId 포함")
  void createSubTodo_success() throws Exception {
    given(todoService.createSubTodo(any(), any(), any()))
        .willReturn(new TodoResponseDto(43L, "하위 투두", null, false, null, 10L));

    mockMvc
        .perform(
            post("/api/todos/10/sub-todos")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "하위 투두" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoId").value(43))
        .andExpect(jsonPath("$.data.title").value("하위 투두"))
        .andExpect(jsonPath("$.data.parentId").value(10))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("title 누락: status=400, error.code=INVALID_INPUT")
  void createSubTodo_missingTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/todos/10/sub-todos")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("title 빈 문자열: status=400, error.code=INVALID_INPUT")
  void createSubTodo_blankTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/todos/10/sub-todos")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("userId가 DB에 없는 경우: status=404, error.code=USER_NOT_FOUND")
  void createSubTodo_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(todoService)
        .createSubTodo(any(), any(), any());

    mockMvc
        .perform(
            post("/api/todos/10/sub-todos")
                .with(authentication(authToken(999L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "하위 투두" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않는 parentId: status=404, error.code=TODO_NOT_FOUND")
  void createSubTodo_parentNotFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .createSubTodo(any(), any(), any());

    mockMvc
        .perform(
            post("/api/todos/999/sub-todos")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "하위 투두" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
  }

  // ── updateSubTodo ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 하위 투두 수정 호출: 401 반환")
  void updateSubTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(
            put("/api/todos/10/sub-todos/43")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "수정 제목" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("하위 투두 수정 성공: status=200, 수정된 title 반환")
  void updateSubTodo_success() throws Exception {
    given(todoService.updateSubTodo(any(), any(), any(), any()))
        .willReturn(new TodoResponseDto(43L, "수정 제목", null, false, null, 10L));

    mockMvc
        .perform(
            put("/api/todos/10/sub-todos/43")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "수정 제목" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoId").value(43))
        .andExpect(jsonPath("$.data.title").value("수정 제목"))
        .andExpect(jsonPath("$.data.parentId").value(10))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("title 누락 (수정): status=400, error.code=INVALID_INPUT")
  void updateSubTodo_missingTitle() throws Exception {
    mockMvc
        .perform(
            put("/api/todos/10/sub-todos/43")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 하위 투두 수정: status=404, error.code=TODO_NOT_FOUND")
  void updateSubTodo_notFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .updateSubTodo(any(), any(), any(), any());

    mockMvc
        .perform(
            put("/api/todos/10/sub-todos/999")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "수정 제목" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
  }

  // ── deleteSubTodo ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 하위 투두 삭제 호출: 401 반환")
  void deleteSubTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/todos/10/sub-todos/43"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("하위 투두 삭제 성공: status=204, 응답 바디 없음")
  void deleteSubTodo_success() throws Exception {
    willDoNothing().given(todoService).deleteSubTodo(any(), any(), any());

    mockMvc
        .perform(delete("/api/todos/10/sub-todos/43").with(authentication(authToken(1L))))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 하위 투두 삭제: status=404, error.code=TODO_NOT_FOUND")
  void deleteSubTodo_notFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .deleteSubTodo(any(), any(), any());

    mockMvc
        .perform(delete("/api/todos/10/sub-todos/999").with(authentication(authToken(1L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
  }

  // ── getTodos ──────────────────────────────────────────────────────────────

  // todoId, title, dueDate, isPinned, sortOrder, isCompleted, tagId, tagTitle, tagColor,
  // routineType, isOverdue
  private TodoListResponseDto sampleDto(Long id, boolean isCompleted, boolean isOverdue) {
    return new TodoListResponseDto(
        id, "투두 " + id, null, false, 1000.0, isCompleted, null, null, null, null, isOverdue);
  }

  @Test
  @DisplayName("인증 없이 투두 목록 조회: Security가 차단, 401 반환")
  void getTodos_unauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/todos"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("투두 목록 조회 성공 (기본 filter=all, sort=priority): status=200, 배열 반환")
  void getTodos_success_defaultParams() throws Exception {
    given(todoService.getTodos(any(), any(), any(), any()))
        .willReturn(List.of(sampleDto(1L, false, false), sampleDto(2L, false, false)));

    mockMvc
        .perform(get("/api/todos").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].todoId").value(1))
        .andExpect(jsonPath("$.data[1].todoId").value(2))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("투두 목록 조회 - isOverdue/태그/루틴 필드 포함 응답 검증")
  void getTodos_success_responseFieldsVerified() throws Exception {
    TodoListResponseDto dto =
        new TodoListResponseDto(
            1L, "투두", null, true, 500.0, false, 3L, "영어", "BLUE", "DAILY", true);
    given(todoService.getTodos(any(), any(), any(), any())).willReturn(List.of(dto));

    mockMvc
        .perform(get("/api/todos").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].isPinned").value(true))
        .andExpect(jsonPath("$.data[0].sortOrder").value(500.0))
        .andExpect(jsonPath("$.data[0].tagId").value(3))
        .andExpect(jsonPath("$.data[0].tagTitle").value("영어"))
        .andExpect(jsonPath("$.data[0].tagColor").value("BLUE"))
        .andExpect(jsonPath("$.data[0].routineType").value("DAILY"))
        .andExpect(jsonPath("$.data[0].isOverdue").value(true));
  }

  @Test
  @DisplayName("filter=day: status=200, 완료 투두 포함")
  void getTodos_success_dayFilter() throws Exception {
    given(todoService.getTodos(any(), any(), any(), any()))
        .willReturn(List.of(sampleDto(1L, false, false), sampleDto(2L, true, false)));

    mockMvc
        .perform(get("/api/todos?filter=day").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].isCompleted").value(false))
        .andExpect(jsonPath("$.data[1].isCompleted").value(true));
  }

  @Test
  @DisplayName("filter=week, sort=dueDate: status=200")
  void getTodos_success_weekFilter_dueDateSort() throws Exception {
    given(todoService.getTodos(any(), any(), any(), any()))
        .willReturn(List.of(sampleDto(1L, false, false)));

    mockMvc
        .perform(get("/api/todos?filter=week&sort=dueDate").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  @DisplayName("유효하지 않은 filter: status=400, error.code=INVALID_FILTER")
  void getTodos_invalidFilter() throws Exception {
    willThrow(new CustomException(TodoErrorCode.INVALID_FILTER))
        .given(todoService)
        .getTodos(any(), any(), any(), any());

    mockMvc
        .perform(get("/api/todos?filter=invalid").with(authentication(authToken(1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_FILTER"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("유효하지 않은 sort: status=400, error.code=INVALID_SORT")
  void getTodos_invalidSort() throws Exception {
    willThrow(new CustomException(TodoErrorCode.INVALID_SORT))
        .given(todoService)
        .getTodos(any(), any(), any(), any());

    mockMvc
        .perform(get("/api/todos?sort=invalid").with(authentication(authToken(1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_SORT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("userId가 DB에 없는 경우: status=404, error.code=USER_NOT_FOUND")
  void getTodos_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(todoService)
        .getTodos(any(), any(), any(), any());

    mockMvc
        .perform(get("/api/todos").with(authentication(authToken(999L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── getTodoDetail ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 투두 상세 조회: 401 반환")
  void getTodoDetail_unauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/todos/1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("투두 상세 조회 성공 (하위 투두 있음): status=200, 모든 필드 검증")
  void getTodoDetail_success() throws Exception {
    TodoDetailResponseDto response =
        new TodoDetailResponseDto(
            1L,
            "토익 단어 50개 외우기",
            null,
            false,
            3L,
            "영어",
            "BLUE",
            "DAILY",
            List.of(new SubTodoDto(10L, "챕터 1 읽기", false)));

    given(todoService.getTodoDetail(any(), any())).willReturn(response);

    mockMvc
        .perform(get("/api/todos/1").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoId").value(1))
        .andExpect(jsonPath("$.data.title").value("토익 단어 50개 외우기"))
        .andExpect(jsonPath("$.data.tagId").value(3))
        .andExpect(jsonPath("$.data.tagTitle").value("영어"))
        .andExpect(jsonPath("$.data.tagColor").value("BLUE"))
        .andExpect(jsonPath("$.data.routineType").value("DAILY"))
        .andExpect(jsonPath("$.data.subTodos[0].todoId").value(10))
        .andExpect(jsonPath("$.data.subTodos[0].title").value("챕터 1 읽기"))
        .andExpect(jsonPath("$.data.subTodos[0].isCompleted").value(false))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않는 todoId: status=404, error.code=TODO_NOT_FOUND")
  void getTodoDetail_notFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .getTodoDetail(any(), any());

    mockMvc
        .perform(get("/api/todos/99").with(authentication(authToken(1L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── getPinnedTodos ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 핀된 투두 조회 호출: 401 반환")
  void getPinnedTodos_unauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/todos/pinned"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("핀된 투두 목록 조회 성공: status=200, isPinned=true인 목록 반환")
  void getPinnedTodos_success() throws Exception {
    List<TodoListResponseDto> pinnedList =
        List.of(
            new TodoListResponseDto(
                3L, "중요 투두", null, true, 500.0, false, null, null, null, null, false),
            new TodoListResponseDto(
                1L, "긴급 투두", null, true, 1000.0, false, null, null, null, null, false));
    given(todoService.getPinnedTodos(any())).willReturn(pinnedList);

    mockMvc
        .perform(get("/api/todos/pinned").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].todoId").value(3))
        .andExpect(jsonPath("$.data[0].isPinned").value(true))
        .andExpect(jsonPath("$.data[1].todoId").value(1))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("핀된 투두 없음: status=200, 빈 배열 반환")
  void getPinnedTodos_empty() throws Exception {
    given(todoService.getPinnedTodos(any())).willReturn(List.of());

    mockMvc
        .perform(get("/api/todos/pinned").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @DisplayName("userId DB에 없음: status=404, error.code=USER_NOT_FOUND")
  void getPinnedTodos_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(todoService)
        .getPinnedTodos(any());

    mockMvc
        .perform(get("/api/todos/pinned").with(authentication(authToken(999L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
  }

  // ── deleteTodo ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 투두 삭제 호출: 401 반환")
  void deleteTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/todos/1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("투두 삭제 성공: status=204, 응답 바디 없음")
  void deleteTodo_success() throws Exception {
    willDoNothing().given(todoService).deleteTodo(any(), any());

    mockMvc
        .perform(delete("/api/todos/1").with(authentication(authToken(1L))))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 투두: status=404, error.code=TODO_NOT_FOUND")
  void deleteTodo_notFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .deleteTodo(any(), any());

    mockMvc
        .perform(delete("/api/todos/99").with(authentication(authToken(1L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
  }

  // ── reorderTodo ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 우선순위 변경 호출: 401 반환")
  void reorderTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(
            patch("/api/todos/1/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "prevTodoId": 1, "nextTodoId": 3 }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("두 항목 사이 삽입 성공: status=200, 중간 sortOrder 반환")
  void reorderTodo_success_between() throws Exception {
    TodoListResponseDto response =
        new TodoListResponseDto(
            2L, "투두", null, false, 15000.0, false, null, null, null, null, false);
    given(todoService.reorderTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            patch("/api/todos/2/order")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "prevTodoId": 1, "nextTodoId": 3 }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.sortOrder").value(15000.0))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("맨 앞으로 이동 (prevTodoId=null): status=200")
  void reorderTodo_success_toFront() throws Exception {
    TodoListResponseDto response =
        new TodoListResponseDto(
            2L, "투두", null, false, 5000.0, false, null, null, null, null, false);
    given(todoService.reorderTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            patch("/api/todos/2/order")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "nextTodoId": 3 }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sortOrder").value(5000.0));
  }

  @Test
  @DisplayName("prevTodoId와 nextTodoId 모두 null: status=400, error.code=INVALID_INPUT")
  void reorderTodo_bothNull() throws Exception {
    willThrow(new CustomException(GlobalErrorCode.INVALID_INPUT))
        .given(todoService)
        .reorderTodo(any(), any(), any());

    mockMvc
        .perform(
            patch("/api/todos/1/order")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 투두: status=404, error.code=TODO_NOT_FOUND")
  void reorderTodo_notFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .reorderTodo(any(), any(), any());

    mockMvc
        .perform(
            patch("/api/todos/999/order")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "nextTodoId": 3 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
  }

  // ── completeTodo ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 완료/미완료 처리 호출: 401 반환")
  void completeTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(
            patch("/api/todos/1/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isCompleted": true }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("완료 처리 성공: status=200, isCompleted=true 반환")
  void completeTodo_success_complete() throws Exception {
    TodoListResponseDto response =
        new TodoListResponseDto(1L, "투두", null, false, 1000.0, true, null, null, null, null, false);
    given(todoService.completeTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            patch("/api/todos/1/complete")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isCompleted": true }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoId").value(1))
        .andExpect(jsonPath("$.data.isCompleted").value(true))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("미완료 처리 성공: status=200, isCompleted=false 반환")
  void completeTodo_success_uncomplete() throws Exception {
    TodoListResponseDto response =
        new TodoListResponseDto(
            1L, "투두", null, false, 1000.0, false, null, null, null, null, false);
    given(todoService.completeTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            patch("/api/todos/1/complete")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isCompleted": false }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isCompleted").value(false))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("isCompleted 누락: status=400, error.code=INVALID_INPUT")
  void completeTodo_missingIsCompleted() throws Exception {
    mockMvc
        .perform(
            patch("/api/todos/1/complete")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 투두: status=404, error.code=TODO_NOT_FOUND")
  void completeTodo_notFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .completeTodo(any(), any(), any());

    mockMvc
        .perform(
            patch("/api/todos/999/complete")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isCompleted": true }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── pinTodo ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 핀/언핀 호출: 401 반환")
  void pinTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(
            patch("/api/todos/1/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isPinned": true }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("핀 설정 성공: status=200, isPinned=true 반환")
  void pinTodo_success_pin() throws Exception {
    TodoListResponseDto response =
        new TodoListResponseDto(
            1L, "중요 투두", null, true, 1000.0, false, null, null, null, null, false);
    given(todoService.pinTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            patch("/api/todos/1/pin")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isPinned": true }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoId").value(1))
        .andExpect(jsonPath("$.data.isPinned").value(true))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("핀 해제 성공: status=200, isPinned=false 반환")
  void pinTodo_success_unpin() throws Exception {
    TodoListResponseDto response =
        new TodoListResponseDto(
            1L, "중요 투두", null, false, 1000.0, false, null, null, null, null, false);
    given(todoService.pinTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            patch("/api/todos/1/pin")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isPinned": false }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isPinned").value(false))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("isPinned 누락: status=400, error.code=INVALID_INPUT")
  void pinTodo_missingIsPinned() throws Exception {
    mockMvc
        .perform(
            patch("/api/todos/1/pin")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 투두: status=404, error.code=TODO_NOT_FOUND")
  void pinTodo_notFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .pinTodo(any(), any(), any());

    mockMvc
        .perform(
            patch("/api/todos/999/pin")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "isPinned": true }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── updateTodo ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 투두 수정 호출: 401 반환")
  void updateTodo_unauthenticated() throws Exception {
    mockMvc
        .perform(
            put("/api/todos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "수정된 제목" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("투두 수정 성공: status=200, TodoDetailResponseDto 반환")
  void updateTodo_success() throws Exception {
    TodoDetailResponseDto response =
        new TodoDetailResponseDto(1L, "수정된 제목", null, false, 3L, "영어", "BLUE", "WEEKLY", List.of());

    given(todoService.updateTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/todos/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정된 제목", "tagId": 3, "routineType": "WEEKLY", "routineDate": 5 }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.todoId").value(1))
        .andExpect(jsonPath("$.data.title").value("수정된 제목"))
        .andExpect(jsonPath("$.data.tagId").value(3))
        .andExpect(jsonPath("$.data.tagTitle").value("영어"))
        .andExpect(jsonPath("$.data.tagColor").value("BLUE"))
        .andExpect(jsonPath("$.data.routineType").value("WEEKLY"))
        .andExpect(jsonPath("$.data.subTodos").isArray())
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("title 생략: status=200 (title은 선택 필드)")
  void updateTodo_omitTitle_ok() throws Exception {
    TodoDetailResponseDto response =
        new TodoDetailResponseDto(1L, "기존 제목", null, false, null, null, null, null, List.of());
    given(todoService.updateTodo(any(), any(), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/todos/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("기존 제목"));
  }

  @Test
  @DisplayName("title 빈 문자열: 서비스에서 INVALID_INPUT 반환 → status=400")
  void updateTodo_blankTitle() throws Exception {
    willThrow(new CustomException(GlobalErrorCode.INVALID_INPUT))
        .given(todoService)
        .updateTodo(any(), any(), any());

    mockMvc
        .perform(
            put("/api/todos/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않는 todoId: status=404, error.code=TODO_NOT_FOUND")
  void updateTodo_todoNotFound() throws Exception {
    willThrow(new CustomException(TodoErrorCode.TODO_NOT_FOUND))
        .given(todoService)
        .updateTodo(any(), any(), any());

    mockMvc
        .perform(
            put("/api/todos/999")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "수정된 제목" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("존재하지 않는 tagId: status=404, error.code=TAG_NOT_FOUND")
  void updateTodo_tagNotFound() throws Exception {
    willThrow(new CustomException(TagErrorCode.TAG_NOT_FOUND))
        .given(todoService)
        .updateTodo(any(), any(), any());

    mockMvc
        .perform(
            put("/api/todos/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정된 제목", "tagId": 999 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TAG_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("잘못된 반복 날짜: status=400, error.code=ROUTINE_INVALID_DATE")
  void updateTodo_routineInvalidDate() throws Exception {
    willThrow(new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE))
        .given(todoService)
        .updateTodo(any(), any(), any());

    mockMvc
        .perform(
            put("/api/todos/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "수정된 제목", "routineType": "WEEKLY", "routineDate": 200 }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_DATE"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }
}
