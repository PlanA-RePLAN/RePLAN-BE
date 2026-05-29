package plana.replan.domain.routine.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
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

@Service
@RequiredArgsConstructor
public class RoutineService {

  private final Clock clock;
  private final RoutineRepository routineRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final GoalRepository goalRepository;
  private final TodoRepository todoRepository;

  @Transactional
  public RoutineResponseDto createRoutine(Long userId, RoutineCreateRequestDto request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    validateRoutineDate(request.routineType(), request.routineDate());

    Tag tag = null;
    if (request.tagId() != null) {
      tag =
          tagRepository
              .findById(request.tagId())
              .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
    }

    Goal goal = null;
    if (request.goalId() != null) {
      goal =
          goalRepository
              .findById(request.goalId())
              .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
    }

    Integer routineDate = request.routineType() == RoutineType.DAILY ? null : request.routineDate();

    Routine routine =
        routineRepository.save(
            Routine.builder()
                .title(request.title())
                .dueDate(request.dueDate())
                .routineType(request.routineType())
                .routineDate(routineDate)
                .user(user)
                .tag(tag)
                .goal(goal)
                .build());

    createTodoFromRoutine(routine);

    return RoutineResponseDto.from(routine);
  }

  @Transactional
  public void generateDailyTodos() {
    LocalDate yesterday = LocalDate.now(clock).minusDays(1);
    LocalDateTime start = yesterday.atStartOfDay();
    LocalDateTime end = yesterday.atTime(23, 59, 59);
    todoRepository
        .findRoutineTodosByDueDateRange(start, end)
        .forEach(todo -> createTodoFromRoutine(todo.getRoutine()));
  }

  public void createTodoFromRoutine(Routine routine) {
    LocalDate today = LocalDate.now(clock);
    LocalDateTime dueDate = nextOccurrence(routine, today).atStartOfDay();
    if (todoRepository.existsByRoutineAndDueDateBetween(routine, dueDate, dueDate.plusDays(1))) {
      return;
    }
    try {
      todoRepository.saveAndFlush(
          Todo.builder()
              .title(routine.getTitle())
              .dueDate(dueDate)
              .isPinned(false)
              .user(routine.getUser())
              .tag(routine.getTag())
              .goal(routine.getGoal())
              .routine(routine)
              .build());
    } catch (DataIntegrityViolationException e) {
      String msg = e.getMostSpecificCause().getMessage();
      if (msg == null || !msg.contains("uq_todo_routine_duedate")) {
        throw e;
      }
      // uq_todo_routine_duedate 충돌 — 동시 실행으로 이미 생성된 것으로 간주
    }
  }

  private LocalDate nextOccurrence(Routine routine, LocalDate today) {
    return switch (routine.getRoutineType()) {
      case DAILY -> today;
      case WEEKLY -> {
        int mask = routine.getRoutineDate();
        for (int i = 0; i < 7; i++) {
          LocalDate d = today.plusDays(i);
          int bit = 1 << (d.getDayOfWeek().getValue() - 1);
          if ((mask & bit) != 0) {
            yield d;
          }
        }
        yield today;
      }
      case MONTHLY -> {
        int day = routine.getRoutineDate();
        for (int k = 0; k < 12; k++) {
          LocalDate firstOfMonth = today.withDayOfMonth(1).plusMonths(k);
          if (firstOfMonth.lengthOfMonth() >= day) {
            LocalDate candidate = firstOfMonth.withDayOfMonth(day);
            if (!candidate.isBefore(today)) {
              yield candidate;
            }
          }
        }
        yield today;
      }
    };
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
}
