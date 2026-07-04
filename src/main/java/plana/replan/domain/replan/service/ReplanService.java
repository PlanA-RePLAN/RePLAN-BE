package plana.replan.domain.replan.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import plana.replan.domain.routine.util.RoutineDays;
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
    List<ReplanOperation> operations =
        applyModifyTagPolicy(aiService.generateRecommend(input), anchor);
    return ReplanRecommendResponse.recommendation(operations, reasonLabels);
  }

  /**
   * 수정(MODIFY_TODO/MODIFY_ROUTINE) 작업의 태그를 AI가 고른 값 대신 대상의 기존 태그로 고정한다. 리플랜은 투두를 쪼개거나 미루는 것이지
   * 카테고리(태그)를 바꾸는 게 아니므로 태그는 그대로 유지한다. 신규 추가(ADD/CREATE_ROUTINE)는 AI가 배정한 태그를 그대로 둔다.
   *
   * <p>미리보기에 넣는 태그는 실제 저장될 태그와 일치해야 한다.
   *
   * <ul>
   *   <li>MODIFY_ROUTINE: 저장 시 회차 오버라이드 태그가 아니라 엄마 루틴 태그(routine.getTag())를 유지하므로 미리보기도 루틴 태그를 쓴다.
   *   <li>MODIFY_TODO: 저장 시 대상 투두(target.getTag())를 유지한다. 대상이 앵커가 아닌 다른 투두일 수도 있으므로(우선순위 재정렬 등)
   *       targetTodoId로 조회해 그 투두의 태그를 쓴다.
   * </ul>
   */
  private List<ReplanOperation> applyModifyTagPolicy(
      List<ReplanOperation> operations, Todo anchor) {
    List<ReplanOperation> result = new ArrayList<>();
    for (ReplanOperation op : operations) {
      Tag tag;
      if (op.action() == ReplanAction.MODIFY_ROUTINE && isAnchorTarget(op, anchor)) {
        // 저장 경로(applyModifyRoutine)는 targetTodoId가 앵커일 때만 유효하고 엄마 루틴 태그를 유지한다.
        // 미리보기도 같은 조건일 때만 엄마 루틴 태그를 붙여 저장될 값과 일치시킨다.
        tag = anchor.getRoutine() != null ? anchor.getRoutine().getTag() : null;
      } else if (op.action() == ReplanAction.MODIFY_TODO
          || op.action() == ReplanAction.MODIFY_ROUTINE) {
        // MODIFY_TODO, 그리고 대상이 앵커가 아닌(저장 시 거부될) MODIFY_ROUTINE은 대상 투두의 기존 태그를 쓴다.
        tag = existingTagForModify(op.targetTodoId(), anchor);
      } else {
        result.add(op); // ADD/CREATE_ROUTINE: AI가 배정한 태그 그대로 둔다.
        continue;
      }
      result.add(op.withTag(tag == null ? null : tag.getId(), tag == null ? null : tag.getTitle()));
    }
    return result;
  }

  /** op의 대상이 앵커 투두인지 확인한다. */
  private boolean isAnchorTarget(ReplanOperation op, Todo anchor) {
    return op.targetTodoId() != null && op.targetTodoId().equals(anchor.getId());
  }

  /** 수정 대상 투두의 기존 태그를 찾는다. 앵커면 앵커 태그, 아니면 소유한 투두를 조회해 그 태그(없으면 null). */
  private Tag existingTagForModify(Long targetTodoId, Todo anchor) {
    if (targetTodoId == null) {
      return null;
    }
    if (targetTodoId.equals(anchor.getId())) {
      return anchor.getTag();
    }
    return todoRepository
        .findById(targetTodoId)
        .filter(t -> t.getUser().getId().equals(anchor.getUser().getId()))
        .map(Todo::getTag)
        .orElse(null);
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
        // 소유권 검증을 통과한 투두만 추려, ID와 라벨이 같은 집합을 가리키게 한다.
        // (남의/없는 투두 ID가 섞여 와도 라벨만 거르고 ID는 원본을 넘기면 둘이 어긋나 잘못된 추천이 만들어질 수 있음)
        List<Todo> ownedTodos = resolveOwnedTodos(a.selectedTodoIds(), anchor.getUser().getId());
        List<Long> ownedIds = ownedTodos.stream().map(Todo::getId).toList();
        List<String> todoLabels =
            ownedTodos.stream().map(t -> t.getId() + ":" + t.getTitle()).toList();
        answerInputs.add(new RecommendInput.AnswerInput(a.key(), a.text(), ownedIds, todoLabels));
      }
    }
    // 유저가 가진 태그(id+이름)를 함께 넘겨, AI가 신규 투두에 실제 존재하는 태그를 배정하게 한다.
    List<RecommendInput.TagOption> tagOptions =
        tagRepository.findAllByUserId(anchor.getUser().getId()).stream()
            .map(t -> new RecommendInput.TagOption(t.getId(), t.getTitle()))
            .toList();
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
        refreshCount,
        tagOptions);
  }

  /** 선택한 투두 ID 중 해당 사용자의 것만 추려 엔티티로 반환한다(없거나 남의 것은 제외). */
  private List<Todo> resolveOwnedTodos(List<Long> todoIds, Long userId) {
    if (todoIds == null || todoIds.isEmpty()) {
      return List.of();
    }
    List<Todo> result = new ArrayList<>();
    for (Long id : todoIds) {
      todoRepository
          .findById(id)
          .filter(t -> t.getUser().getId().equals(userId))
          .ifPresent(result::add);
    }
    return result;
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
          // 회차 투두가 실제로 만들어졌을 때만 "앵커를 치울 대체가 생겼다"고 본다.
          // (루틴 종료일이 이미 지나 회차가 안 생기면 앵커를 비활성화하면 안 된다 — 원본까지 사라짐)
          replacementCreated |= applyCreateRoutine(op, anchor, replan);
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
    // 루틴 회차는 MODIFY_TODO로 고치지 않는다(루틴 변경은 MODIFY_ROUTINE 담당).
    // 같은 (routine_id, due_date) 슬롯에 새 행을 만들면 partial unique index와 충돌하고,
    // 분리 후 슬롯이 비면 스케줄러가 회차를 중복 생성하므로 거부한다.
    if (target.getRoutine() != null) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    // 하위 투두를 미리 스냅샷해두고 새 부모로 옮긴 뒤 부모만 치운다.
    // retire(target)을 그대로 쓰면 하위 투두까지 소프트 삭제돼 사용자의 서브태스크가 모두 사라진다.
    List<Todo> children = new ArrayList<>(target.getChildren());
    Todo newTodo = recreateFromModify(target, op, replan);
    children.forEach(child -> child.updateParent(newTodo));
    retireWithoutChildren(target);
    // 수정 대상을 소프트 삭제하는 경우(실패 전), 그 투두를 가리키던 리플랜들이
    // @SQLRestriction(deleted_at IS NULL) 때문에 리플랜 조회에서 통째로 사라진다.
    // 이번에 만든 리플랜뿐 아니라, 같은 투두를 이전에 리플랜해 달려 있던 리플랜까지 모두
    // 살아있는 새 투두로 옮겨 달아 월간 통계(리플랜 횟수 등)에 빠짐없이 집계되게 한다.
    // (실패 후 비활성화는 투두가 살아 있어 옮길 필요가 없다.)
    if (!isOverdue(target)) {
      // 이번 리플랜은 앵커를 가리키므로, 앵커 자신을 수정한 경우에만 새 투두로 옮긴다.
      // (앵커가 아닌 다른 투두 수정이면 이번 리플랜은 여전히 살아있는 앵커를 가리켜야 한다.)
      if (target == anchor) {
        replan.relinkTodo(newTodo);
      }
      // 그 투두에 이전부터 달려 있던 다른 리플랜들도 빠짐없이 새 투두로 옮긴다.
      relinkReplansTo(target, newTodo);
    }
  }

  /**
   * {@code from} 투두를 가리키던 리플랜(메모)을 모두 {@code to} 투두로 옮겨 단다. 같은 투두를 한 달에 여러 번 리플랜하면 리플랜이 여러 개 달릴 수
   * 있는데, 그 투두를 소프트 삭제하면 달려 있던 리플랜이 전부 통계에서 사라진다. 그래서 소프트 삭제 직전에 호출해 모든 리플랜을 살아있는 새 투두로 옮긴다.
   */
  private void relinkReplansTo(Todo from, Todo to) {
    replanRepository.findByTodo(from).forEach(r -> r.relinkTodo(to));
  }

  /**
   * 기존 투두를 (하위 투두까지 함께) 치운다: 마감 지났으면 비활성화(통계에 실패로 남김), 아니면 소프트 삭제(통계에서 제외). 부모만 치우고 자식을 두면 상태가
   * 어긋나므로 두 경로 모두 자식을 함께 처리한다.
   */
  private void retire(Todo target) {
    if (isOverdue(target)) {
      target.getChildren().forEach(Todo::deactivate);
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

  /**
   * op가 지정한 필드만 바꾸고 나머지는 기존 투두에서 물려받아 새 투두를 만든다. (루틴 회차는 applyModifyTodo에서 이미 거부되므로 여기서는 일반 투두만
   * 다룬다.)
   */
  private Todo recreateFromModify(Todo target, ReplanOperation op, Replan replan) {
    String title = op.title() != null ? op.title() : target.getTitle();
    LocalDateTime dueDate =
        (op.dueDate() != null || op.dueTime() != null)
            ? resolveModifiedDueDate(target, op)
            : target.getDueDate();
    // 수정(MODIFY_TODO)은 태그를 바꾸지 않고 기존 투두 태그를 그대로 유지한다(AI/프론트가 tagId를 줘도 무시).
    Tag tag = target.getTag();
    Todo created =
        Todo.builder()
            .title(title)
            .dueDate(dueDate)
            .sortOrder(target.getSortOrder())
            .isPinned(target.isPinned())
            .user(target.getUser())
            .tag(tag)
            .goal(target.getGoal())
            .parent(target.getParent())
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
    // 하위 루틴 회차는 규칙(반복 유형/요일/시각/태그)을 따로 가지지 않고 엄마 루틴을 그대로 따른다.
    // 따라서 루틴 규칙 변경(MODIFY_ROUTINE)은 엄마 루틴 회차에만 의미가 있으므로 하위 루틴 회차는 거부한다.
    // (엄마 루틴 수정 API·루틴 오버라이드도 동일하게 하위 루틴을 거부한다.)
    if (routine.isChild()) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    // 수정(MODIFY_ROUTINE)은 태그를 바꾸지 않고 기존 루틴 태그를 그대로 유지한다(AI/프론트가 tagId를 줘도 무시).
    Tag tag = routine.getTag();
    RoutineType effectiveType =
        op.routineType() != null ? parseRoutineType(op.routineType()) : routine.getRoutineType();
    // 반복 유형이 바뀌면 기존 값은 의미가 달라 재사용하지 않고, 새 유형에 맞는 routineDays를 op가 명시하도록 강제한다.
    // 유형이 그대로이고 op가 routineDays를 생략하면 기존 비트마스크를 그대로 유지한다.
    boolean typeChanged = op.routineType() != null && effectiveType != routine.getRoutineType();
    Integer effectiveRoutineDate;
    if (op.routineDays() != null) {
      if (!RoutineDays.isValid(effectiveType, op.routineDays())) {
        throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
      }
      effectiveRoutineDate = RoutineDays.toMask(effectiveType, op.routineDays());
    } else {
      effectiveRoutineDate = typeChanged ? null : routine.getRoutineDate();
    }
    // WEEKLY/MONTHLY인데 최종 반복 날짜가 없으면 400. (DAILY는 null이 정상)
    if (effectiveType != RoutineType.DAILY && effectiveRoutineDate == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    if (effectiveType == RoutineType.DAILY) {
      effectiveRoutineDate = null;
    }
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

    // 이번 회차(앵커) 치우기.
    // 주의: 위에서 만든 Replan은 anchor를 가리킨다. 실패 전이라고 anchor를 소프트 삭제하면
    // @SQLRestriction(deleted_at IS NULL) 때문에 월간 리포트의 리플랜 조회에서 이 리플랜이 통째로
    // 빠진다(리플랜 횟수 누락). 그래서 대체할 살아있는 새 회차가 있을 때만 소프트 삭제하고
    // 리플랜을 그 새 회차로 옮겨 달며, 새 회차가 없으면 비활성화로 남겨 리플랜이 사라지지 않게 한다.
    boolean failedBefore = !isOverdue(anchor);
    if (failedBefore && routineService.willCreateUpcomingOccurrence(routine)) {
      // 실패 전 + 새 규칙의 다음 회차가 만들어짐 → 옛 회차는 소프트 삭제(통계 제외)하고,
      // 새 규칙의 다음 회차(오늘 또는 가까운 미래)를 곧바로 만들어 리플랜을 그 회차로 옮긴다.
      retire(anchor);
      routineService.createTodoTreeFromMother(routine);
      // willCreateUpcomingOccurrence가 true면 위 호출이 반드시 회차를 만든다.
      // 만에 하나 못 찾으면 옛 회차를 소프트 삭제한 채 리플랜이 갈 곳을 잃으므로, 예외로 트랜잭션을 되돌려 데이터 유실을 막는다.
      Todo instance =
          todoRepository
              .findFirstUpcomingMotherTodoByRoutine(routine, LocalDate.now(clock).atStartOfDay())
              .orElseThrow(() -> new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION));
      // 같은 슬롯에 회차가 이미 있어 createTodoTreeFromMother가 새로 만들지 않은 경우(no-op)에도
      // 그 회차가 옛 규칙 내용으로 남지 않도록 제목·태그를 새 루틴 값으로 동기화한다(updateMotherRoutine과 동일).
      instance.updateTitle(routine.getTitle());
      instance.updateTag(routine.getTag());
      instance.getChildren().forEach(child -> child.updateTag(routine.getTag()));
      instance.linkReplan(replan);
      // 이번 리플랜은 메모리에서 바로 새 회차로 옮기고,
      // 같은 회차(앵커)에 이전부터 달려 있던 다른 리플랜들도 빠짐없이 새 회차로 옮긴다.
      // (앵커를 위에서 소프트 삭제했으므로 옮기지 않으면 그 리플랜들이 통계에서 사라진다.)
      replan.relinkTodo(instance);
      relinkReplansTo(anchor, instance);
    } else if (failedBefore) {
      // 실패 전이지만 반복 종료일이 지나 다음 회차가 더는 만들어지지 않는다(루틴이 사실상 끝남).
      // 소프트 삭제하면 리플랜이 통계에서 사라지므로, 회차와 하위 회차를 비활성화로 남긴다.
      anchor.getChildren().forEach(Todo::deactivate);
      anchor.deactivate();
    } else {
      // 실패 후 → 비활성화(통계에 실패로 남김). 앵커가 살아있어 리플랜도 정상 집계된다.
      retire(anchor);
    }
  }

  /** 새 루틴을 만들고 가까운 회차 투두를 리플랜에 연결한다. 회차 투두가 실제로 만들어졌으면 true. */
  private boolean applyCreateRoutine(ReplanOperation op, Todo anchor, Replan replan) {
    if (op.routineType() == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    RoutineType type = parseRoutineType(op.routineType());
    if (!RoutineDays.isValid(type, op.routineDays())) {
      throw new CustomException(ReplanErrorCode.REPLAN_INVALID_OPERATION);
    }
    User user = anchor.getUser();
    Tag tag = resolveTag(op.tagId(), user.getId());
    Routine routine =
        Routine.builder()
            .title(op.title())
            // op.dueDate는 루틴의 반복 종료일(이 날짜 이후로는 회차를 만들지 않음)로 보존한다.
            .dueDate(parseRoutineEndDate(op.dueDate()))
            .routineTime(parseTime(op.dueTime()))
            .routineType(type)
            // 배열 → 비트마스크. DAILY는 null.
            .routineDate(RoutineDays.toMask(type, op.routineDays()))
            .user(user)
            .tag(tag)
            .build();
    routine.linkReplan(replan);
    routineRepository.save(routine);
    routineService.createTodoTreeFromMother(routine);
    Optional<Todo> instance =
        todoRepository.findFirstUpcomingMotherTodoByRoutine(
            routine, LocalDate.now(clock).atStartOfDay());
    instance.ifPresent(instanceTodo -> instanceTodo.linkReplan(replan));
    return instance.isPresent();
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
