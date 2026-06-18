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

  public List<ReplanQuestion> getQuestions(Long userId, ReplanQuestionsRequest req) {
    Todo anchor = findOwnedTodo(userId, req.anchorTodoId());
    RecommendInput input = buildInput(anchor, req.reasonCodes(), req.directInput(), null);
    return aiService.generateQuestions(input);
  }

  public ReplanRecommendResponse recommend(Long userId, ReplanRecommendRequest req) {
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
        answerInputs.add(
            new RecommendInput.AnswerInput(
                a.key(), a.text(), a.selectedTodoIds(), a.selectedChips()));
      }
    }
    return new RecommendInput(
        anchor.getTitle(),
        anchor.getDueDate() != null
            ? anchor.getDueDate().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            : null,
        anchor.getTag() != null ? anchor.getTag().getTitle() : null,
        routine,
        routine ? String.valueOf(anchor.getRoutine().getRoutineType()) : null,
        labels,
        answerInputs,
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
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
    Todo anchor = findOwnedTodo(userId, req.anchorTodoId());
    Replan replan = buildReplan(anchor, req.reasonCodes());
    replanRepository.save(replan);

    if (req.acceptedOperations() == null) {
      return;
    }
    for (ReplanOperation op : req.acceptedOperations()) {
      switch (op.action()) {
        case ADD -> {
          applyAdd(op, anchor, replan);
          deactivateIfReplacedOriginal(anchor);
        }
        case MODIFY_TODO -> applyModifyTodo(op, anchor, replan);
        case MODIFY_ROUTINE -> applyModifyRoutine(op, anchor, replan);
        case CREATE_ROUTINE -> applyCreateRoutine(op, anchor, replan);
      }
    }
  }

  private Replan buildReplan(Todo anchor, List<String> reasonCodes) {
    List<String> codes = reasonCodes != null ? reasonCodes : List.of();
    String r1 = codes.size() > 0 ? codes.get(0) : "UNKNOWN";
    String r2 = codes.size() > 1 ? codes.get(1) : null;
    String r3 = codes.size() > 2 ? codes.get(2) : null;
    return Replan.builder()
        .todo(anchor)
        .failureReason1(r1)
        .failureReason2(r2)
        .failureReason3(r3)
        .build();
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
    target.updateDueDate(combineDueDate(op.dueDate(), op.dueTime()));
    if (op.tagId() != null) {
      target.updateTag(resolveTag(op.tagId(), target.getUser().getId()));
    }
    target.linkReplan(replan);
  }

  /** 마감 지난 일반 투두(앵커)를 ADD로 대체한 경우, 원본은 통계엔 남기되 목록에서만 숨긴다. */
  private void deactivateIfReplacedOriginal(Todo anchor) {
    boolean basic = anchor.getRoutine() == null;
    boolean overdue =
        anchor.getDueDate() != null
            && anchor.getDueDate().isBefore(LocalDateTime.now(clock));
    if (basic && overdue) {
      anchor.deactivate();
    }
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
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_PARSE_ERROR);
    }
  }

  private void applyModifyRoutine(ReplanOperation op, Todo anchor, Replan replan) {
    Routine routine = anchor.getRoutine();
    if (routine == null) {
      throw new CustomException(ReplanErrorCode.REPLAN_TODO_NOT_FOUND);
    }
    Tag tag = op.tagId() != null ? resolveTag(op.tagId(), anchor.getUser().getId()) : routine.getTag();
    routine.update(
        op.title() != null ? op.title() : routine.getTitle(),
        op.routineType() != null ? RoutineType.valueOf(op.routineType()) : routine.getRoutineType(),
        op.routineDate(),
        parseTime(op.dueTime()),
        tag);
    routine.linkReplan(replan);

    // 이번 회차 투두(앵커)가 미완료면 새 규칙에 맞춰 동기화, 완료면 그대로 둔다
    if (!anchor.isCompleted()) {
      if (op.title() != null) {
        anchor.updateTitle(op.title());
      }
      if (op.dueDate() != null || op.dueTime() != null) {
        anchor.updateDueDate(combineDueDate(op.dueDate(), op.dueTime()));
      }
      anchor.linkReplan(replan);
    }
  }

  private void applyCreateRoutine(ReplanOperation op, Todo anchor, Replan replan) {
    User user = anchor.getUser();
    Tag tag = resolveTag(op.tagId(), user.getId());
    Routine routine =
        Routine.builder()
            .title(op.title())
            .routineTime(parseTime(op.dueTime()))
            .routineType(op.routineType() != null ? RoutineType.valueOf(op.routineType()) : null)
            .routineDate(op.routineDate())
            .user(user)
            .tag(tag)
            .build();
    routine.linkReplan(replan);
    routineRepository.save(routine);
  }

  private LocalTime parseTime(String time) {
    if (time == null) {
      return null;
    }
    try {
      return LocalTime.parse(time);
    } catch (DateTimeParseException e) {
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_PARSE_ERROR);
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
