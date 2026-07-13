package plana.replan.domain.routine.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.routine.dto.RoutineOverrideCompleteRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideContentRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideOrderRequestDto;
import plana.replan.domain.routine.dto.RoutineOverridePinRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideResponseDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineOverrideRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.routine.util.RoutineDays;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@Service
@RequiredArgsConstructor
public class RoutineOverrideService {

  private final Clock clock;
  private final RoutineRepository routineRepository;
  private final RoutineOverrideRepository routineOverrideRepository;
  private final TagRepository tagRepository;
  private final TodoRepository todoRepository;

  @Transactional
  public RoutineOverrideResponseDto updateContent(
      Long userId, Long routineId, LocalDate date, RoutineOverrideContentRequestDto request) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    validateOccurrenceDate(routine, date);

    if (request.title() != null && request.title().isBlank()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    final Tag tag;
    if (request.tagId() != null) {
      tag =
          tagRepository
              .findById(request.tagId())
              .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
      if (!tag.getUser().getId().equals(userId)) {
        throw new CustomException(TagErrorCode.TAG_NOT_FOUND);
      }
    } else {
      tag = null;
    }

    RoutineOverride override = upsert(routine, date);
    override.updateContent(request.title(), tag, request.time());

    // override의 null 필드는 "루틴 기본값 사용" 의미이므로, todo에는 실제 유효한 값을 세팅한다.
    String effectiveTitle = request.title() != null ? request.title() : routine.getTitle();
    Tag effectiveTag = tag != null ? tag : routine.getTag();
    LocalTime effectiveTime =
        request.time() != null
            ? request.time()
            : routine.getRoutineTime() != null
                ? routine.getRoutineTime()
                : LocalTime.of(23, 59, 59);
    Optional<Todo> existingTodo = findExistingTodo(routine, date);
    existingTodo.ifPresent(
        todo -> {
          todo.updateTitle(effectiveTitle);
          todo.updateTag(effectiveTag);
          todo.updateDueDate(date.atTime(effectiveTime));
        });

    return RoutineOverrideResponseDto.of(
        routine, override, existingTodo.map(Todo::getId).orElse(null));
  }

  @Transactional
  public RoutineOverrideResponseDto updateOrder(
      Long userId, Long routineId, LocalDate date, RoutineOverrideOrderRequestDto request) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    validateOccurrenceDate(routine, date);

    RoutineOverride override = upsert(routine, date);
    override.updateOrder(request.sortOrder());

    Optional<Todo> existingTodo = findExistingTodo(routine, date);
    existingTodo.ifPresent(todo -> todo.updateSortOrder(request.sortOrder()));

