package plana.replan.domain.goal.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.dto.create.CreatedTodoItem;
import plana.replan.domain.goal.dto.create.GoalWithTodosCreateRequest;
import plana.replan.domain.goal.dto.create.GoalWithTodosCreateResponse;
import plana.replan.domain.goal.dto.create.TodoItemRequest;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineCreateRequestDto;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class GoalWithTodosService {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final UserRepository userRepository;
  private final GoalService goalService;
  private final TodoService todoService;
  private final RoutineService routineService;

  @Transactional
  public GoalWithTodosCreateResponse create(Long userId, GoalWithTodosCreateRequest request) {
    userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    Long goalId =
        goalService
            .createGoal(
                userId,
                new plana.replan.domain.goal.dto.create.GoalCreateRequest(
                    request.title(), request.dueDate(), request.dueTime(), request.reference()))
            .id();

    List<CreatedTodoItem> createdItems = new ArrayList<>();
    for (TodoItemRequest item : request.todos()) {
      if ("RECURRING".equals(item.type())) {
        createdItems.add(createRoutine(userId, goalId, item));
      } else if ("ONE_TIME".equals(item.type())) {
        createdItems.add(createTodo(userId, goalId, item));
      } else {
        throw new CustomException(GoalErrorCode.TODO_INVALID_TYPE);
      }
    }

    return new GoalWithTodosCreateResponse(goalId, createdItems);
  }

  private CreatedTodoItem createTodo(Long userId, Long goalId, TodoItemRequest item) {
    if (item.subRoutines() != null && !item.subRoutines().isEmpty()) {
      throw new CustomException(GoalErrorCode.TODO_SUB_ROUTINE_NOT_ALLOWED_FOR_ONE_TIME);
    }
    if (item.subTodos() != null && !item.subTodos().isEmpty()) {
      validateSubTodos(item.subTodos());
    }

    TodoCreateRequestDto dto = buildTodoCreateRequest(item, goalId);
    TodoResponseDto todo = todoService.createTodo(userId, dto);

    if (item.subTodos() != null) {
      for (String subTitle : item.subTodos()) {
        todoService.createSubTodo(userId, todo.getTodoId(), new SubTodoCreateRequestDto(subTitle));
      }
    }

    return CreatedTodoItem.ofTodo(todo.getTodoId(), todo.getTitle());
  }

  private CreatedTodoItem createRoutine(Long userId, Long goalId, TodoItemRequest item) {
    if (item.subTodos() != null && !item.subTodos().isEmpty()) {
      throw new CustomException(GoalErrorCode.TODO_SUB_TODO_NOT_ALLOWED_FOR_RECURRING);
    }
    if (item.subRoutines() != null && !item.subRoutines().isEmpty()) {
      validateSubRoutines(item.subRoutines());
    }

    RoutineCreateRequestDto dto = buildRoutineCreateRequest(item, goalId);
    var routine = routineService.createRoutine(userId, dto);

    List<Long> subRoutineIds = new ArrayList<>();
    if (item.subRoutines() != null) {
      for (String childTitle : item.subRoutines()) {
        var child =
            routineService.createChildRoutine(
                userId, routine.getRoutineId(), new SubRoutineCreateRequestDto(childTitle));
        subRoutineIds.add(child.routineId());
      }
    }

    return CreatedTodoItem.ofRoutine(routine.getRoutineId(), routine.getTitle(), subRoutineIds);
  }

  private TodoCreateRequestDto buildTodoCreateRequest(TodoItemRequest item, Long goalId) {
    return new TodoCreateRequestDto(
        item.title(), parseDateTime(item.dueDate(), item.dueTime()), item.tagId(), goalId);
  }

  private RoutineCreateRequestDto buildRoutineCreateRequest(TodoItemRequest item, Long goalId) {
    Integer routineDate = item.routineType() == RoutineType.DAILY ? null : item.routineDate();
    return new RoutineCreateRequestDto(
        item.title(),
        parseDateTime(item.dueDate(), item.dueTime()),
        null,
        item.routineType(),
        routineDate,
        item.tagId(),
        goalId);
  }

  private void validateSubTodos(List<String> subTodos) {
    for (String title : subTodos) {
      if (title == null || title.isBlank()) {
        throw new CustomException(GoalErrorCode.TODO_INVALID_TYPE);
      }
    }
  }

  private void validateSubRoutines(List<String> subRoutines) {
    for (String title : subRoutines) {
      if (title == null || title.isBlank()) {
        throw new CustomException(GoalErrorCode.TODO_SUB_ROUTINE_INVALID_TITLE);
      }
    }
  }

  private LocalDateTime parseDateTime(String date, String time) {
    if (date == null && time != null) {
      throw new CustomException(GoalErrorCode.TODO_DUE_TIME_WITHOUT_DATE);
    }
    if (date == null) return null;
    LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
    LocalTime localTime =
        (time != null) ? LocalTime.parse(time, TIME_FORMATTER) : LocalTime.MIDNIGHT;
    return LocalDateTime.of(localDate, localTime);
  }
}
