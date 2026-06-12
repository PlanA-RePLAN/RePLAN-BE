package plana.replan.domain.routine.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;

@Getter
@AllArgsConstructor
public class RoutineResponseDto {

  private Long routineId;
  private String title;
  private LocalDateTime dueDate;
  private LocalTime routineTime;
  private RoutineType routineType;
  private Integer routineDate;
  private Long tagId;
  private Long goalId;

  public static RoutineResponseDto from(Routine routine) {
    return new RoutineResponseDto(
        routine.getId(),
        routine.getTitle(),
        routine.getDueDate(),
        routine.getRoutineTime(),
        routine.getRoutineType(),
        routine.getRoutineDate(),
        routine.getTag() != null ? routine.getTag().getId() : null,
        routine.getGoal() != null ? routine.getGoal().getId() : null);
  }
}
