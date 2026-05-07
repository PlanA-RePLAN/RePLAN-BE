package plana.replan.domain.goal.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.dto.GoalCreateRequest;
import plana.replan.domain.goal.dto.GoalPageResponse;
import plana.replan.domain.goal.dto.GoalResponse;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class GoalService {

  private final GoalRepository goalRepository;
  private final UserRepository userRepository;

  @Transactional
  public GoalResponse createGoal(Long userId, GoalCreateRequest request) {
    User user = findUser(userId);
    Goal goal =
        Goal.builder()
            .title(request.title())
            .dueDate(request.dueDate())
            .reference(request.reference())
            .user(user)
            .build();
    return GoalResponse.from(goalRepository.save(goal));
  }

  @Transactional
  public void deleteGoal(Long userId, Long goalId) {
    Goal goal =
        goalRepository
            .findById(goalId)
            .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
    if (!goal.getUser().getId().equals(userId)) {
      throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
    }
    goal.softDelete();
  }

  @Transactional(readOnly = true)
  public GoalPageResponse getGoals(Long userId, Long cursor, int size, Integer year) {
    User user = findUser(userId);
    PageRequest pageable = PageRequest.of(0, size + 1);

    List<Goal> goals;
    if (year != null) {
      goals =
          cursor == null
              ? goalRepository.findByUserAndYear(user, year, pageable)
              : goalRepository.findByUserAndYearAndIdLessThan(user, year, cursor, pageable);
    } else {
      goals =
          cursor == null
              ? goalRepository.findByUserOrderByIdDesc(user, pageable)
              : goalRepository.findByUserAndIdLessThanOrderByIdDesc(user, cursor, pageable);
    }

    boolean hasNext = goals.size() > size;
    List<GoalResponse> content = goals.stream().limit(size).map(GoalResponse::from).toList();
    Long nextCursor = hasNext ? content.get(content.size() - 1).id() : null;

    return new GoalPageResponse(content, nextCursor, hasNext);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
