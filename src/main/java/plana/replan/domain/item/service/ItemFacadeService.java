package plana.replan.domain.item.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.item.dto.ItemCompleteRequestDto;
import plana.replan.domain.item.dto.ItemContentRequestDto;
import plana.replan.domain.item.dto.ItemDeleteRequestDto;
import plana.replan.domain.item.dto.ItemDetailResponseDto;
import plana.replan.domain.item.dto.ItemKind;
import plana.replan.domain.item.dto.ItemOrderRequestDto;
import plana.replan.domain.item.dto.ItemPinRequestDto;
import plana.replan.domain.item.dto.ItemResponseDto;
import plana.replan.domain.item.dto.ItemScope;
import plana.replan.domain.item.dto.ItemSubTodoCreateRequestDto;
import plana.replan.domain.item.dto.ItemSubTodoDeleteRequestDto;
import plana.replan.domain.item.dto.ItemSubTodoUpdateRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideCompleteRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideContentRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideOrderRequestDto;
import plana.replan.domain.routine.dto.RoutineOverridePinRequestDto;
import plana.replan.domain.routine.dto.RoutineOverrideResponseDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.dto.RoutineUpdateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineCreateRequestDto;
import plana.replan.domain.routine.dto.SubRoutineUpdateRequestDto;
import plana.replan.domain.routine.service.RoutineOverrideService;
import plana.replan.domain.routine.service.RoutineService;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.SubTodoUpdateRequestDto;
import plana.replan.domain.todo.dto.TodoCompleteRequestDto;
import plana.replan.domain.todo.dto.TodoPinRequestDto;
import plana.replan.domain.todo.dto.TodoUpdateRequestDto;
import plana.replan.domain.todo.service.TodoService;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

/**
 * 홈 화면용 통합 아이템 창구. 일회성 투두와 반복 루틴 회차를 "아이템" 하나의 개념으로 다룰 수 있게, 요청을 기존 투두/루틴/회차(override) 서비스로 그대로
 * 넘겨준다. 도메인 로직은 전부 기존 서비스에 있고 여기서는 분기만 한다.
 */
@Service
@RequiredArgsConstructor
public class ItemFacadeService {

  private final Clock clock;
  private final TodoService todoService;
  private final RoutineService routineService;
  private final RoutineOverrideService routineOverrideService;

  /** 투두 목록과 루틴 회차 목록을 합쳐 한 목록으로 반환한다. 두 목록은 서로 겹치지 않는다(투두 조회는 반복 미연결만 반환). */
  @Transactional(readOnly = true)
  public List<ItemResponseDto> getItems(Long userId, String filter, String sort, LocalDate date) {
    List<ItemResponseDto> items = new ArrayList<>();

    todoService
        .getTodos(userId, filter, sort, date)
        .forEach(todo -> items.add(ItemResponseDto.fromTodo(todo)));

    LocalDate baseDate = date != null ? date : LocalDate.now(clock);
    routineService
        .getRoutinesByFilter(userId, filter, baseDate)
        .forEach(
            (key, routines) ->
                routines.forEach(
                    routine ->
                        items.add(
                            ItemResponseDto.fromRoutine(routine, resolveDate(key, routine)))));

    items.sort(buildComparator(sort));
    return items;
  }

  private LocalDate resolveDate(String key, RoutineResponseDto routine) {
    try {
      return LocalDate.parse(key);
    } catch (DateTimeParseException e) {
      // filter=all이면 날짜별 그룹이 아니라 키가 "all" — 회차 마감일시에서 날짜를 뽑고, 그것도 없으면 null
      return routine.getDueDate() != null ? routine.getDueDate().toLocalDate() : null;
    }
  }

  private Comparator<ItemResponseDto> buildComparator(String sort) {
    // 완료된 아이템을 뒤로 보내는 규칙은 기존 투두 목록 조회와 동일
    Comparator<ItemResponseDto> base = Comparator.comparing(ItemResponseDto::isCompleted);
    if ("dueDate".equals(sort)) {
      return base.thenComparing(
          ItemResponseDto::dueDate, Comparator.nullsLast(Comparator.naturalOrder()));
    }
    return base.thenComparingDouble(ItemResponseDto::sortOrder);
  }

