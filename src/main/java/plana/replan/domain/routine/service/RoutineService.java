package plana.replan.domain.routine.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    if (isTodayMatch(routine)) {
      createTodoFromRoutine(routine);
    }

    return RoutineResponseDto.from(routine);
  }

  @Transactional
  public void generateDailyTodos() {
    List<Routine> routines = routineRepository.findAll();
    routines.stream().filter(this::isTodayMatch).forEach(this::createTodoFromRoutine);
  }

  public boolean isTodayMatch(Routine routine) {
    LocalDate today = LocalDate.now(clock);
    return switch (routine.getRoutineType()) {
      case DAILY -> true;
      case WEEKLY -> {
        int dayBit = 1 << (today.getDayOfWeek().getValue() - 1);
        yield routine.getRoutineDate() != null && (routine.getRoutineDate() & dayBit) != 0;
      }
      case MONTHLY -> routine.getRoutineDate() != null
          && routine.getRoutineDate() == today.getDayOfMonth();
    };
  }

  public void createTodoFromRoutine(Routine routine) {
    LocalDateTime todayStart = LocalDate.now(clock).atStartOfDay();
    if (todoRepository.existsByRoutineAndDueDateBetween(
        routine, todayStart, todayStart.plusDays(1))) {
      return;
    }
    try {
      todoRepository.saveAndFlush(
          Todo.builder()
              .title(routine.getTitle())
              .dueDate(todayStart)
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
