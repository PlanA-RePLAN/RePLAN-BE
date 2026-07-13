package plana.replan.domain.routine.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.dto.RoutineUpdateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineCreateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineDayViewDto;
import plana.replan.domain.routine.dto.SubRoutineResponseDto;
import plana.replan.domain.routine.dto.SubRoutineUpdateRequestDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineOverrideRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.routine.util.RoutineDays;
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
public class RoutineService {

  private final Clock clock;
  private final RoutineRepository routineRepository;
  private final RoutineOverrideRepository routineOverrideRepository;
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

    validateRoutineDays(request.routineType(), request.routineDays());

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

    Integer routineDate = RoutineDays.toMask(request.routineType(), request.routineDays());

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

  /** 엄마 루틴 전체 수정. 하위 루틴 ID를 넘기면 400(ROUTINE_INVALID_TARGET). goalId는 수정 불가. */
  @Transactional
  public RoutineResponseDto updateMotherRoutine(
      Long userId, Long routineId, RoutineUpdateRequestDto request) {
    Routine routine = findOwnedRoutine(userId, routineId);
    if (routine.isChild()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET);
    }
    validateRoutineDays(request.routineType(), request.routineDays());

    Tag tag = null;
    if (request.tagId() != null) {
      tag =
          tagRepository
              .findById(request.tagId())
              .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
    }

    Integer routineDate = RoutineDays.toMask(request.routineType(), request.routineDays());
    routine.update(
        request.title(),
        request.dueDate(),
        request.routineType(),
        routineDate,
        request.routineTime(),
        tag);

    LocalDate today = LocalDate.now(clock);
    routineOverrideRepository.deleteByRoutineAndOverrideDateGreaterThanEqual(routine, today);

    // 오늘 이미 배치로 생성된 todo가 있으면 새 루틴 값으로 즉시 반영
    todoRepository
        .findMotherTodoByRoutineAndDate(routine, today.atStartOfDay(), today.atTime(LocalTime.MAX))
        .ifPresent(
            motherTodo -> {
              motherTodo.updateTitle(routine.getTitle());
              motherTodo.updateTag(routine.getTag());
              motherTodo.getChildren().forEach(child -> child.updateTag(routine.getTag()));
            });

    return RoutineResponseDto.from(routine);
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
  public Map<String, List<RoutineResponseDto>> getRoutinesByFilter(
      Long userId, String filter, LocalDate date) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    String normalizedFilter = filter.toLowerCase();
    if (normalizedFilter.equals("all")) {
      return Map.of("all", getAllFilterRoutines(userId));
    }

    if (date == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    List<LocalDate> dates =
        switch (normalizedFilter) {
          case "day" -> List.of(date);
          case "week" -> date.datesUntil(date.plusWeeks(1)).collect(Collectors.toList());
          case "month" -> date.datesUntil(date.plusMonths(1)).collect(Collectors.toList());
          default -> throw new CustomException(GlobalErrorCode.INVALID_INPUT);
        };

    Map<String, List<RoutineResponseDto>> result = new LinkedHashMap<>();
    for (LocalDate d : dates) {
      result.put(d.toString(), getRoutinesForDate(userId, d));
    }
    return result;
  }

  /** all 필터 전체 처리. 완료/미완료 Todo와 override를 루틴별로 한 번씩만 배치 조회해(N+1 방지) 각 루틴의 응답을 구성한다. */
  private List<RoutineResponseDto> getAllFilterRoutines(Long userId) {
    List<Routine> routines = routineRepository.findAllActiveMotherRoutinesByUser(userId);
    if (routines.isEmpty()) {
      return List.of();
    }

    LocalDate today = LocalDate.now(clock);
    Map<Long, List<Todo>> completedByRoutine =
        todoRepository.findCompletedMotherTodosByRoutines(routines).stream()
            .collect(Collectors.groupingBy(t -> t.getRoutine().getId()));
    // 마감일 최신순(DESC)으로 정렬돼 있어, 각 목록의 첫 번째가 가장 최근 미완료 회차다.
    Map<Long, List<Todo>> incompletesByRoutine =
        todoRepository.findIncompleteMotherTodosByRoutines(routines).stream()
            .collect(Collectors.groupingBy(t -> t.getRoutine().getId()));
    Map<Long, List<RoutineOverride>> overridesByRoutine =
        routineOverrideRepository.findByRoutineIn(routines).stream()
            .collect(Collectors.groupingBy(o -> o.getRoutine().getId()));

    return routines.stream()
        .flatMap(
            routine ->
                buildAllFilterResponses(
                    routine,
                    today,
                    completedByRoutine.getOrDefault(routine.getId(), List.of()),
                    incompletesByRoutine.getOrDefault(routine.getId(), List.of()),
                    overridesByRoutine.getOrDefault(routine.getId(), List.of()))
                    .stream())
        .collect(Collectors.toList());
  }

