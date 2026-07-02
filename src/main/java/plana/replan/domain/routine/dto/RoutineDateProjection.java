package plana.replan.domain.routine.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface RoutineDateProjection {
  Long getRoutineId();

  String getTitle();

  LocalDateTime getDueDate();

  LocalDateTime getRepeatEndDate();

  LocalTime getRoutineTime();

  String getRoutineType();

  Integer getRoutineDate();

  Long getTagId();

  String getTagTitle();

  String getTagColor();

  Long getGoalId();

  Long getTodoId();

  Double getSortOrder();

  Boolean getIsPinned();

  Boolean getIsCompleted();

  Boolean getIsOverdue();

  Boolean getHasOverride();

  LocalDate getOverrideDate();
}
