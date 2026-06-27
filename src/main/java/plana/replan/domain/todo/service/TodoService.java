package plana.replan.domain.todo.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
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
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.exception.TodoErrorCode;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@Service
@RequiredArgsConstructor
public class TodoService {

  private final Clock clock;
  private final TodoRepository todoRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final RoutineRepository routineRepository;
  private final GoalRepository goalRepository;

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
      if (!tag.getUser().getId().equals(userId)) {
        throw new CustomException(TagErrorCode.TAG_NOT_FOUND);
      }
    }

    Goal goal = null;
    if (request.getGoalId() != null) {
      goal =
          goalRepository
              .findById(request.getGoalId())
              .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
    }

    double newSortOrder =
        todoRepository.findMaxSortOrderByUser(user).map(max -> max + 10000.0).orElse(10000.0);

    Todo todo =
        Todo.builder()
            .title(request.getTitle())
            .dueDate(request.getDueDate())
            .isPinned(false)
            .sortOrder(newSortOrder)
            .user(user)
            .tag(tag)
            .goal(goal)
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

    if (parent.getParent() != null) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    Todo subTodo =
        Todo.builder().title(request.getTitle()).user(user).parent(parent).isPinned(false).build();

    todoRepository.save(subTodo);
    return TodoResponseDto.from(subTodo);
  }

  @Transactional
  public TodoResponseDto updateSubTodo(
      Long userId, Long parentId, Long subTodoId, SubTodoUpdateRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Todo subTodo =
        todoRepository
            .findById(subTodoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!subTodo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    if (subTodo.getParent() == null || !subTodo.getParent().getId().equals(parentId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    subTodo.updateTitle(request.getTitle());
    return TodoResponseDto.from(subTodo);
  }

  @Transactional(readOnly = true)
  public List<TodoListResponseDto> getPinnedTodos(Long userId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    return todoRepository.findPinnedActiveTodosForUser(user).stream()
        .sorted(Comparator.comparingDouble(Todo::getSortOrder))
        .map(todo -> TodoListResponseDto.from(todo, clock))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<TodoListResponseDto> getTodos(
      Long userId, String filter, String sort, LocalDate date) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    if (filter == null) {
      throw new CustomException(TodoErrorCode.INVALID_FILTER);
    }
    if (sort == null) {
      throw new CustomException(TodoErrorCode.INVALID_SORT);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    Comparator<Todo> sortComparator = buildSortComparator(sort);

    LocalDate today = date != null ? date : LocalDate.now(clock);
    var startOfDay = today.atStartOfDay();
    var endOfDay = today.atTime(LocalTime.MAX);

    List<Todo> todos;
    switch (filter.toLowerCase()) {
      case "all" -> {
        todos = new ArrayList<>(todoRepository.findAllActiveTodosForUser(user));
        todos.sort(Comparator.comparing(Todo::isCompleted).thenComparing(sortComparator));
      }
      case "day" -> {
        List<Todo> active =
            todoRepository.findActiveTodosByDueDateRange(user, startOfDay, endOfDay);
        List<Todo> completed =
            todoRepository.findCompletedTodosByCompletedTimeRange(user, startOfDay, endOfDay);
        todos = new ArrayList<>(active);
        todos.addAll(completed);
        todos.sort(Comparator.comparing(Todo::isCompleted).thenComparing(sortComparator));
      }
      case "week" -> {
        LocalDate endDate = today.plusDays(6);
        todos =
            new ArrayList<>(
                todoRepository.findAllTodosByDueDateRange(
                    user, startOfDay, endDate.atTime(LocalTime.MAX)));
        todos.sort(Comparator.comparing(Todo::isCompleted).thenComparing(sortComparator));
      }
      case "month" -> {
        LocalDate endDate = today.plusMonths(1).minusDays(1);
        todos =
            new ArrayList<>(
                todoRepository.findAllTodosByDueDateRange(
                    user, startOfDay, endDate.atTime(LocalTime.MAX)));
        todos.sort(Comparator.comparing(Todo::isCompleted).thenComparing(sortComparator));
      }
      default -> throw new CustomException(TodoErrorCode.INVALID_FILTER);
    }

    return todos.stream()
        .map(todo -> TodoListResponseDto.from(todo, clock))
        .collect(Collectors.toList());
  }

  private Comparator<Todo> buildSortComparator(String sort) {
    return switch (sort) {
      case "priority" -> Comparator.comparingDouble(Todo::getSortOrder);
      case "dueDate" -> Comparator.comparing(
          Todo::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
      default -> throw new CustomException(TodoErrorCode.INVALID_SORT);
    };
  }

  @Transactional
  public TodoDetailResponseDto updateTodo(Long userId, Long todoId, TodoUpdateRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Todo todo =
        todoRepository
            .findById(todoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!todo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    Tag tag = null;
    if (request.getTagId() != null) {
      tag =
          tagRepository
              .findById(request.getTagId())
              .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
      if (!tag.getUser().getId().equals(userId)) {
        throw new CustomException(TagErrorCode.TAG_NOT_FOUND);
      }
    }

    if (request.getTitle() != null && request.getTitle().isBlank()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    if (request.getTitle() != null) {
      todo.updateTitle(request.getTitle());
    }
    if (todo.getRoutine() == null) {
      todo.updateDueDate(request.getDueDate());
    }
    todo.updateTag(tag);
    handleRoutineUpdate(todo, request, tag);

    return TodoDetailResponseDto.from(todo);
  }

  private void handleRoutineUpdate(Todo todo, TodoUpdateRequestDto request, Tag tag) {
    // 이미 반복에 연결된 Todo의 루틴 속성은 이 API에서 건드리지 않음.
    // 반복 스케줄 전체를 바꾸려면 PUT /api/routines/{id} 사용.
    if (todo.getRoutine() != null || request.getRoutineType() == null) {
      return;
    }
    validateRoutineDate(request.getRoutineType(), request.getRoutineDate());
    Integer routineDate =
        request.getRoutineType() == RoutineType.DAILY ? null : request.getRoutineDate();
    Routine newRoutine =
        routineRepository.save(
            Routine.builder()
                .title(todo.getTitle())
                .routineType(request.getRoutineType())
                .routineDate(routineDate)
                .routineTime(request.getRoutineTime())
                .user(todo.getUser())
                .tag(tag)
                .build());
    todo.updateRoutine(newRoutine);
  }

  private void validateRoutineDate(RoutineType routineType, Integer routineDate) {
    if (routineType == RoutineType.WEEKLY) {
      if (routineDate == null || routineDate < 1 || routineDate > 127) {
        throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE);
      }
    } else if (routineType == RoutineType.MONTHLY) {
      if (routineDate == null || routineDate < 1 || routineDate > 31) {
        throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE);
      }
    }
  }

  @Transactional(readOnly = true)
  public TodoDetailResponseDto getTodoDetail(Long userId, Long todoId) {
    Todo todo =
        todoRepository
            .findById(todoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!todo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    return TodoDetailResponseDto.from(todo);
  }

  @Transactional
  public TodoListResponseDto reorderTodo(Long userId, Long todoId, TodoOrderRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Todo todo =
        todoRepository
            .findById(todoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!todo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    if (todo.getParent() != null) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    if (request.getPrevTodoId() == null && request.getNextTodoId() == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    double prevSortOrder = 0;
    if (request.getPrevTodoId() != null) {
      Todo prev =
          todoRepository
              .findById(request.getPrevTodoId())
              .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));
      if (!prev.getUser().getId().equals(userId) || prev.getParent() != null) {
        throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
      }
      prevSortOrder = prev.getSortOrder();
    }

    double nextSortOrder = prevSortOrder + 20000;
    if (request.getNextTodoId() != null) {
      Todo next =
          todoRepository
              .findById(request.getNextTodoId())
              .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));
      if (!next.getUser().getId().equals(userId) || next.getParent() != null) {
        throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
      }
      nextSortOrder = next.getSortOrder();
    }

    todo.updateSortOrder((prevSortOrder + nextSortOrder) / 2);
    return TodoListResponseDto.from(todo, clock);
  }

  @Transactional
  public TodoListResponseDto completeTodo(
      Long userId, Long todoId, TodoCompleteRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Todo todo =
        todoRepository
            .findById(todoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!todo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    if (todo.getParent() != null) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    todo.updateCompleted(request.getIsCompleted(), LocalDateTime.now(clock));
    return TodoListResponseDto.from(todo, clock);
  }

  @Transactional
  public TodoListResponseDto pinTodo(Long userId, Long todoId, TodoPinRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Todo todo =
        todoRepository
            .findById(todoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!todo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    if (todo.getParent() != null) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    todo.updatePinned(request.getIsPinned());
    return TodoListResponseDto.from(todo, clock);
  }

  @Transactional
  public void deleteTodo(Long userId, Long todoId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Todo todo =
        todoRepository
            .findById(todoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!todo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    if (todo.getParent() != null) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    todo.getChildren().forEach(child -> child.softDelete());
    todo.softDelete();
  }

  @Transactional
  public void deleteSubTodo(Long userId, Long parentId, Long subTodoId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Todo subTodo =
        todoRepository
            .findById(subTodoId)
            .orElseThrow(() -> new CustomException(TodoErrorCode.TODO_NOT_FOUND));

    if (!subTodo.getUser().getId().equals(userId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    if (subTodo.getParent() == null || !subTodo.getParent().getId().equals(parentId)) {
      throw new CustomException(TodoErrorCode.TODO_NOT_FOUND);
    }

    subTodo.softDelete();
  }
}
