package plana.replan.domain.routine.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineType;

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

  @Schema(description = "반복 종료 마감일 (ISO 8601 형식). 없으면 null", example = "2025-12-31T00:00:00")
  private LocalDateTime dueDate;

  @Schema(description = "마감 시각 (HH:mm:ss 형식). 없으면 null", example = "08:00:00")
  private LocalTime routineTime;

  @Schema(description = "반복 유형", example = "DAILY")
  private RoutineType routineType;

  @Schema(description = "반복 날짜 설정값. DAILY=null, WEEKLY=요일 비트마스크, MONTHLY=일자 비트마스크", example = "21")
  private Integer routineDate;

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
  private double effectiveSortOrder;

  @Schema(description = "해당 날짜 건너뜀 여부", example = "false")
  private boolean isSkipped;

  @Schema(description = "해당 날짜 핀 여부", example = "false")
  private boolean isPinned;

  @Schema(description = "해당 날짜 완료 여부", example = "false")
  private boolean isCompleted;

  @Schema(description = "해당 날짜에 override 존재 여부", example = "false")
  private boolean hasOverride;

  public static RoutineResponseDto from(Routine routine) {
    return new RoutineResponseDto(
        routine.getId(),
        routine.getTitle(),
        routine.getDueDate(),
        routine.getRoutineTime(),
        routine.getRoutineType(),
        routine.getRoutineDate(),
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
    double effectiveSortOrder =
        p.getOverrideSortOrder() != null ? p.getOverrideSortOrder() : p.getDefaultSortOrder();
    return new RoutineResponseDto(
        p.getRoutineId(),
        p.getTitle(),
        p.getDueDate(),
        p.getRoutineTime(),
        p.getRoutineType() != null ? RoutineType.valueOf(p.getRoutineType()) : null,
        p.getRoutineDate(),
        p.getTagId(),
        p.getTagTitle(),
        p.getTagColor(),
        p.getGoalId(),
        p.getTodoId(),
        effectiveSortOrder,
        Boolean.TRUE.equals(p.getIsSkipped()),
        Boolean.TRUE.equals(p.getIsPinned()),
        Boolean.TRUE.equals(p.getIsCompleted()),
        Boolean.TRUE.equals(p.getHasOverride()));
  }
}
