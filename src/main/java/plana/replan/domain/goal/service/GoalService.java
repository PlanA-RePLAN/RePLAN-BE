package plana.replan.domain.goal.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.dto.GoalCreateRequestDto;
import plana.replan.domain.goal.dto.GoalSingleResponseDto;
import plana.replan.domain.goal.dto.GoalsByDateResponseDto;
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
  public GoalSingleResponseDto createGoal(Long userId, GoalCreateRequestDto request) {
    User user = findUser(userId);
    Goal goal =
        Goal.builder()
            .title(request.title())
            .dueDate(request.dueDate())
            .reference(request.reference())
            .user(user)
            .build();
    return GoalSingleResponseDto.from(goalRepository.save(goal));
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
  public List<GoalsByDateResponseDto> getGoals(Long userId, Integer year, Integer month) {
    if (year == null && month != null) {
      throw new CustomException(GoalErrorCode.GOAL_INVALID_FILTER);
    }
    if (month != null && (month < 1 || month > 12)) {
      throw new CustomException(GoalErrorCode.GOAL_INVALID_MONTH);
    }
    User user = findUser(userId);

    List<Goal> goals;
    if (year != null && month != null) {
      goals = goalRepository.findByUserAndCreatedAtYearAndMonth(user, year, month);
    } else if (year != null) {
      goals = goalRepository.findByUserAndCreatedAtYear(user, year);
    } else {
      goals = goalRepository.findByUserOrderByCreatedAtDescIdAsc(user);
    }

    Map<LocalDate, List<Goal>> byDate =
        goals.stream()
            .collect(
                Collectors.groupingBy(
                    g -> g.getCreatedAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()));

    return byDate.entrySet().stream()
        .map(
            e ->
                new GoalsByDateResponseDto(
                    e.getKey(),
                    e.getValue().stream()
                        .sorted(Comparator.comparing(Goal::getId))
                        .map(GoalSingleResponseDto::from)
                        .toList()))
        .toList();
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
