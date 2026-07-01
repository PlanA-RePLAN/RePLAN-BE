package plana.replan.domain.routine.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.util.RoutineDays;

@Schema(description = "루틴 조회 응답")
@Getter
@AllArgsConstructor
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class RoutineResponseDto {

  @Schema(description = "루틴 ID", example = "1")
  private Long routineId;

  @Schema(description = "제목 (override 적용값)", example = "아침 스트레칭")
  private String title;

  @Schema(
      description =
          "해당 날짜 인스턴스 마감 일시 (ISO 8601 형식). instanceDate + routineTime, 없으면 23:59:59. 단건 조회 시 null",
      example = "2025-06-20T08:00:00")
  private LocalDateTime dueDate;

  @Schema(description = "반복 종료 마감일 (ISO 8601 형식). 없으면 null", example = "2025-12-31T00:00:00")
  private LocalDateTime repeatEndDate;

  @Schema(description = "마감 시각 (HH:mm:ss 형식). 없으면 null", example = "08:00:00")
  private LocalTime routineTime;

  @Schema(description = "반복 유형", example = "DAILY")
  private RoutineType routineType;

  @Schema(
      description = "반복 날짜 배열. DAILY=null, WEEKLY=요일 인덱스(월0…일6), MONTHLY=일자(1~31)",
      example = "[0, 2, 4]")
  private List<Integer> routineDays;

  @Schema(description = "태그 ID (override 적용값). 없으면 null", example = "3")
  private Long tagId;

  @Schema(description = "태그 제목. 없으면 null", example = "운동")
  private String tagTitle;

  @Schema(description = "태그 색상. 없으면 null", example = "GREEN")
  private String tagColor;

  @Schema(description = "목표 ID. 없으면 null", example = "2")
  private Long goalId;

  @Schema(description = "해당 날짜에 생성된 Todo ID. 아직 생성 안 됐으면 null", example = "42")
  private Long todoId;

  @Schema(description = "해당 날짜 인스턴스의 정렬 순서", example = "10000.0")
  private double sortOrder;

  @Schema(description = "해당 날짜 핀 여부", example = "false")
  private boolean isPinned;

  @Schema(description = "해당 날짜 완료 여부", example = "false")
  private boolean isCompleted;

  @Schema(description = "마감 시각이 지났고 미완료인 경우 true", example = "false")
  private boolean isOverdue;

  @Schema(description = "해당 날짜에 override 존재 여부", example = "false")
  private boolean hasOverride;

  public static RoutineResponseDto from(Routine routine) {
    return new RoutineResponseDto(
        routine.getId(),
        routine.getTitle(),
        null,
        routine.getDueDate(),
        routine.getRoutineTime(),
        routine.getRoutineType(),
        RoutineDays.toDays(routine.getRoutineType(), routine.getRoutineDate()),
        routine.getTag() != null ? routine.getTag().getId() : null,
        routine.getTag() != null ? routine.getTag().getTitle() : null,
        routine.getTag() != null ? routine.getTag().getColor() : null,
        routine.getGoal() != null ? routine.getGoal().getId() : null,
        null,
        routine.getDefaultSortOrder(),
        false,
        false,
        false,
        false);
  }

  public static RoutineResponseDto from(RoutineDateProjection p) {
    RoutineType type = p.getRoutineType() != null ? RoutineType.valueOf(p.getRoutineType()) : null;
    return new RoutineResponseDto(
        p.getRoutineId(),
        p.getTitle(),
        p.getDueDate(),
        p.getRepeatEndDate(),
        p.getRoutineTime(),
        type,
        RoutineDays.toDays(type, p.getRoutineDate()),
        p.getTagId(),
        p.getTagTitle(),
        p.getTagColor(),
        p.getGoalId(),
        p.getTodoId(),
        p.getSortOrder() != null ? p.getSortOrder() : 10000.0,
        Boolean.TRUE.equals(p.getIsPinned()),
        Boolean.TRUE.equals(p.getIsCompleted()),
        Boolean.TRUE.equals(p.getIsOverdue()),
        Boolean.TRUE.equals(p.getHasOverride()));
  }
}
