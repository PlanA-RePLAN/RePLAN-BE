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
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class GoalWithTodosService {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final GoalRepository goalRepository;
  private final TodoRepository todoRepository;
  private final RoutineRepository routineRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final RoutineService routineService;

  @Transactional
  public GoalWithTodosCreateResponse create(Long userId, GoalWithTodosCreateRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    Goal goal =
        goalRepository.save(
            Goal.builder()
                .title(request.title())
                .dueDate(parseGoalDueDate(request.dueDate(), request.dueTime()))
                .reference(request.reference())
                .user(user)
                .build());

    List<CreatedTodoItem> createdItems = new ArrayList<>();
    for (TodoItemRequest item : request.todos()) {
      if ("RECURRING".equals(item.type())) {
        createdItems.add(createRoutine(user, goal, item));
      } else if ("ONE_TIME".equals(item.type())) {
        createdItems.add(createTodo(user, goal, item));
      } else {
        throw new CustomException(GoalErrorCode.TODO_INVALID_TYPE);
      }
    }

    return new GoalWithTodosCreateResponse(goal.getId(), createdItems);
  }

  private CreatedTodoItem createTodo(User user, Goal goal, TodoItemRequest item) {
    Tag tag = resolveTag(user, item.tagId());
    LocalDateTime dueDate = parseTodoDueDate(item.dueDate(), item.dueTime());

    Todo todo =
        todoRepository.save(
            Todo.builder()
                .title(item.title())
                .dueDate(dueDate)
                .isPinned(false)
                .user(user)
                .tag(tag)
                .goal(goal)
                .build());

    if (item.subTodos() != null) {
      for (String subTitle : item.subTodos()) {
        todoRepository.save(
            Todo.builder().title(subTitle).isPinned(false).user(user).parent(todo).build());
      }
    }

    return CreatedTodoItem.ofTodo(todo.getId(), todo.getTitle());
  }

  private CreatedTodoItem createRoutine(User user, Goal goal, TodoItemRequest item) {
    if (item.subTodos() != null && !item.subTodos().isEmpty()) {
      throw new CustomException(GoalErrorCode.TODO_SUB_TODO_NOT_ALLOWED_FOR_RECURRING);
    }

    Tag tag = resolveTag(user, item.tagId());
    LocalDateTime dueDate = parseTodoDueDate(item.dueDate(), item.dueTime());
    Integer routineDate = item.routineType() == RoutineType.DAILY ? null : item.routineDate();

    Routine routine =
        routineRepository.save(
            Routine.builder()
                .title(item.title())
                .dueDate(dueDate)
                .routineType(item.routineType())
                .routineDate(routineDate)
                .user(user)
                .tag(tag)
                .goal(goal)
                .build());

    if (routineService.isTodayMatch(routine)) {
      routineService.createTodoFromRoutine(routine);
    }

    return CreatedTodoItem.ofRoutine(routine.getId(), routine.getTitle());
  }

  private Tag resolveTag(User user, Long tagId) {
    if (tagId == null) return null;
    Tag tag =
        tagRepository
            .findById(tagId)
            .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
    if (!tag.getUser().getId().equals(user.getId())) {
      throw new CustomException(TagErrorCode.TAG_NOT_FOUND);
    }
    return tag;
  }

  private LocalDateTime parseGoalDueDate(String date, String time) {
    if (date == null && time != null) {
      throw new CustomException(GoalErrorCode.GOAL_DUE_TIME_WITHOUT_DATE);
    }
    return parseDateTime(date, time);
  }

  private LocalDateTime parseTodoDueDate(String date, String time) {
    if (date == null && time != null) {
      throw new CustomException(GoalErrorCode.TODO_DUE_TIME_WITHOUT_DATE);
    }
    return parseDateTime(date, time);
  }

  private LocalDateTime parseDateTime(String date, String time) {
    if (date == null) return null;
    LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
    LocalTime localTime =
        (time != null) ? LocalTime.parse(time, TIME_FORMATTER) : LocalTime.MIDNIGHT;
    return LocalDateTime.of(localDate, localTime);
  }
}