  @Transactional(readOnly = true)
  public ItemDetailResponseDto getDetail(
      Long userId, ItemKind kind, Long todoId, Long routineId, LocalDate date) {
    if (kind == ItemKind.TODO) {
      requireTodoTarget(todoId);
      return ItemDetailResponseDto.fromTodo(todoService.getTodoDetail(userId, todoId));
    }
    requireRoutineInstanceTarget(routineId, date);
    RoutineOverrideResponseDto override =
        routineOverrideService.getOverride(userId, routineId, date);
    List<ItemDetailResponseDto.SubItemDto> subItems;
    if (override.todoId() != null) {
      subItems =
          todoService.getTodoDetail(userId, override.todoId()).getSubTodos().stream()
              .map(ItemDetailResponseDto.SubItemDto::from)
              .toList();
    } else {
      // 행이 아직 없는 회차: 그날 생길 예정인 하위(하위 루틴, 읽기 전용) + 예약해 둔 하위(index로 수정/삭제)를 병합해 보여준다.
      List<ItemDetailResponseDto.SubItemDto> merged = new ArrayList<>();
      routineService
          .getAliveChildren(userId, routineId)
          .forEach(
              child ->
                  merged.add(
                      ItemDetailResponseDto.SubItemDto.plannedFromChildRoutine(
                          child.routineId(), child.title())));
      List<String> reserved = override.reservedSubtodos();
      for (int i = 0; i < reserved.size(); i++) {
        merged.add(ItemDetailResponseDto.SubItemDto.reserved(reserved.get(i), i));
      }
      subItems = merged;
    }
    return ItemDetailResponseDto.fromRoutine(override, subItems);
  }

  @Transactional
  public void complete(Long userId, ItemCompleteRequestDto request) {
    if (request.kind() == ItemKind.TODO) {
      requireTodoTarget(request.todoId());
      todoService.completeTodo(
          userId, request.todoId(), new TodoCompleteRequestDto(request.isCompleted()));
      return;
    }
    requireRoutineInstanceTarget(request.routineId(), request.date());
    routineOverrideService.updateComplete(
        userId,
        request.routineId(),
        request.date(),
        new RoutineOverrideCompleteRequestDto(request.isCompleted()));
  }

  @Transactional
  public void pin(Long userId, ItemPinRequestDto request) {
    if (request.kind() == ItemKind.TODO) {
      requireTodoTarget(request.todoId());
      todoService.pinTodo(userId, request.todoId(), new TodoPinRequestDto(request.isPinned()));
      return;
    }
    requireRoutineInstanceTarget(request.routineId(), request.date());
    routineOverrideService.updatePin(
        userId,
        request.routineId(),
        request.date(),
        new RoutineOverridePinRequestDto(request.isPinned()));
  }

  @Transactional
  public void reorder(Long userId, ItemOrderRequestDto request) {
    if (request.kind() == ItemKind.TODO) {
      requireTodoTarget(request.todoId());
      todoService.updateSortOrder(userId, request.todoId(), request.sortOrder());
      return;
    }
    requireRoutineInstanceTarget(request.routineId(), request.date());
    routineOverrideService.updateOrder(
        userId,
        request.routineId(),
        request.date(),
        new RoutineOverrideOrderRequestDto(request.sortOrder()));
  }

