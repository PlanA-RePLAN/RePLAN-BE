package plana.replan.domain.replan.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import plana.replan.domain.replan.dto.ReplanAnswer;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanQuestionsRequest;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.entity.FailureReasonCode;
import plana.replan.domain.replan.exception.ReplanErrorCode;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class ReplanService {

  private final TodoRepository todoRepository;
  private final ReplanRepository replanRepository;
  private final ReplanAiService aiService;

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