  /**
   * 루틴 1건에 대한 all 필터 응답을 구성한다.
   *
   * <p>완료한 회차(완료 Todo + Todo 없이 override로만 완료된 날짜)는 언제나 전부 포함한다. 여기에 아직 해결 안 된 회차를 덧붙이는데, 반복 종료일이
   * 지났는지에 따라 다르다.
   *
   * <ul>
   *   <li>반복이 안 끝난 루틴: "지금 해야 할 것" 1건만 덧붙인다(미완료 중 최신, 없으면 다음 발생일).
   *   <li>반복이 끝난 루틴: 아직 안 한 회차를 전부 덧붙인다(없으면 아무것도 안 붙인다).
   * </ul>
   */
  private List<RoutineResponseDto> buildAllFilterResponses(
      Routine routine,
      LocalDate today,
      List<Todo> completedTodos,
      List<Todo> incompleteTodos,
      List<RoutineOverride> overrides) {
    Set<LocalDate> completedDates =
        completedTodos.stream()
            .map(Todo::getDueDate)
            .filter(Objects::nonNull)
            .map(LocalDateTime::toLocalDate)
            .collect(Collectors.toSet());

    Map<LocalDate, RoutineOverride> overridesByDate =
        overrides.stream()
            .collect(Collectors.toMap(RoutineOverride::getOverrideDate, o -> o, (a, b) -> a));

    Stream<RoutineResponseDto> completedFromTodos =
        completedTodos.stream().map(todo -> toRoutineResponseFromTodo(routine, todo));

    // Todo가 아직 생성되지 않은 날짜라도 override로 완료 처리됐다면 완료 이력에서 빠지지 않도록 포함한다.
    Stream<RoutineResponseDto> completedFromOverrides =
        overrides.stream()
            .filter(o -> Boolean.TRUE.equals(o.getIsCompleted()))
            .filter(o -> !completedDates.contains(o.getOverrideDate()))
            .map(o -> toRoutineResponseFromOverride(routine, o.getOverrideDate(), o));

    boolean ended =
        routine.getDueDate() != null && today.isAfter(routine.getDueDate().toLocalDate());
    Stream<RoutineResponseDto> pending =
        ended
            ? incompleteTodos.stream().map(todo -> toRoutineResponseFromTodo(routine, todo))
            : toNextActionableRoutineResponse(
                routine,
                today,
                completedDates,
                incompleteTodos.stream().findFirst(),
                overridesByDate)
                .stream();

    return Stream.concat(Stream.concat(completedFromTodos, completedFromOverrides), pending)
        .collect(Collectors.toList());
  }

  /**
   * 반복이 안 끝난 루틴에 대해 "지금 해야 할 것" 1건을 구성한다. 완료 안 된 Todo 중 가장 최근 것이 있으면 그 상태로 반환한다. 없으면 다음 발생일부터 하루씩
   * 훑어, 이미 완료 처리된 날짜(Todo 또는 override)이거나 skip override가 있는 날짜는 건너뛰고, 아직 해결되지 않은 첫 날짜를 그 회차
   * 정보(override 있으면 override 값, 없으면 루틴 기본값 + 그 날짜)로 구성한다. (DAILY 루틴은 오늘 회차가 끝났어도 nextOccurrence가 항상
   * 오늘을 가리키므로, 이 건너뛰기 로직이 없으면 방금 완료 처리한 오늘 회차를 다시 보여주는 문제가 생긴다.)
   *
   * <p>반복 종료일을 넘어가 더 이상 낼 회차가 없으면 빈 값을 반환해 아무 항목도 남기지 않는다.
   */
  private Optional<RoutineResponseDto> toNextActionableRoutineResponse(
      Routine routine,
      LocalDate today,
      Set<LocalDate> completedDates,
      Optional<Todo> latestIncomplete,
      Map<LocalDate, RoutineOverride> overridesByDate) {
    if (latestIncomplete.isPresent()) {
      return Optional.of(toRoutineResponseFromTodo(routine, latestIncomplete.get()));
    }

    LocalDate repeatEndDate =
        routine.getDueDate() != null ? routine.getDueDate().toLocalDate() : null;
    LocalDate candidate = nextOccurrence(routine, today);
    for (int i = 0; i < 366; i++) {
      if (repeatEndDate != null && candidate.isAfter(repeatEndDate)) {
        break;
      }
      if (completedDates.contains(candidate)) {
        candidate = nextOccurrence(routine, candidate.plusDays(1));
        continue;
      }
      RoutineOverride override = overridesByDate.get(candidate);
      if (override != null
          && (override.isSkipped() || Boolean.TRUE.equals(override.getIsCompleted()))) {
        candidate = nextOccurrence(routine, candidate.plusDays(1));
        continue;
      }
      return Optional.of(
          override != null
              ? toRoutineResponseFromOverride(routine, candidate, override)
              : toRoutineResponseFromCandidate(routine, candidate));
    }
    return Optional.empty();
  }

