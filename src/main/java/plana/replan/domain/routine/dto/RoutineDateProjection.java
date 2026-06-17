package plana.replan.domain.routine.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

public interface RoutineDateProjection {
  Long getRoutineId();

  String getTitle();

  LocalDateTime getDueDate();

  LocalTime getRoutineTime();

  String getRoutineType();

  Integer getRoutineDate();

  Long getTagId();

  String getTagTitle();

  String getTagColor();

  Long getGoalId();
}
