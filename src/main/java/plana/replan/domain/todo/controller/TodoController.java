package plana.replan.domain.todo.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.SubTodoUpdateRequestDto;
import plana.replan.domain.todo.dto.TodoCompleteRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoDetailResponseDto;
import plana.replan.domain.todo.dto.TodoListResponseDto;
import plana.replan.domain.todo.dto.TodoOrderRequestDto;
import plana.replan.domain.todo.dto.TodoPinRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.dto.TodoUpdateRequestDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController implements TodoControllerDocs {

  private final TodoService todoService;

  @Override
  @GetMapping("/pinned")
  public ResponseEntity<ApiResult<List<TodoListResponseDto>>> getPinnedTodos(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(todoService.getPinnedTodos(userId)));
  }

  @Override
  @GetMapping("/{todoId}")
  public ResponseEntity<ApiResult<TodoDetailResponseDto>> getTodoDetail(
      @AuthenticationPrincipal Long userId, @PathVariable Long todoId) {
    return ResponseEntity.ok(ApiResult.ok(todoService.getTodoDetail(userId, todoId)));
  }

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<List<TodoListResponseDto>>> getTodos(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "all") String filter,
      @RequestParam(defaultValue = "priority") String sort) {
    return ResponseEntity.ok(ApiResult.ok(todoService.getTodos(userId, filter, sort)));
  }

  @Override
  @PatchMapping("/{todoId}/order")
  public ResponseEntity<ApiResult<TodoListResponseDto>> reorderTodo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long todoId,
      @RequestBody TodoOrderRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(todoService.reorderTodo(userId, todoId, request)));
  }

  @Override
  @PatchMapping("/{todoId}/complete")
  public ResponseEntity<ApiResult<TodoListResponseDto>> completeTodo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long todoId,
      @Valid @RequestBody TodoCompleteRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(todoService.completeTodo(userId, todoId, request)));
  }

  @Override
  @PatchMapping("/{todoId}/pin")
  public ResponseEntity<ApiResult<TodoListResponseDto>> pinTodo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long todoId,
      @Valid @RequestBody TodoPinRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(todoService.pinTodo(userId, todoId, request)));
  }

  @Override
  @DeleteMapping("/{todoId}")
  public ResponseEntity<Void> deleteTodo(
      @AuthenticationPrincipal Long userId, @PathVariable Long todoId) {
    todoService.deleteTodo(userId, todoId);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PutMapping("/{todoId}")
  public ResponseEntity<ApiResult<TodoDetailResponseDto>> updateTodo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long todoId,
      @Valid @RequestBody TodoUpdateRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(todoService.updateTodo(userId, todoId, request)));
  }

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

  @Override
  @PutMapping("/{parentId}/sub-todos/{subTodoId}")
  public ResponseEntity<ApiResult<TodoResponseDto>> updateSubTodo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long parentId,
      @PathVariable Long subTodoId,
      @Valid @RequestBody SubTodoUpdateRequestDto request) {
    return ResponseEntity.ok(
        ApiResult.ok(todoService.updateSubTodo(userId, parentId, subTodoId, request)));
  }

  @Override
  @DeleteMapping("/{parentId}/sub-todos/{subTodoId}")
  public ResponseEntity<Void> deleteSubTodo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long parentId,
      @PathVariable Long subTodoId) {
    todoService.deleteSubTodo(userId, parentId, subTodoId);
    return ResponseEntity.noContent().build();
  }
}