  private RoutineResponseDto toRoutineResponseFromTodo(Routine routine, Todo todo) {
    return buildRoutineResponse(
        routine,
        todo.getTitle(),
        todo.getDueDate(),
        todo.getTag(),
        todo.getId(),
        todo.getSortOrder(),
        todo.isPinned(),
        todo.isCompleted(),
        false);
  }

  private RoutineResponseDto toRoutineResponseFromOverride(
      Routine routine, LocalDate date, RoutineOverride override) {
    // 시간 우선순위: 회차 예외 시간 > 루틴 기본 시간 > 23:59:59
    LocalTime time =
        override.getOverrideTime() != null
            ? override.getOverrideTime()
            : routine.getRoutineTime() != null
                ? routine.getRoutineTime()
                : LocalTime.of(23, 59, 59);
    Tag tag = override.getTag() != null ? override.getTag() : routine.getTag();
    return buildRoutineResponse(
        routine,
        override.getTitle() != null ? override.getTitle() : routine.getTitle(),
        date.atTime(time),
        tag,
        null,
        override.getSortOrder() != null ? override.getSortOrder() : routine.getDefaultSortOrder(),
        Boolean.TRUE.equals(override.getIsPinned()),
        Boolean.TRUE.equals(override.getIsCompleted()),
        true);
  }

  /**
   * 아직 Todo도 override도 없는 "다음 발생 예정" 회차를 응답으로 만든다. 마감 일시에 그 회차 날짜(candidate)와 루틴 시각을 채워, 프론트가 회차를
   * 정확히 지목(routineId + 날짜)해 조회·조작할 수 있게 한다.
   */
  private RoutineResponseDto toRoutineResponseFromCandidate(Routine routine, LocalDate candidate) {
    LocalTime time =
        routine.getRoutineTime() != null ? routine.getRoutineTime() : LocalTime.of(23, 59, 59);
    return buildRoutineResponse(
        routine,
        routine.getTitle(),
        candidate.atTime(time),
        routine.getTag(),
        null,
        routine.getDefaultSortOrder(),
        false,
        false,
        false);
  }

  private RoutineResponseDto buildRoutineResponse(
      Routine routine,
      String title,
      LocalDateTime dueDate,
      Tag tag,
      Long todoId,
      double sortOrder,
      boolean isPinned,
      boolean isCompleted,
      boolean hasOverride) {
    boolean overdue = !isCompleted && dueDate != null && dueDate.isBefore(LocalDateTime.now(clock));

    return new RoutineResponseDto(
        routine.getId(),
        title,
        dueDate,
        routine.getDueDate(),
        routine.getRoutineTime(),
        routine.getRoutineType(),
        RoutineDays.toDays(routine.getRoutineType(), routine.getRoutineDate()),
        tag != null ? tag.getId() : null,
        tag != null ? tag.getTitle() : null,
        tag != null ? tag.getColor() : null,
        routine.getGoal() != null ? routine.getGoal().getId() : null,
        todoId,
        sortOrder,
        isPinned,
        isCompleted,
        overdue,
        hasOverride);
  }

  private List<RoutineResponseDto> getRoutinesForDate(Long userId, LocalDate date) {
    int dayBit = 1 << (date.getDayOfWeek().getValue() - 1);
    int monthDayBit = 1 << (date.getDayOfMonth() - 1);
    return routineRepository.findMotherRoutinesByDate(userId, dayBit, monthDayBit, date).stream()
        .map(RoutineResponseDto::from)
        .collect(Collectors.toList());
  }

