package plana.replan.domain.routine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.goal.entity.Goal;
import plana.replan.domain.goal.exception.GoalErrorCode;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.dto.RoutineCreateRequestDto;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.exception.RoutineErrorCode;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.tag.exception.TagErrorCode;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class RoutineService {

  private final RoutineRepository routineRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final GoalRepository goalRepository;

  @Transactional
  public RoutineResponseDto createRoutine(Long userId, RoutineCreateRequestDto request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    validateRoutineDate(request.routineType(), request.routineDate());

    Tag tag = null;
    if (request.tagId() != null) {
      tag =
          tagRepository
              .findById(request.tagId())
              .orElseThrow(() -> new CustomException(TagErrorCode.TAG_NOT_FOUND));
    }

    Goal goal = null;
    if (request.goalId() != null) {
      goal =
          goalRepository
              .findById(request.goalId())
              .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
    }

    Routine routine =
        Routine.builder()
            .title(request.title())
            .dueDate(request.dueDate())
            .routineType(request.routineType())
            .routineDate(request.routineDate())
            .user(user)
            .tag(tag)
            .goal(goal)
            .build();

    return RoutineResponseDto.from(routineRepository.save(routine));
  }

  private void validateRoutineDate(RoutineType routineType, Integer routineDate) {
    if (routineType == RoutineType.WEEKLY) {
      if (routineDate == null || routineDate < 1 || routineDate > 127) {
        throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE);
      }
    } else if (routineType == RoutineType.MONTHLY) {
      if (routineDate == null || routineDate < 1 || routineDate > 31) {
        throw new CustomException(RoutineErrorCode.ROUTINE_INVALID_DATE);
      }
    }
  }
}
