package plana.replan.domain.todo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.global.common.ApiResult;

@Tag(name = "Todo", description = "투두 관련 API")
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

  private final TodoService todoService;

  @Operation(
      summary = "투두 생성",
      description =
          """
                    **호출 주체**: AccessToken을 보유한 인증 사용자

                    **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

                    **비즈니스 로직**
                    1. JwtFilter에서 AccessToken 검증 후 userId를 SecurityContext에 저장
                    2. @AuthenticationPrincipal 로 userId 주입
                    3. title(필수), due_date(선택), tag_id(선택)를 받아 투두 생성
                    4. tag_id가 주어진 경우 태그 존재 여부 확인 후 연결

                    **참고**: tag_id가 제공된 경우 해당 태그가 존재하지 않으면 404 반환
                    """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "투두 생성 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 오류 (title 누락)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                                  {
                                    "status": 400,
                                    "success": false,
                                    "data": null,
                                    "error": {
                                      "code": "VALIDATION_ERROR",
                                      "message": "제목은 필수입니다.",
                                      "detail": null
                                    }
                                  }
                                  """))),
    @ApiResponse(responseCode = "401", description = "AccessToken 없음 또는 유효하지 않은 토큰"),
    @ApiResponse(
        responseCode = "404",
        description = "유저 또는 태그를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                                  {
                                    "status": 404,
                                    "success": false,
                                    "data": null,
                                    "error": {
                                      "code": "TAG_NOT_FOUND",
                                      "message": "태그를 찾을 수 없습니다.",
                                      "detail": null
                                    }
                                  }
                                  """)))
  })
  @PostMapping("/create")
  public ResponseEntity<ApiResult<TodoResponseDto>> createTodo(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody TodoCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.ok(todoService.createTodo(userId, request)));
  }
}
