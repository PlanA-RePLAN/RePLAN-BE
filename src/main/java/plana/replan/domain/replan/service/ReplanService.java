package plana.replan.domain.replan.service;

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
        case ADD -> applyAdd(op, anchor, replan);
        default -> {
          /* MODIFY_TODO / MODIFY_ROUTINE / CREATE_ROUTINE 는 Task 5,6 에서 구현 */
        }
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