  /**
   * 살아있는 하위 루틴 목록(ID 포함). 행(Todo)이 아직 없는 가상 회차 상세에서 "그날 생길 예정인 하위"를 보여주고, 하위 루틴을 지목(수정/삭제)할 수 있게 하는
   * 용도로, 배치가 하위 투두를 만드는 기준({@code getChildren()})과 같은 목록을 쓴다.
   */
  @Transactional(readOnly = true)
  /** 그 날짜 기준 하위 루틴 예정분 목록 — 하위 루틴의 그날 개인화(제목/완료)를 반영하고, 그날 제외(skip)된 것은 뺀다. */
  public List<SubRoutineDayViewDto> getAliveChildrenForDate(
      Long userId, Long routineId, LocalDate date) {
    Routine routine = findOwnedRoutine(userId, routineId);
    if (routine.isChild()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET);
    }
    List<Routine> children = routine.getChildren();
    if (children.isEmpty()) {
      return List.of();
    }
    Map<Long, RoutineOverride> overrides =
        routineOverrideRepository
            .findByRoutineIdInAndOverrideDate(children.stream().map(Routine::getId).toList(), date)
            .stream()
            .collect(Collectors.toMap(o -> o.getRoutine().getId(), o -> o));
    return children.stream()
        .map(
            child -> {
              RoutineOverride ov = overrides.get(child.getId());
              if (ov != null && ov.isSkipped()) {
                return null;
              }
              return new SubRoutineDayViewDto(
                  child.getId(),
                  ov != null && ov.getTitle() != null ? ov.getTitle() : child.getTitle(),
                  ov != null && Boolean.TRUE.equals(ov.getIsCompleted()));
            })
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<SubRoutineResponseDto> getAliveChildren(Long userId, Long routineId) {
    Routine routine = findOwnedRoutine(userId, routineId);
    if (routine.isChild()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET);
    }
    return routine.getChildren().stream().map(SubRoutineResponseDto::from).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, List<RoutineResponseDto>> getPinnedRoutines(Long userId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    return routineRepository.findPinnedMotherRoutines(userId).stream()
        .collect(
            Collectors.groupingBy(
                p -> p.getOverrideDate().toString(),
                LinkedHashMap::new,
                Collectors.mapping(RoutineResponseDto::from, Collectors.toList())));
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
   * 루틴 삭제의 단일 진입점 (children 일괄 + 자기 자신). 삭제 시 이 루틴이 찍어낸 투두를 다음 규칙으로 정리한다.
   *
   * <ul>
   *   <li>완료된 회차 투두 트리 → 기록(통계·이력)을 위해 남긴다. 단 routine 참조는 끊는다 — @SQLRestriction이 걸린 삭제 루틴을
   *       lazy-load하는 순간 EntityNotFoundException(500)이 나기 때문 (목록 조회·undo 공통).
   *   <li>미완료 회차 투두 트리 → 하위 투두(직접 추가한 서브투두 포함)까지 함께 softDelete한다. 회차 하나 건너뛰기(skip)와 일관된 동작. 판단 단위는
   *       투두 낱개가 아니라 엄마 투두 트리다 — 부모만 지워지고 자식이 살아남으면 삭제된 부모를 참조하는 고아가 생긴다.
   *   <li>회차 예외(override) → 진짜 삭제. 루틴에 딸린 부속 메모라 어디서도 단독 참조하지 않으며, 루틴 전체 수정(updateMotherRoutine)도
   *       같은 방식으로 버린다.
   * </ul>
   *
   * 외부 public 진입점이므로 호출자 트랜잭션 유무와 무관하게 지연 로딩이 안전하도록 @Transactional을 보장한다.
   */
  @Transactional
  public void cascadeSoftDelete(Routine routine) {
    if (routine.isMother()) {
      routineOverrideRepository.deleteByRoutine(routine);

      LocalDateTime now = LocalDateTime.now(clock);
      todoRepository
          .findAllByRoutine(routine)
          .forEach(
              motherTodo -> {
                motherTodo.updateRoutine(null);
                if (!motherTodo.isCompleted()) {
                  motherTodo
                      .getChildren()
                      .forEach(
                          child -> {
                            child.updateRoutine(null);
                            child.softDelete(now);
                          });
                  motherTodo.softDelete(now);
                }
              });
    }
    routine.getChildren().forEach(this::detachAndSoftDelete);
    detachAndSoftDelete(routine);
  }

  /**
   * 남아있는 투두의 routine 참조를 끊고 루틴을 softDelete한다. 하위 루틴 단독 삭제 경로에서는 서브투두의 운명을 소속 엄마 투두 트리의 완료 여부로 결정한다
   * (완료 회차의 기록은 보존, 미완료 회차 것은 함께 삭제).
   */
  private void detachAndSoftDelete(Routine routine) {
    LocalDateTime now = LocalDateTime.now(clock);
    todoRepository
        .findAllByRoutine(routine)
        .forEach(
            todo -> {
              todo.updateRoutine(null);
              Todo root = todo.getParent() != null ? todo.getParent() : todo;
              if (!root.isCompleted()) {
                todo.softDelete(now);
              }
            });
    routine.softDelete();
  }

  @Transactional
  public void generateDailyTodos() {
    LocalDate today = LocalDate.now(clock);
    List<Routine> routines =
        routineRepository.findAllActiveMotherRoutines().stream()
            .filter(r -> isOccurrenceDay(r, today))
            .collect(Collectors.toList());

    List<Long> routineIds = routines.stream().map(Routine::getId).collect(Collectors.toList());
    Map<Long, RoutineOverride> overrideMap =
        routineOverrideRepository.findByRoutineIdInAndOverrideDate(routineIds, today).stream()
            .collect(Collectors.toMap(o -> o.getRoutine().getId(), o -> o));

    routines.forEach(
        r -> {
          RoutineOverride override = overrideMap.get(r.getId());
          if (override != null && override.isSkipped()) return;
          createTodoTreeFromMother(r, override);
        });
  }

  /**
   * {@link #createTodoTreeFromMother(Routine)}가 이 루틴의 다음 회차 투두를 실제로 만들지 판단한다. 다음 회차(오늘 또는 가까운 미래)가
   * 반복 종료일을 넘지 않으면 만든다 — createTodoTreeFromMother와 같은 규칙이다. 리플랜에서 "옛 회차를 치우고 새 회차로 옮길 수 있는지"를 결정하는
   * 데 쓴다(만들 수 없으면 옛 회차를 삭제하면 안 된다).
   */
  public boolean willCreateUpcomingOccurrence(Routine routine) {
    if (routine.getDueDate() == null) {
      return true; // 무기한 반복 → 다음 회차를 항상 만든다
    }
    LocalDate next = nextOccurrence(routine, LocalDate.now(clock));
    return !next.isAfter(routine.getDueDate().toLocalDate());
  }

  private boolean isOccurrenceDay(Routine routine, LocalDate today) {
    // 발생일 판정은 RoutineDays.isOccurrence 한 곳에서만 관리한다 (회차 예외 검증과 규칙 공유)
    return RoutineDays.isOccurrence(routine.getRoutineType(), routine.getRoutineDate(), today);
  }

  /**
   * 엄마 루틴 1건으로 (1) 엄마 Todo와 (2) 모든 살아있는 하위 루틴의 Todo를 한꺼번에 생성한다. 스케줄러와 엄마 루틴 생성 API 양쪽에서 호출되는 단일
   * 진입점이다. 하위 루틴은 routineType이 null이므로 nextOccurrence 호출 대상이 아니며, dueDate는 엄마 Todo의 dueDate를 그대로
   * 상속한다.
   */
  public void createTodoTreeFromMother(Routine motherRoutine) {
    createTodoTreeFromMother(motherRoutine, null);
  }

  public void createTodoTreeFromMother(Routine motherRoutine, RoutineOverride override) {
    if (motherRoutine.isChild()) {
      throw new IllegalStateException("createTodoTreeFromMother는 엄마 루틴에만 호출 가능합니다.");
    }
    LocalDate today = LocalDate.now(clock);
    // 시간 우선순위: 회차 예외 시간 > 루틴 기본 시간 > 23:59:59 (조회 응답과 같은 규칙)
    LocalTime time =
        (override != null && override.getOverrideTime() != null)
            ? override.getOverrideTime()
            : motherRoutine.getRoutineTime() != null
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

    Todo motherTodo = saveRoutineTodo(motherRoutine, dueDate, null, override);
    if (motherTodo == null) {
      return;
    }
    // 하위 루틴의 그날 개인화(제목/완료/제외)를 적용해 하위 행을 만든다
    Map<Long, RoutineOverride> childOverrides =
        childOverridesFor(motherRoutine, dueDate.toLocalDate());
    motherRoutine
        .getChildren()
        .forEach(
            child -> {
              RoutineOverride childOv = childOverrides.get(child.getId());
              if (childOv != null && childOv.isSkipped()) {
                return;
              }
              saveRoutineTodo(child, dueDate, motherTodo, childOv);
            });

    // 회차 예외에 예약해 둔 하위 투두를 실제 하위 투두로 실체화하고(완료 상태 승계) 예약을 비운다.
    if (override != null && override.getOverrideSubtodos() != null) {
      override
          .getOverrideSubtodos()
          .forEach(
              reserved -> {
                Todo sub =
                    todoRepository.save(
                        Todo.builder()
                            .title(reserved.title())
                            .user(motherRoutine.getUser())
                            .parent(motherTodo)
                            .isPinned(false)
                            .build());
                if (reserved.isCompleted()) {
                  sub.updateCompleted(true, LocalDateTime.now(clock));
                }
              });
      override.clearSubtodos();
    }
  }

  /** 기존 엄마 Todo에 하위 Todo 1개를 매단다. 하위 루틴 추가 API에서 호출. dueDate/tag/goal/user는 엄마 Todo에서 상속한다. */
  public void attachChildTodoUnder(Todo motherTodo, Routine childRoutine) {
    if (!childRoutine.isChild()) {
      throw new IllegalStateException("attachChildTodoUnder는 하위 루틴에만 호출 가능합니다.");
    }
    saveRoutineTodo(childRoutine, motherTodo.getDueDate(), motherTodo, null);
  }

  private Map<Long, RoutineOverride> childOverridesFor(Routine motherRoutine, LocalDate date) {
    List<Routine> children = motherRoutine.getChildren();
    if (children.isEmpty()) {
      return Map.of();
    }
    return routineOverrideRepository
        .findByRoutineIdInAndOverrideDate(children.stream().map(Routine::getId).toList(), date)
        .stream()
        .collect(Collectors.toMap(o -> o.getRoutine().getId(), o -> o));
  }

  private Todo saveRoutineTodo(
      Routine routine, LocalDateTime dueDate, Todo parentTodo, RoutineOverride override) {
    if (todoRepository.existsByRoutineAndDueDate(routine, dueDate)) {
      return null;
    }

    Routine motherForInherit = routine.isChild() ? routine.getParent() : routine;

    String title =
        (override != null && override.getTitle() != null)
            ? override.getTitle()
            : routine.getTitle();
    Tag tag =
        (override != null && override.getTag() != null)
            ? override.getTag()
            : motherForInherit.getTag();
    double sortOrder =
        (override != null && override.getSortOrder() != null)
            ? override.getSortOrder()
            : motherForInherit.getDefaultSortOrder();
    boolean isPinned = override != null && Boolean.TRUE.equals(override.getIsPinned());
    boolean isCompleted = override != null && Boolean.TRUE.equals(override.getIsCompleted());

    Todo todo;
    try {
      todo =
          todoRepository.saveAndFlush(
              Todo.builder()
                  .title(title)
                  .dueDate(dueDate)
                  .sortOrder(sortOrder)
                  .isPinned(isPinned)
                  .user(motherForInherit.getUser())
                  .tag(tag)
                  .goal(motherForInherit.getGoal())
                  .routine(routine)
                  .parent(parentTodo)
                  .build());
    } catch (DataIntegrityViolationException e) {
      // 스케줄러 중복 실행 등으로 unique(routine_id, due_date) 충돌 시 no-op
      return null;
    }

    if (isCompleted) {
      LocalDateTime completedTime =
          override.getCompletedTime() != null
              ? override.getCompletedTime()
              : LocalDateTime.now(clock);
      todo.updateCompleted(true, completedTime);
    }

    return todo;
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
        // routineDate는 일자 비트마스크(일자 d → 비트 d-1). 오늘부터 하루씩 훑어
        // 그 날짜의 일자 비트가 켜진 첫 날을 다음 회차로 잡는다. 짧은 달에 없는 일자(29~31)는 자연히 건너뛴다.
        int mask = routine.getRoutineDate();
        for (int i = 0; i < 366; i++) {
          LocalDate d = today.plusDays(i);
          if ((mask & (1 << (d.getDayOfMonth() - 1))) != 0) {
            yield d;
          }
        }
        yield today;
      }
    };
  }

  private void validateRoutineDays(RoutineType routineType, List<Integer> routineDays) {
    // WEEKLY: 요일 인덱스(0~6), MONTHLY: 일자(1~31). 비어있거나 범위 밖이면 400. DAILY는 무시.
    if (!RoutineDays.isValid(routineType, routineDays)) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE);
    }
  }
}