  @Transactional
  public void updateContent(Long userId, ItemContentRequestDto request) {
    if (request.kind() == ItemKind.TODO) {
      requireTodoTarget(request.todoId());
      todoService.updateTodo(
          userId,
          request.todoId(),
          new TodoUpdateRequestDto(
              request.title(),
              request.dueDate(),
              request.tagId(),
              request.routineType(),
              request.routineDays(),
              request.routineTime()));
      return;
    }
    requireScope(request.scope());
    if (request.scope() == ItemScope.THIS) {
      requireRoutineInstanceTarget(request.routineId(), request.date());
      routineOverrideService.updateContent(
          userId,
          request.routineId(),
          request.date(),
          new RoutineOverrideContentRequestDto(
              request.title(), request.tagId(), request.routineTime()));
      return;
    }
    // 반복 전체 수정 — 기존 루틴 수정 API의 필수값(title, routineType)을 여기서 미리 확인한다.
    requireRoutineTarget(request.routineId());
    if (request.title() == null || request.title().isBlank() || request.routineType() == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    routineService.updateMotherRoutine(
        userId,
        request.routineId(),
        new RoutineUpdateRequestDto(
            request.title(),
            request.repeatEndDate(),
            request.routineTime(),
            request.routineType(),
            request.routineDays(),
            request.tagId()));
  }

  @Transactional
  public void delete(Long userId, ItemDeleteRequestDto request) {
    if (request.kind() == ItemKind.TODO) {
      requireTodoTarget(request.todoId());
      todoService.deleteTodo(userId, request.todoId());
      return;
    }
    requireScope(request.scope());
    if (request.scope() == ItemScope.THIS) {
      requireRoutineInstanceTarget(request.routineId(), request.date());
      routineOverrideService.skip(userId, request.routineId(), request.date());
      return;
    }
    requireRoutineTarget(request.routineId());
    routineService.deleteMotherRoutine(userId, request.routineId());
  }

  @Transactional
  public void addSubTodo(Long userId, ItemSubTodoCreateRequestDto request) {
    if (request.kind() == ItemKind.TODO) {
      requireTodoTarget(request.todoId());
      todoService.createSubTodo(
          userId, request.todoId(), new SubTodoCreateRequestDto(request.title()));
      return;
    }
    requireScope(request.scope());
    if (request.scope() == ItemScope.THIS) {
      requireRoutineInstanceTarget(request.routineId(), request.date());
      routineOverrideService.addSubtodo(
          userId, request.routineId(), request.date(), request.title());
      return;
    }
    // 반복 전체 — 하위 루틴 생성 (모든 회차에 반복)
    requireRoutineTarget(request.routineId());
    routineService.createChildRoutine(
        userId, request.routineId(), new SubRoutineCreateRequestDto(request.title()));
  }

  @Transactional
  public void updateSubTodo(Long userId, ItemSubTodoUpdateRequestDto request) {
    if (request.subTodoId() != null) {
      // 행 하위 (그날만)
      requireTodoTarget(request.parentTodoId());
      todoService.updateSubTodo(
          userId,
          request.parentTodoId(),
          request.subTodoId(),
          new SubTodoUpdateRequestDto(request.title()));
      return;
    }
    if (request.subRoutineId() != null) {
      // 하위 루틴 (반복 전체)
      routineService.updateChildRoutine(
          userId, request.subRoutineId(), new SubRoutineUpdateRequestDto(request.title()));
      return;
    }
    // 예약 하위 (그날만, 행이 아직 없는 회차)
    requireReservedTarget(request.routineId(), request.date(), request.index());
    routineOverrideService.updateSubtodo(
        userId, request.routineId(), request.date(), request.index(), request.title());
  }

  @Transactional
  public void deleteSubTodo(Long userId, ItemSubTodoDeleteRequestDto request) {
    if (request.subTodoId() != null) {
      // 행 하위 (그날만)
      requireTodoTarget(request.parentTodoId());
      todoService.deleteSubTodo(userId, request.parentTodoId(), request.subTodoId());
      return;
    }
    if (request.subRoutineId() != null) {
      // 하위 루틴 (반복 전체)
      routineService.deleteChildRoutine(userId, request.subRoutineId());
      return;
    }
    // 예약 하위 (그날만, 행이 아직 없는 회차)
    requireReservedTarget(request.routineId(), request.date(), request.index());
    routineOverrideService.deleteSubtodo(
        userId, request.routineId(), request.date(), request.index());
  }

  private void requireReservedTarget(Long routineId, java.time.LocalDate date, Integer index) {
    if (routineId == null || date == null || index == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  private void requireTodoTarget(Long todoId) {
    if (todoId == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  private void requireRoutineTarget(Long routineId) {
    if (routineId == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  private void requireRoutineInstanceTarget(Long routineId, LocalDate date) {
    if (routineId == null || date == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  private void requireScope(ItemScope scope) {
    if (scope == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }
}
