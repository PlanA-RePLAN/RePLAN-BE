package plana.replan.domain.goal.service;

import java.time.Clock;
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
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@Service
@RequiredArgsConstructor
public class GoalWithTodosService {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final Clock clock;
  private final GoalRepository goalRepository;
  private final TodoRepository todoRepository;
  private final RoutineRepository routineRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;

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
                .dueDate(request.dueDate())
                .reference(request.reference())
                .user(user)
                .build());

    List<CreatedTodoItem> createdItems = new ArrayList<>();
    for (TodoItemRequest item : request.todos()) {
      if ("RECURRING".equals(item.type())) {
        createdItems.add(createRoutine(user, goal, item));
      } else {
        createdItems.add(createTodo(user, goal, item));
      }
    }

    return new GoalWithTodosCreateResponse(goal.getId(), createdItems);
  }

  private CreatedTodoItem createTodo(User user, Goal goal, TodoItemRequest item) {
    Tag tag = resolveTag(user, item.tagId());

    LocalDateTime dueDate = parseDueDate(item.dueDate(), item.dueTime());

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
    Tag tag = resolveTag(user, item.tagId());

    LocalDateTime dueDate = parseDueDate(item.dueDate(), item.dueTime());
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

    if (isTodayMatch(routine)) {
      todoRepository.save(
          Todo.builder()
              .title(routine.getTitle())
              .dueDate(routine.getDueDate())
              .isPinned(false)
              .user(user)
              .tag(tag)
              .goal(goal)
              .routine(routine)
              .build());
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

  private LocalDateTime parseDueDate(String date, String time) {
    if (date == null && time != null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    if (date == null) return null;
    LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
    LocalTime localTime =
        (time != null) ? LocalTime.parse(time, TIME_FORMATTER) : LocalTime.MIDNIGHT;
    return LocalDateTime.of(localDate, localTime);
  }

  private boolean isTodayMatch(Routine routine) {
    LocalDate today = LocalDate.now(clock);
    return switch (routine.getRoutineType()) {
      case DAILY -> true;
      case WEEKLY -> {
        int todayBit = 1 << (today.getDayOfWeek().getValue() - 1);
        yield routine.getRoutineDate() != null && (routine.getRoutineDate() & todayBit) != 0;
      }
      case MONTHLY -> routine.getRoutineDate() != null
          && routine.getRoutineDate() == today.getDayOfMonth();
    };
  }
}
