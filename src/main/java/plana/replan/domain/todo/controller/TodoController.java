package plana.replan.domain.todo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController implements TodoControllerDocs {

  private final TodoService todoService;

  @Override
  @PostMapping("/create")
  public ResponseEntity<ApiResult<TodoResponseDto>> createTodo(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody TodoCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.ok(todoService.createTodo(userId, request)));
  }

  @Override
  @PostMapping("/{parentId}/sub-todos")
  public ResponseEntity<ApiResult<TodoResponseDto>> createSubTodo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long parentId,
      @Valid @RequestBody SubTodoCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.ok(todoService.createSubTodo(userId, parentId, request)));
  }
}
