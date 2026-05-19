package plana.replan.domain.todo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.exception.TodoErrorCode;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class TodoService {

  private final TodoRepository todoRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;

  @Transactional
  public TodoResponseDto createTodo(Long userId, TodoCreateRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    Tag tag = null;
    if (request.getTagId() != null) {
      tag =
          tagRepository
              .findById(request.getTagId())
              .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
    }

    Todo todo =
        Todo.builder()
            .title(request.getTitle())
            .dueDate(request.getDueDate())
            .isPinned(false)
            .user(user)
            .tag(tag)
            .build();

    todoRepository.save(todo);
    return TodoResponseDto.from(todo);
  }

  @Transactional
  public TodoResponseDto createSubTodo(
      Long userId, Long parentId, SubTodoCreateRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    Todo parent =
        todoRepository
            .findById(parentId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!parent.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    Todo subTodo =
        Todo.builder().title(request.getTitle()).user(user).parent(parent).isPinned(false).build();

    todoRepository.save(subTodo);
    return TodoResponseDto.from(subTodo);
  }
}
