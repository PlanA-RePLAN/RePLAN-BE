package plana.replan.domain.tag.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import plana.replan.domain.tag.dto.TagResponseDto;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.service.TagService;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.SecurityConfig;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;
import plana.replan.global.jwt.JwtUtil;

@WebMvcTest(TagController.class)
@Import(SecurityConfig.class)
class TagControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TagService tagService;
  @MockitoBean private JwtUtil jwtUtil;

  private UsernamePasswordAuthenticationToken authToken(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  // ── createTag ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 태그 생성 호출: 401 반환")
  void createTag_unauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "영어" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("태그 생성 성공 (색상 포함): status=201, data 필드 검증")
  void createTag_success_withColor() throws Exception {
    given(tagService.createTag(any(), any())).willReturn(new TagResponseDto(1L, "영어", "BLUE"));

    mockMvc
        .perform(
            post("/api/tags")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "영어", "color": "BLUE" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tagId").value(1))
        .andExpect(jsonPath("$.data.title").value("영어"))
        .andExpect(jsonPath("$.data.color").value("BLUE"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("태그 생성 성공 (색상 없음): status=201, color=null 반환")
  void createTag_success_withoutColor() throws Exception {
    given(tagService.createTag(any(), any())).willReturn(new TagResponseDto(2L, "독서", null));

    mockMvc
        .perform(
            post("/api/tags")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "독서" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.tagId").value(2))
        .andExpect(jsonPath("$.data.color").value(nullValue()));
  }

  @Test
  @DisplayName("title 누락: status=400, error.code=INVALID_INPUT")
  void createTag_missingTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/tags")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @DisplayName("title 빈 문자열: status=400, error.code=INVALID_INPUT")
  void createTag_blankTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/tags")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("userId DB에 없음: status=404, error.code=USER_NOT_FOUND")
  void createTag_userNotFound() throws Exception {
    willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
        .given(tagService)
        .createTag(any(), any());

    mockMvc
        .perform(
            post("/api/tags")
                .with(authentication(authToken(999L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "영어" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
  }

  // ── updateTag ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 태그 수정 호출: 401 반환")
  void updateTag_unauthenticated() throws Exception {
    mockMvc
        .perform(
            put("/api/tags/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "업무" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("태그 수정 성공: status=200, 수정된 필드 반환")
  void updateTag_success() throws Exception {
    given(tagService.updateTag(any(), any(), any()))
        .willReturn(new TagResponseDto(1L, "업무", "RED"));

    mockMvc
        .perform(
            put("/api/tags/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "업무", "color": "RED" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tagId").value(1))
        .andExpect(jsonPath("$.data.title").value("업무"))
        .andExpect(jsonPath("$.data.color").value("RED"))
        .andExpect(jsonPath("$.error").value(nullValue()));
  }

  @Test
  @DisplayName("title 생략: status=200 (title은 선택 필드)")
  void updateTag_omitTitle_ok() throws Exception {
    given(tagService.updateTag(any(), any(), any()))
        .willReturn(new TagResponseDto(1L, "영어", "RED"));

    mockMvc
        .perform(
            put("/api/tags/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "color": "RED" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("영어"));
  }

  @Test
  @DisplayName("title 빈 문자열: 서비스에서 INVALID_INPUT 반환 → status=400")
  void updateTag_blankTitle() throws Exception {
    willThrow(new CustomException(GlobalErrorCode.INVALID_INPUT))
        .given(tagService)
        .updateTag(any(), any(), any());

    mockMvc
        .perform(
            put("/api/tags/1")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 태그: status=404, error.code=TAG_NOT_FOUND")
  void updateTag_notFound() throws Exception {
    willThrow(new CustomException(TagErrorCode.TAG_NOT_FOUND))
        .given(tagService)
        .updateTag(any(), any(), any());

    mockMvc
        .perform(
            put("/api/tags/999")
                .with(authentication(authToken(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "업무" }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TAG_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // ── deleteTag ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("인증 없이 태그 삭제 호출: 401 반환")
  void deleteTag_unauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/tags/1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("EMPTY_TOKEN"));
  }

  @Test
  @DisplayName("태그 삭제 성공: status=200, data=성공 메시지")
  void deleteTag_success() throws Exception {
    willDoNothing().given(tagService).deleteTag(any(), any());

    mockMvc
        .perform(delete("/api/tags/1").with(authentication(authToken(1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("태그가 성공적으로 삭제되었습니다."));
  }

  @Test
  @DisplayName("존재하지 않거나 본인 소유가 아닌 태그: status=404, error.code=TAG_NOT_FOUND")
  void deleteTag_notFound() throws Exception {
    willThrow(new CustomException(TagErrorCode.TAG_NOT_FOUND))
        .given(tagService)
        .deleteTag(any(), any());

    mockMvc
        .perform(delete("/api/tags/999").with(authentication(authToken(1L))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TAG_NOT_FOUND"));
  }
}