    return RoutineOverrideResponseDto.of(
        routine, override, existingTodo.map(Todo::getId).orElse(null));
  }

  @Transactional
  public RoutineOverrideResponseDto updateComplete(
      Long userId, Long routineId, LocalDate date, RoutineOverrideCompleteRequestDto request) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    validateOccurrenceDate(routine, date);
    LocalDateTime now = LocalDateTime.now(clock);

    RoutineOverride override = upsert(routine, date);
    override.updateComplete(request.isCompleted(), now);

    Optional<Todo> existingTodo = findExistingTodo(routine, date);
    existingTodo.ifPresent(todo -> todo.updateCompleted(request.isCompleted(), now));

    return RoutineOverrideResponseDto.of(
        routine, override, existingTodo.map(Todo::getId).orElse(null));
  }

  @Transactional
  public RoutineOverrideResponseDto updatePin(
      Long userId, Long routineId, LocalDate date, RoutineOverridePinRequestDto request) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    validateOccurrenceDate(routine, date);

    RoutineOverride override = upsert(routine, date);
    override.updatePin(request.isPinned());

    Optional<Todo> existingTodo = findExistingTodo(routine, date);
    existingTodo.ifPresent(todo -> todo.updatePinned(request.isPinned()));

    return RoutineOverrideResponseDto.of(
        routine, override, existingTodo.map(Todo::getId).orElse(null));
  }

  @Transactional(readOnly = true)
  public RoutineOverrideResponseDto getOverride(Long userId, Long routineId, LocalDate date) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    Long todoId = findExistingTodo(routine, date).map(Todo::getId).orElse(null);

    return routineOverrideRepository
        .findByRoutineAndOverrideDate(routine, date)
        .map(override -> RoutineOverrideResponseDto.of(routine, override, todoId))
        .orElseGet(() -> RoutineOverrideResponseDto.ofNoOverride(routine, date, todoId));
  }

  /**
   * 회차에 하위 투두를 추가한다. 행(Todo)이 이미 있으면 그 행에 실제 하위 투두를 만들고, 없으면(미래 회차) 회차 예외에 제목을 예약해 뒀다가 배치가 행을 만들 때
   * 실체화한다. 행 유무 분기를 트랜잭션 안에서 하므로 자정 배치와 경합해도 안전하다.
   */
  @Transactional
  public void addSubtodo(Long userId, Long routineId, LocalDate date, String title) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    validateOccurrenceDate(routine, date);
    validateSubtodoTitle(title);
    rejectIfSkipped(routine, date);

    Optional<Todo> existingTodo = findExistingTodo(routine, date);
    if (existingTodo.isPresent()) {
      todoRepository.save(
          Todo.builder()
              .title(title)
              .user(routine.getUser())
              .parent(existingTodo.get())
              .isPinned(false)
              .build());
      return;
    }
    upsert(routine, date).addSubtodo(title);
  }

  /** 예약된 하위 투두의 제목 수정 (행이 있는 날짜의 하위는 기존 투두 API를 사용). */
  @Transactional
  public void updateSubtodo(Long userId, Long routineId, LocalDate date, int index, String title) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    validateSubtodoTitle(title);
    RoutineOverride override = requireOverrideWithSubtodo(routine, date, index);
    override.updateSubtodo(index, title);
  }

  /** 예약된 하위 투두 완료/미완료 (행이 있는 날짜의 하위는 투두 완료 API를 사용). */
  @Transactional
  public void completeSubtodo(
      Long userId, Long routineId, LocalDate date, int index, boolean isCompleted) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    RoutineOverride override = requireOverrideWithSubtodo(routine, date, index);
    override.completeSubtodo(index, isCompleted);
  }

  /** 예약된 하위 투두 삭제 (행이 있는 날짜의 하위는 기존 투두 API를 사용). */
  @Transactional
  public void deleteSubtodo(Long userId, Long routineId, LocalDate date, int index) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    RoutineOverride override = requireOverrideWithSubtodo(routine, date, index);
    override.removeSubtodo(index);
  }

  private void validateSubtodoTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  private void rejectIfSkipped(Routine routine, LocalDate date) {
    routineOverrideRepository
        .findByRoutineAndOverrideDate(routine, date)
        .filter(RoutineOverride::isSkipped)
        .ifPresent(
            o -> {
              throw new CustomException(RoutineErrorCode.ROUTINE_OVERRIDE_SKIPPED);
            });
  }

  private RoutineOverride requireOverrideWithSubtodo(Routine routine, LocalDate date, int index) {
    RoutineOverride override =
        routineOverrideRepository
            .findByRoutineAndOverrideDate(routine, date)
            .orElseThrow(
                () -> new CustomException(RoutineErrorCode.ROUTINE_OVERRIDE_SUBTODO_NOT_FOUND));
    if (index < 0 || index >= override.reservedSubtodoCount()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_OVERRIDE_SUBTODO_NOT_FOUND);
    }
    return override;
  }

  @Transactional
  public void skip(Long userId, Long routineId, LocalDate date) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);
    validateOccurrenceDate(routine, date);

    findExistingTodo(routine, date)
        .ifPresent(
            todo -> {
              if (todo.isCompleted()) {
                throw new CustomException(RoutineErrorCode.ROUTINE_OVERRIDE_CANNOT_SKIP_COMPLETED);
              }
              LocalDateTime now = LocalDateTime.now(clock);
              todo.getChildren().forEach(child -> child.softDelete(now));
              todo.softDelete(now);
            });

    RoutineOverride override = upsert(routine, date);
    override.skip();
  }

  @Transactional
  public void undo(Long userId, Long routineId, LocalDate date) {
    Routine routine = findOwnedMotherRoutine(userId, routineId);

    // skip된 override가 없으면 no-op (ghost override row 생성 방지)
    routineOverrideRepository
        .findByRoutineAndOverrideDate(routine, date)
        .filter(RoutineOverride::isSkipped)
        .ifPresent(
            override -> {
              override.unskip();

              LocalDateTime start = date.atStartOfDay();
              LocalDateTime end = date.plusDays(1).atStartOfDay();
              todoRepository
                  .findDeletedMotherTodoByRoutineAndDate(routineId, start, end)
                  .ifPresent(
                      todo -> {
                        todoRepository
                            .findDeletedChildrenByParentId(todo.getId(), todo.getDeletedAt())
                            .forEach(Todo::restore);
                        todo.restore();
                      });
            });
  }

  /**
   * 그 날짜가 이 루틴의 실제 발생일인지 검증한다. 발생하지 않는 날짜(요일/일자 불일치 또는 반복 종료일 이후)에 회차 예외(override)를 만들면 존재하지 않는 회차의
   * 완료/건너뜀 기록이 생겨 이력과 통계가 오염되므로 미리 거절한다.
   */
  private void validateOccurrenceDate(Routine routine, LocalDate date) {
    boolean occurs =
        RoutineDays.isOccurrence(routine.getRoutineType(), routine.getRoutineDate(), date);
    boolean afterRepeatEnd =
        routine.getDueDate() != null && date.isAfter(routine.getDueDate().toLocalDate());
    if (!occurs || afterRepeatEnd) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE);
    }
  }

  private Routine findOwnedMotherRoutine(Long userId, Long routineId) {
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
    if (routine.isChild()) {
      throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_TARGET);
    }
    return routine;
  }

  private RoutineOverride upsert(Routine routine, LocalDate date) {
    return routineOverrideRepository
        .findByRoutineAndOverrideDate(routine, date)
        .orElseGet(
            () -> {
              try {
                return routineOverrideRepository.saveAndFlush(
                    RoutineOverride.builder().routine(routine).overrideDate(date).build());
              } catch (DataIntegrityViolationException e) {
                return routineOverrideRepository
                    .findByRoutineAndOverrideDate(routine, date)
                    .orElseThrow(() -> e);
              }
            });
  }

  private Optional<Todo> findExistingTodo(Routine routine, LocalDate date) {
    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    return todoRepository.findMotherTodoByRoutineAndDate(routine, start, end);
  }
}
