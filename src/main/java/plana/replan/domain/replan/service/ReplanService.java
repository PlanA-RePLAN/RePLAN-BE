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
import plana.replan.domain.replan.dto.ReplanAnchorTodo;
import plana.replan.domain.replan.dto.ReplanAnswer;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.domain.replan.entity.FailureReasonCode;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.exception.ReplanErrorCode;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.repository.RoutineOverrideRepository;
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

  private static final DateTimeFormatter ANCHOR_DUE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  // Replan 테이블 failure_reason_* 컬럼 길이(VARCHAR(128))와 맞춘다.
  private static final int MAX_REASON_LENGTH = 128;

  private final TodoRepository todoRepository;
  private final ReplanRepository replanRepository;
  private final ReplanAiService aiService;
  private final TagRepository tagRepository;
  private final Clock clock;
  private final RoutineRepository routineRepository;
  private final RoutineService routineService;
  private final RoutineOverrideRepository routineOverrideRepository;

  /**
   * 2단계 선택(+선택적 추가질문 답변)을 받아, 추가 질문이 필요하면 질문을, 충분하면 추천을 반환한다. 질문이 필요한지는 {@link
   * ReplanQuestionRegistry}가 결정론적으로 판단한다(LLM/프론트가 아님). 필요한 질문 중 아직 답하지 않은 게 남아 있으면 그 질문들을 다시 내려주고,
   * 모두 답했으면(또는 필요한 질문이 없으면) 곧바로 추천을 생성한다.
   */
  public ReplanRecommendResponse recommend(Long userId, ReplanRecommendRequest req) {
    validateReasonCodes(req.reasonCodes());
    int refreshCount = normalizeRefreshCount(req.refreshCount());
    Todo anchor = findOwnedTodo(userId, req.anchorTodoId());
    List<String> reasonLabels = toReasonLabelPath(req.reasonCodes());

    // 필요한 질문 중 아직 답이 안 온 것만 추려서, 하나라도 남아 있으면 질문 단계로 되돌린다.
    // (사유 여러 개가 각각 질문을 요구하는데 일부 답변만 온 경우, 빠진 질문 없이 추천으로 넘어가지 않도록)
    List<ReplanQuestion> required = ReplanQuestionRegistry.forReasonCodes(req.reasonCodes());
    if (!required.isEmpty()) {
      java.util.Set<String> answeredKeys = new java.util.HashSet<>();
      if (req.answers() != null) {
        for (ReplanAnswer a : req.answers()) {
          if (a != null && a.key() != null) {
            answeredKeys.add(a.key());
          }
        }
      }
      List<ReplanQuestion> unanswered =
          required.stream().filter(q -> !answeredKeys.contains(q.key())).toList();
      if (!unanswered.isEmpty()) {
        // 질문 화면의 "기존 투두 수정 사항" 카드용으로 앵커 투두의 기존 정보를 함께 내려준다.
        return ReplanRecommendResponse.askQuestions(unanswered, ReplanAnchorTodo.from(anchor));
      }
    }

    RecommendInput input = buildInput(anchor, req.reasonCodes(), null, req.answers(), refreshCount);
    List<ReplanOperation> operations = aiService.generateRecommend(input);
    return ReplanRecommendResponse.recommendation(operations, reasonLabels);
  }

  /**
   * 선택한 실패 이유 코드를 화면 표시용 한글 라벨 목록으로 변환한다. 각 코드의 상위(부모)→하위 순서로 라벨을 펼쳐, 프론트가 "목표/계획 개선 필요 > 구체적 계획
   * 수립을 실패했어요"처럼 2단계 라벨을 그대로 보여줄 수 있게 한다. enum에 없는 코드(직접입력)는 원문을 그대로 넣는다.
   */
  List<String> toReasonLabelPath(List<String> codes) {
    List<String> result = new ArrayList<>();
    if (codes == null) {
      return result;
    }
    for (String code : codes) {
      try {
        java.util.Deque<String> path = new java.util.ArrayDeque<>();
        FailureReasonCode fc = FailureReasonCode.valueOf(code);
        while (fc != null) {
          path.addFirst(fc.getLabel());
          fc = fc.getParent();
        }
        for (String label : path) {
          if (!result.contains(label)) {
            result.add(label);
          }
        }
      } catch (IllegalArgumentException e) {
        if (!result.contains(code)) {
          result.add(code);
        }
      }
    }
    return result;
  }

  RecommendInput buildInput(
      Todo anchor,
      List<String> reasonCodes,
      String directInput,
      List<ReplanAnswer> answers,
      int refreshCount) {
    List<String> labels = toReasonLabels(reasonCodes);
    if (directInput != null && !directInput.isBlank()) {
      labels.add(directInput);
    }
    boolean routine = anchor.getRoutine() != null;
    List<RecommendInput.AnswerInput> answerInputs = new ArrayList<>();
    if (answers != null) {
      for (ReplanAnswer a : answers) {
        if (a == null) {
          continue; // 답변 배열에 null이 섞여 와도 500으로 터지지 않도록 건너뛴다.
        }
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
        // 마감 시간까지 포함해 보낸다 — +30분/+1시간/+2시간 같은 로직이 기존 시간을 기준으로 계산할 수 있어야 한다.
        anchor.getDueDate() != null ? anchor.getDueDate().format(ANCHOR_DUE_FORMAT) : null,
        anchor.getTag() != null ? anchor.getTag().getTitle() : null,
        routine,
        routine ? String.valueOf(anchor.getRoutine().getRoutineType()) : null,
        labels,
        answerInputs,
        LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE),
        refreshCount);
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

    boolean anchorHandled = false;
    boolean replacementCreated = false;
    for (ReplanOperation op : req.acceptedOperations()) {
      validateOperation(op);
      switch (op.action()) {
        case ADD -> {
          applyAdd(op, anchor, replan);
          replacementCreated = true;
        }
        case MODIFY_TODO -> {
          applyModifyTodo(op, anchor, replan);
          if (op.targetTodoId() != null && op.targetTodoId().equals(anchor.getId())) {
            anchorHandled = true;
          }
        }
        case MODIFY_ROUTINE -> {
          applyModifyRoutine(op, anchor, replan);
          anchorHandled = true;
        }
        case CREATE_ROUTINE -> {
          applyCreateRoutine(op, anchor, replan);
          replacementCreated = true;
        }
      }
    }

    // 마감 지난(실패 후) 앵커를 ADD/CREATE_ROUTINE으로 대체한 경우에만 앵커를 치운다(비활성화).
    // 미래(실패 전) 앵커의 ADD는 보조 투두 추가일 수 있으므로 앵커를 건드리지 않는다.
    if (!anchorHandled && isOverdue(anchor) && replacementCreated) {
      retire(anchor);
    }
  }

  private Replan buildReplan(Todo anchor, List<String> reasonCodes) {
    // validateReasonCodes는 save/recommend 진입 시점에 이미 호출됨 — 여기서는 보장된 1~3개
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
    // MODIFY는 title 생략(null)은 허용하되, 빈 문자열을 명시하면 잘못된 입력으로 본다.
    if ((op.action() == ReplanAction.MODIFY_TODO || op.action() == ReplanAction.MODIFY_ROUTINE)
        && op.title() != null
        && op.title().isBlank()) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
  }

  /** 새로고침 횟수를 0~3로 검증하고 null은 0으로 정규화한다. */
  private int normalizeRefreshCount(Integer refreshCount) {
    if (refreshCount == null) {
      return 0;
    }
    if (refreshCount < 0 || refreshCount > 3) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_REFRESH_COUNT);
    }
    return refreshCount;
  }

  private void validateReasonCodes(List<String> codes) {
    if (codes == null || codes.size() < 1 || codes.size() > 3) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_REASON);
    }
    for (String code : codes) {
      // 직접 입력 사유는 Replan 테이블의 failure_reason 컬럼(최대 128자)에 그대로 저장되므로,
      // 길이를 넘으면 저장 시 DB 오류(500) 대신 여기서 미리 400으로 막는다.
      if (code == null || code.isBlank() || code.length() > MAX_REASON_LENGTH) {
        throw new CustomException(ReplanErrorCode.REPLAN_INVALID_REASON);
      }
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
    if (!target.getUser().getId().equals(anchor.getUser().getId())) {
      throw new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND);
    }
    // 하위 투두를 미리 스냅샷해두고 새 부모로 옮긴 뒤 부모만 치운다.
    // retire(target)을 그대로 쓰면 하위 투두까지 소프트 삭제돼 사용자의 서브태스크가 모두 사라진다.
    List<Todo> children = new ArrayList<>(target.getChildren());
    Todo newTodo = recreateFromModify(target, op, replan);
    children.forEach(child -> child.updateParent(newTodo));
    retireWithoutChildren(target);
  }

  /** 기존 투두를 사용자 화면에서 치운다: 마감 지났으면 비활성화(통계에 실패로 남김), 아니면 소프트 삭제(통계에서 제외). */
  private void retire(Todo target) {
    if (isOverdue(target)) {
      target.deactivate();
    } else {
      target.getChildren().forEach(Todo::softDelete);
      target.softDelete();
    }
  }

  /**
   * 투두 자신만 치운다(하위 투두는 건드리지 않음): 마감 지났으면 비활성화, 아니면 소프트 삭제. MODIFY_TODO에서 하위 투두를 새 부모로 옮긴 뒤 원래 부모를 치울
   * 때 사용한다.
   */
  private void retireWithoutChildren(Todo target) {
    if (isOverdue(target)) {
      target.deactivate();
    } else {
      target.softDelete();
    }
  }

  /** op가 지정한 필드만 바꾸고 나머지는 기존 투두에서 물려받아 새 투두를 만든다(루틴 연결 없음). */
  private Todo recreateFromModify(Todo target, ReplanOperation op, Replan replan) {
    String title = op.title() != null ? op.title() : target.getTitle();
    LocalDateTime dueDate =
        (op.dueDate() != null || op.dueTime() != null)
            ? resolveModifiedDueDate(target, op)
            : target.getDueDate();
    Tag tag =
        op.tagId() != null ? resolveTag(op.tagId(), target.getUser().getId()) : target.getTag();
    Todo created =
        Todo.builder()
            .title(title)
            .dueDate(dueDate)
            .sortOrder(target.getSortOrder())
            .isPinned(target.isPinned())
            .user(target.getUser())
            .tag(tag)
            .goal(target.getGoal())
            .build();
    created.linkReplan(replan);
    return todoRepository.save(created);
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
    if (!op.targetTodoId().equals(anchor.getId())) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    Routine routine = anchor.getRoutine();
    if (routine == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND);
    }
    Tag tag =
        op.tagId() != null ? resolveTag(op.tagId(), anchor.getUser().getId()) : routine.getTag();
    RoutineType effectiveType =
        op.routineType() != null ? parseRoutineType(op.routineType()) : routine.getRoutineType();
    // 반복 유형이 바뀌면 기존 routineDate는 의미가 달라(WEEKLY=요일 비트마스크 ↔ MONTHLY=일자)
    // 재사용하지 않고, 새 유형에 맞는 routineDate를 op가 명시하도록 강제한다(없으면 아래 검증에서 400).
    // 유형이 그대로면 생략된 routineDate를 기존 값으로 보완한다.
    boolean typeChanged = op.routineType() != null && effectiveType != routine.getRoutineType();
    Integer effectiveRoutineDate =
        typeChanged
            ? op.routineDate()
            : (op.routineDate() != null ? op.routineDate() : routine.getRoutineDate());
    validateRecurrence(effectiveType, effectiveRoutineDate);
    // DAILY는 반복 날짜가 의미 없으므로 null로 정규화한다(다른 루틴 생성 경로와 규약을 맞춘다).
    effectiveRoutineDate = normalizeRoutineDate(effectiveType, effectiveRoutineDate);
    routine.update(
        op.title() != null ? op.title() : routine.getTitle(),
        routine.getDueDate(),
        effectiveType,
        effectiveRoutineDate,
        op.dueTime() != null ? parseTime(op.dueTime()) : routine.getRoutineTime(),
        tag);
    routine.linkReplan(replan);

    // 미래 회차 수정기록 폐기(옛 규칙 기준이라 의미 없어짐)
    routineOverrideRepository.deleteByRoutineAndOverrideDateGreaterThanEqual(
        routine, LocalDate.now(clock));

    // 이번 회차(앵커) 치우기 — 실패 전 삭제 / 실패 후 비활성화
    boolean failedBefore = !isOverdue(anchor);
    retire(anchor);

    // 실패 전 + 새 규칙에 오늘이 포함되면 오늘 회차를 새 규칙대로 재생성
    if (failedBefore && routineService.occursToday(routine)) {
      routineService.createTodoTreeFromMother(routine);
      todoRepository
          .findFirstUpcomingMotherTodoByRoutine(routine, LocalDate.now(clock).atStartOfDay())
          .ifPresent(instance -> instance.linkReplan(replan));
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
            // op.dueDate는 루틴의 반복 종료일(이 날짜 이후로는 회차를 만들지 않음)로 보존한다.
            .dueDate(parseRoutineEndDate(op.dueDate()))
            .routineTime(parseTime(op.dueTime()))
            .routineType(type)
            // DAILY는 반복 날짜가 의미 없으므로 null로 정규화한다(다른 루틴 생성 경로와 규약을 맞춘다).
            .routineDate(normalizeRoutineDate(type, op.routineDate()))
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

  /** DAILY 루틴은 반복 날짜가 의미 없으므로 항상 null로 둔다(WEEKLY/MONTHLY는 그대로). */
  private Integer normalizeRoutineDate(RoutineType type, Integer routineDate) {
    return type == RoutineType.DAILY ? null : routineDate;
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

  /** 루틴 반복 종료일(yyyy-MM-dd)을 파싱한다. 없으면 null(무기한 반복). */
  private LocalDateTime parseRoutineEndDate(String dueDate) {
    if (dueDate == null) {
      return null;
    }
    try {
      return LocalDate.parse(dueDate).atStartOfDay();
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
