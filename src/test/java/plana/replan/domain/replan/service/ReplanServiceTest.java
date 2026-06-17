package plana.replan.domain.replan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.dto.ReplanQuestionsRequest;
import plana.replan.domain.replan.dto.ReplanRecommendRequest;
import plana.replan.domain.replan.dto.ReplanRecommendResponse;
import plana.replan.domain.replan.dto.ReplanSaveRequest;
import plana.replan.domain.replan.entity.Replan;
import plana.replan.domain.replan.repository.ReplanRepository;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class ReplanServiceTest {

  @Mock private TodoRepository todoRepository;
  @Mock private ReplanRepository replanRepository;
  @Mock private ReplanAiService aiService;
  @Mock private TagRepository tagRepository;

  @InjectMocks private ReplanService replanService;

  private Todo ownedTodo(Long todoId, Long userId) {
    User user = org.mockito.Mockito.mock(User.class);
    given(user.getId()).willReturn(userId);
    Todo todo = org.mockito.Mockito.mock(Todo.class);
    given(todo.getUser()).willReturn(user);
    org.mockito.Mockito.lenient().when(todo.getTitle()).thenReturn("데이터 분석 공부하기");
    return todo;
  }

  @Test
  void 추가질문_조회_성공() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(aiService.generateQuestions(any()))
        .willReturn(List.of(new ReplanQuestion("k", null, "질문", null)));

    ReplanQuestionsRequest req =
        new ReplanQuestionsRequest(42L, List.of("GOAL_NO_PRIORITY"), null);

    List<ReplanQuestion> result = replanService.getQuestions(1L, req);

    assertThat(result).hasSize(1);
  }

  @Test
  void 남의_투두면_조회_실패() {
    Todo todo = ownedTodo(42L, 999L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));

    ReplanQuestionsRequest req =
        new ReplanQuestionsRequest(42L, List.of("GOAL_NO_PRIORITY"), null);

    assertThatThrownBy(() -> replanService.getQuestions(1L, req))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void 추천_성공() {
    Todo todo = ownedTodo(42L, 1L);
    given(todo.getDueDate()).willReturn(LocalDateTime.of(2026, 6, 7, 10, 0));
    given(todo.getTag()).willReturn(org.mockito.Mockito.mock(Tag.class));
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(aiService.generateRecommend(any()))
        .willReturn(new ReplanRecommendResponse("요약", "팁", List.of()));

    ReplanRecommendRequest req =
        new ReplanRecommendRequest(42L, List.of("GOAL_NO_PRIORITY"), null);

    ReplanRecommendResponse res = replanService.recommend(1L, req);

    assertThat(res.summary()).isEqualTo("요약");
  }

  @Test
  void 추가없이_끝내기여도_실패사유는_저장된다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));

    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of());

    replanService.save(1L, req);

    then(replanRepository).should(times(1)).save(any(Replan.class));
    then(todoRepository).should(never()).save(any(Todo.class));
  }

  @Test
  void ADD작업은_새_투두를_만든다() {
    Todo todo = ownedTodo(42L, 1L);
    given(todoRepository.findById(42L)).willReturn(Optional.of(todo));
    given(replanRepository.save(any(Replan.class)))
        .willAnswer(inv -> inv.getArgument(0));

    ReplanOperation add =
        new ReplanOperation(
            ReplanAction.ADD, null, "데이터 분석 3~4강", "2026-06-09", "23:59",
            null, null, null, List.of());
    ReplanSaveRequest req =
        new ReplanSaveRequest(42L, List.of("GOAL_NO_PRIORITY"), List.of(add));

    replanService.save(1L, req);

    then(todoRepository).should(times(1)).save(any(Todo.class));
  }
}
