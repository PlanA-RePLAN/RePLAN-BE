package plana.replan.domain.routine.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.dto.SubRoutineCreateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineResponseDto;
import plana.replan.domain.routine.dto.SubRoutineUpdateRequestDto;
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
                .routineTime(request.routineTime())
                .routineType(request.routineType())
                .routineDate(routineDate)
                .user(user)
                .tag(tag)
                .goal(goal)
                .build());

    createTodoTreeFromMother(routine);

    return RoutineResponseDto.from(routine);
  }

  @Transactional
  public SubRoutineResponseDto createChildRoutine(
      Long userId, Long parentId, SubRoutineCreateRequestDto request) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }

    Routine parent =
        routineRepository
            .findById(parentId)
            .orElseThrow(() -> new CustomException(RoutineErrorCode.ROUTINE_NOT_FOUND));

    if (!parent.getUser().getId().equals(userId)) {
      throw new CustomException(RoutineErrorCode.ROUTINE_NOT_FOUND);
    }
    if (parent.isChild()) {
      // 2단계 중첩 금지 — 하위 루틴 ID를 parentId로 넘긴 경우
      throw new CustomException(RoutineErrorCode.ROUTINE_NOT_FOUND);
    }

    Routine child =
        routineRepository.save(
            Routine.builder().title(request.title()).user(parent.getUser()).parent(parent).build());

    // 엄마의 살아있는 다음 발생일 Todo가 있으면 즉시 매단다.
    LocalDateTime todayStart = LocalDate.now(clock).atStartOfDay();
    Optional<Todo> motherTodo =
        todoRepository.findFirstUpcomingMotherTodoByRoutine(parent, todayStart);
    motherTodo.ifPresent(mt -> attachChildTodoUnder(mt, child));

    return SubRoutineResponseDto.from(child);
  }

  /** 하위 루틴 title 수정. 엄마 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET). */
  @Transactional
  public SubRoutineResponseDto updateChildRoutine(
      Long userId, Long routineId, SubRoutineUpdateRequestDto request) {
    Routine routine = findOwnedRoutine(userId, routineId);
    if (routine.isMother()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET);
    }
    routine.updateTitle(request.title());
    return SubRoutineResponseDto.from(routine);
  }

  @Transactional(readOnly = true)
  public List<RoutineResponseDto> getRoutinesByDate(Long userId, LocalDate date) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    int dayBit = 1 << (date.getDayOfWeek().getValue() - 1);
    int dayOfMonth = date.getDayOfMonth();

    return routineRepository.findMotherRoutinesByDate(userId, dayBit, dayOfMonth).stream()
        .map(p -> RoutineResponseDto.from(p))
        .collect(Collectors.toList());
  }

  /** 엄마 루틴 전용 삭제. 하위 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET). */
  @Transactional
  public void deleteMotherRoutine(Long userId, Long routineId) {
    Routine routine = findOwnedRoutine(userId, routineId);
    if (routine.isChild()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET);
    }
    cascadeSoftDelete(routine);
  }

  /** 하위 루틴 전용 삭제. 엄마 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET). */
  @Transactional
  public void deleteChildRoutine(Long userId, Long routineId) {
    Routine routine = findOwnedRoutine(userId, routineId);
    if (routine.isMother()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET);
    }
    cascadeSoftDelete(routine);
  }

  private Routine findOwnedRoutine(Long userId, Long routineId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    Routine routine =
        routineRepository
            .findById(routineId)
            .orElseThrow(() -> new CustomException(RoutineErrorCode.ROUTINE_NOT_FOUND));
    if (!routine.getUser().getId().equals(userId)) {
      throw new CustomException(RoutineErrorCode.ROUTINE_NOT_FOUND);
    }
    return routine;
  }

  /**
   * TodoService.handleRoutineUpdate처럼 권한 검증이 이미 끝난 cross-domain 호출용. children 일괄 softDelete + 자기
   * softDelete 정책을 한 지점에 모으기 위한 헬퍼. 해당 루틴들을 참조하는 살아있는 Todo는 routine 참조를 null로 끊는다
   * — @SQLRestriction이 걸려있어 deleted routine을 lazy-load할 때 EntityNotFoundException이 터지기 때문 (목록 조회
   * 500 방지). 외부 public 진입점이므로 호출자 트랜잭션 유무와 무관하게 routine.getChildren() 지연 로딩이
   * 안전하도록 @Transactional(propagation = REQUIRED)로 트랜잭션을 보장한다.
   */
  @Transactional
  public void cascadeSoftDelete(Routine routine) {
    routine.getChildren().forEach(this::detachAndSoftDelete);
    detachAndSoftDelete(routine);
  }

  private void detachAndSoftDelete(Routine routine) {
    todoRepository.findAllByRoutine(routine).forEach(t -> t.updateRoutine(null));
    routine.softDelete();
  }

  @Transactional
  public void generateDailyTodos() {
    LocalDate yesterday = LocalDate.now(clock).minusDays(1);
    LocalDateTime start = yesterday.atStartOfDay();
    LocalDateTime end = yesterday.atTime(23, 59, 59);
    todoRepository
        .findMotherRoutineTodosForRollover(start, end)
        .forEach(todo -> createTodoTreeFromMother(todo.getRoutine()));
  }

  /**
   * 엄마 루틴 1건으로 (1) 엄마 Todo와 (2) 모든 살아있는 하위 루틴의 Todo를 한꺼번에 생성한다. 스케줄러와 엄마 루틴 생성 API 양쪽에서 호출되는 단일
   * 진입점이다. 하위 루틴은 routineType이 null이므로 nextOccurrence 호출 대상이 아니며, dueDate는 엄마 Todo의 dueDate를 그대로
   * 상속한다.
   */
  public void createTodoTreeFromMother(Routine motherRoutine) {
    if (motherRoutine.isChild()) {
      throw new IllegalStateException("createTodoTreeFromMother는 엄마 루틴에만 호출 가능합니다.");
    }
    LocalDate today = LocalDate.now(clock);
    LocalTime time =
        motherRoutine.getRoutineTime() != null
            ? motherRoutine.getRoutineTime()
            : LocalTime.of(23, 59, 59);
    LocalDateTime dueDate = nextOccurrence(motherRoutine, today).atTime(time);

    // 반복 종료일(dueDate)이 설정돼 있고 다음 회차가 그 날짜보다 뒤면 회차 Todo를 만들지 않는다.
    // (종료일 = "이 날짜까지는 반복 생성, 그 다음 날부터는 생성 안 됨")
    // 종료일이 자정(00:00)으로 저장돼 있어도 그날 회차(예: 23:59)는 포함되도록 날짜 단위로 비교한다.
    if (motherRoutine.getDueDate() != null
        && dueDate.toLocalDate().isAfter(motherRoutine.getDueDate().toLocalDate())) {
      return;
    }

    Todo motherTodo = saveRoutineTodo(motherRoutine, dueDate, null);
    if (motherTodo == null) {
      return;
    }
    motherRoutine.getChildren().forEach(child -> saveRoutineTodo(child, dueDate, motherTodo));
  }

  /** 기존 엄마 Todo에 하위 Todo 1개를 매단다. 하위 루틴 추가 API에서 호출. dueDate/tag/goal/user는 엄마 Todo에서 상속한다. */
  public void attachChildTodoUnder(Todo motherTodo, Routine childRoutine) {
    if (!childRoutine.isChild()) {
      throw new IllegalStateException("attachChildTodoUnder는 하위 루틴에만 호출 가능합니다.");
    }
    saveRoutineTodo(childRoutine, motherTodo.getDueDate(), motherTodo);
  }

  private Todo saveRoutineTodo(Routine routine, LocalDateTime dueDate, Todo parentTodo) {
    if (todoRepository.existsByRoutineAndDueDate(routine, dueDate)) {
      return null;
    }
    try {
      Routine motherForInherit = routine.isChild() ? routine.getParent() : routine;
      return todoRepository.saveAndFlush(
          Todo.builder()
              .title(routine.getTitle())
              .dueDate(dueDate)
              .isPinned(false)
              .user(motherForInherit.getUser())
              .tag(motherForInherit.getTag())
              .goal(motherForInherit.getGoal())
              .routine(routine)
              .parent(parentTodo)
              .build());
    } catch (DataIntegrityViolationException e) {
      String msg = e.getMostSpecificCause().getMessage();
      if (msg == null || !msg.contains("uq_todo_routine_duedate")) {
        throw e;
      }
      // uq_todo_routine_duedate 충돌 — 동시 INSERT로 이미 생성된 것으로 간주
      return null;
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
