package plana.replan.domain.replan.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.replan.dto.RecommendInput;
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanAnswer;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanQuestionsRequest;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.domain.replan.entity.FailureReasonCode;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.exception.ReplanErrorCode;
import plana.replan.domain.replan.repository.ReplanRepository;
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
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class ReplanService {

  private final TodoRepository todoRepository;
  private final ReplanRepository replanRepository;
  private final ReplanAiService aiService;
  private final TagRepository tagRepository;
  private final Clock clock;
  private final RoutineRepository routineRepository;
  private final RoutineService routineService;

  public List<ReplanQuestion> getQuestions(Long userId, ReplanQuestionsRequest req) {
    validateReasonCodesForQuestions(req.reasonCodes(), req.directInput());
    Todo anchor = findOwnedTodo(userId, req.anchorTodoId());
    RecommendInput input = buildInput(anchor, req.reasonCodes(), req.directInput(), null);
    return aiService.generateQuestions(input);
  }

  public ReplanRecommendResponse recommend(Long userId, ReplanRecommendRequest req) {
    validateReasonCodes(req.reasonCodes());
    Todo anchor = findOwnedTodo(userId, req.anchorTodoId());
    RecommendInput input = buildInput(anchor, req.reasonCodes(), null, req.answers());
    return aiService.generateRecommend(input);
  }

  RecommendInput buildInput(
      Todo anchor, List<String> reasonCodes, String directInput, List<ReplanAnswer> answers) {
    List<String> labels = toReasonLabels(reasonCodes);
    if (directInput != null && !directInput.isBlank()) {
      labels.add(directInput);
    }
    boolean routine = anchor.getRoutine() != null;
    List<RecommendInput.AnswerInput> answerInputs = new ArrayList<>();
    if (answers != null) {
      for (ReplanAnswer a : answers) {
        List<String> todoLabels =
            resolveSelectedTodoLabels(a.selectedTodoIds(), anchor.getUser().getId());
        answerInputs.add(
            new RecommendInput.AnswerInput(
                a.key(), a.text(), a.selectedTodoIds(), a.selectedChips(), todoLabels));
      }
    }
    return new RecommendInput(
        anchor.getId(),
        anchor.getTitle(),
        anchor.getDueDate() != null
            ? anchor.getDueDate().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            : null,
        anchor.getTag() != null ? anchor.getTag().getTitle() : null,
        routine,
        routine ? String.valueOf(anchor.getRoutine().getRoutineType()) : null,
        labels,
        answerInputs,
        LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE));
  }

  private List<String> resolveSelectedTodoLabels(List<Long> todoIds, Long userId) {
    if (todoIds == null || todoIds.isEmpty()) {
      return List.of();
    }
    List<String> labels = new ArrayList<>();
    for (Long id : todoIds) {
      todoRepository
          .findById(id)
          .filter(t -> t.getUser().getId().equals(userId))
          .ifPresent(t -> labels.add(id + ":" + t.getTitle()));
    }
    return labels;
  }

  List<String> toReasonLabels(List<String> codes) {
    List<String> labels = new ArrayList<>();
    if (codes == null) {
      return labels;
    }
    for (String code : codes) {
      try {
        labels.add(FailureReasonCode.valueOf(code).getLabel());
      } catch (IllegalArgumentException e) {
        labels.add(code); // 직접입력 등 enum에 없는 코드는 원문 사용
      }
    }
    return labels;
  }

  @Transactional
  public void save(Long userId, ReplanSaveRequest req) {
    validateReasonCodes(req.reasonCodes());
    Todo anchor = findOwnedTodo(userId, req.anchorTodoId());
    Replan replan = buildReplan(anchor, req.reasonCodes());
    replanRepository.save(replan);

    if (req.acceptedOperations() == null) {
      return;
    }

    boolean anyAdd = false;
    boolean anchorModifiedInPlace = false;

    for (ReplanOperation op : req.acceptedOperations()) {
      validateOperation(op);
      switch (op.action()) {
        case ADD -> {
          applyAdd(op, anchor, replan);
          anyAdd = true;
        }
        case MODIFY_TODO -> {
          applyModifyTodo(op, anchor, replan);
          if (op.targetTodoId() != null && op.targetTodoId().equals(anchor.getId())) {
            anchorModifiedInPlace = true;
          }
        }
        case MODIFY_ROUTINE -> applyModifyRoutine(op, anchor, replan);
        case CREATE_ROUTINE -> applyCreateRoutine(op, anchor, replan);
      }
    }

    // 마감 지난 일반 투두(앵커)를 ADD로 대체한 경우에만 한 번 숨긴다.
    // MODIFY_TODO로 앵커 자체를 수정했다면 숨기지 않는다.
    if (anchor.getRoutine() == null && isOverdue(anchor) && anyAdd && !anchorModifiedInPlace) {
      anchor.deactivate();
    }
  }

  private Replan buildReplan(Todo anchor, List<String> reasonCodes) {
    // validateReasonCodes는 save/recommend/getQuestions 진입 시점에 이미 호출됨 — 여기서는 보장된 1~3개
    String r1 = reasonCodes.get(0);
    String r2 = reasonCodes.size() > 1 ? reasonCodes.get(1) : null;
    String r3 = reasonCodes.size() > 2 ? reasonCodes.get(2) : null;
    return Replan.builder()
        .todo(anchor)
        .failureReason1(r1)
        .failureReason2(r2)
        .failureReason3(r3)
        .build();
  }

  private void validateOperation(ReplanOperation op) {
    if (op == null || op.action() == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    if ((op.action() == ReplanAction.MODIFY_TODO || op.action() == ReplanAction.MODIFY_ROUTINE)
        && op.targetTodoId() == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    if ((op.action() == ReplanAction.ADD || op.action() == ReplanAction.CREATE_ROUTINE)
        && (op.title() == null || op.title().isBlank())) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
  }

  private void validateReasonCodes(List<String> codes) {
    if (codes == null || codes.size() < 1 || codes.size() > 3) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_REASON);
    }
  }

  /** getQuestions 전용: 코드 목록이 없어도 directInput이 있으면 통과. 단, 코드가 있을 경우 최대 3개. */
  private void validateReasonCodesForQuestions(List<String> codes, String directInput) {
    boolean hasDirectInput = directInput != null && !directInput.isBlank();
    boolean hasCodes = codes != null && !codes.isEmpty();
    if (!hasCodes && !hasDirectInput) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_REASON);
    }
    if (codes != null && codes.size() > 3) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_REASON);
    }
  }

  private void applyAdd(ReplanOperation op, Todo anchor, Replan replan) {
    User user = anchor.getUser();
    Tag tag = resolveTag(op.tagId(), user.getId());
    Todo created =
        Todo.builder()
            .title(op.title())
            .dueDate(combineDueDate(op.dueDate(), op.dueTime()))
            .isPinned(false)
            .user(user)
            .tag(tag)
            .build();
    created.linkReplan(replan);
    todoRepository.save(created);
  }

  private void applyModifyTodo(ReplanOperation op, Todo anchor, Replan replan) {
    Todo target =
        todoRepository
            .findById(op.targetTodoId())
            .orElseThrow(() -> new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND));
    // 우선순위 등으로 앵커 외 다른 투두를 수정할 수 있으나, 반드시 같은 사용자 소유여야 한다
    if (!target.getUser().getId().equals(anchor.getUser().getId())) {
      throw new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND);
    }
    if (op.title() != null) {
      target.updateTitle(op.title());
    }
    // dueDate/dueTime 중 하나라도 지정된 경우에만 업데이트한다 — 제목만 바꾸는 op이면 기존 마감일을 보존
    if (op.dueDate() != null || op.dueTime() != null) {
      target.updateDueDate(resolveModifiedDueDate(target, op));
    }
    if (op.tagId() != null) {
      target.updateTag(resolveTag(op.tagId(), target.getUser().getId()));
    }
    target.linkReplan(replan);
  }

  /** 앵커의 마감이 지났는지 확인한다. */
  private boolean isOverdue(Todo anchor) {
    return anchor.getDueDate() != null && anchor.getDueDate().isBefore(LocalDateTime.now(clock));
  }

  private Tag resolveTag(Long tagId, Long userId) {
    if (tagId == null) {
      return null;
    }
    Tag tag =
        tagRepository
            .findById(tagId)
            .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
    if (!tag.getUser().getId().equals(userId)) {
      throw new CustomException(TagErrorCode.TAG_NOT_FOUND);
    }
    return tag;
  }

  private LocalDateTime combineDueDate(String dueDate, String dueTime) {
    if (dueDate == null) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(dueDate);
      LocalTime time = dueTime != null ? LocalTime.parse(dueTime) : LocalTime.of(23, 59, 59);
      return date.atTime(time);
    } catch (DateTimeParseException e) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
  }

  /**
   * op의 dueDate/dueTime을 기존 투두(target)의 마감일에 안전하게 적용한다. dueDate가 있으면 그 날짜를 사용하고, dueDate가 null이고
   * dueTime만 있으면 기존 날짜를 유지한 채 시간만 바꾼다. 기존 마감일도 없으면 오늘 날짜를 기준으로 삼는다.
   */
  private LocalDateTime resolveModifiedDueDate(Todo target, ReplanOperation op) {
    if (op.dueDate() != null) {
      return combineDueDate(op.dueDate(), op.dueTime());
    }
    // dueDate null, dueTime present: 기존 날짜를 보존하고 시간만 변경
    LocalDate baseDate =
        target.getDueDate() != null ? target.getDueDate().toLocalDate() : LocalDate.now(clock);
    return baseDate.atTime(parseTime(op.dueTime()));
  }

  private void applyModifyRoutine(ReplanOperation op, Todo anchor, Replan replan) {
    Routine routine = anchor.getRoutine();
    if (routine == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND);
    }
    Tag tag =
        op.tagId() != null ? resolveTag(op.tagId(), anchor.getUser().getId()) : routine.getTag();
    RoutineType effectiveType =
        op.routineType() != null ? parseRoutineType(op.routineType()) : routine.getRoutineType();
    Integer effectiveRoutineDate =
        op.routineDate() != null ? op.routineDate() : routine.getRoutineDate();
    validateRecurrence(effectiveType, effectiveRoutineDate);
    routine.update(
        op.title() != null ? op.title() : routine.getTitle(),
        effectiveType,
        effectiveRoutineDate,
        op.dueTime() != null ? parseTime(op.dueTime()) : routine.getRoutineTime(),
        tag);
    routine.linkReplan(replan);

    // 이번 회차 투두(앵커)가 미완료면 새 규칙에 맞춰 동기화, 완료면 그대로 둔다
    if (!anchor.isCompleted()) {
      if (op.title() != null) {
        anchor.updateTitle(op.title());
      }
      if (op.dueDate() != null || op.dueTime() != null) {
        anchor.updateDueDate(resolveModifiedDueDate(anchor, op));
      }
      if (op.tagId() != null) {
        anchor.updateTag(tag);
      }
      anchor.linkReplan(replan);
    }
  }

  private void applyCreateRoutine(ReplanOperation op, Todo anchor, Replan replan) {
    if (op.routineType() == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    RoutineType type = parseRoutineType(op.routineType());
    validateRecurrence(type, op.routineDate());
    User user = anchor.getUser();
    Tag tag = resolveTag(op.tagId(), user.getId());
    Routine routine =
        Routine.builder()
            .title(op.title())
            .routineTime(parseTime(op.dueTime()))
            .routineType(type)
            .routineDate(op.routineDate())
            .user(user)
            .tag(tag)
            .build();
    routine.linkReplan(replan);
    routineRepository.save(routine);
    routineService.createTodoTreeFromMother(routine);
    todoRepository
        .findFirstUpcomingMotherTodoByRoutine(routine, LocalDate.now(clock).atStartOfDay())
        .ifPresent(instanceTodo -> instanceTodo.linkReplan(replan));
  }

  private void validateRecurrence(RoutineType type, Integer routineDate) {
    if (type == RoutineType.WEEKLY
        && (routineDate == null || routineDate < 1 || routineDate > 127)) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    if (type == RoutineType.MONTHLY
        && (routineDate == null || routineDate < 1 || routineDate > 31)) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
  }

  private LocalTime parseTime(String time) {
    if (time == null) {
      return null;
    }
    try {
      return LocalTime.parse(time);
    } catch (DateTimeParseException e) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
  }

  private RoutineType parseRoutineType(String routineType) {
    try {
      return RoutineType.valueOf(routineType);
    } catch (IllegalArgumentException e) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
  }

  Todo findOwnedTodo(Long userId, Long todoId) {
    Todo todo =
        todoRepository
            .findById(todoId)
            .orElseThrow(() -> new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND));
    if (!todo.getUser().getId().equals(userId)) {
      throw new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND);
    }
    return todo;
  }
}
