package plana.replan.domain.routine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.entity.RoutineOverride;
import plana.replan.domain.routine.util.RoutineDays;
import plana.replan.domain.tag.entity.Tag;

@Schema(description = "루틴 인스턴스 override 응답")
public record RoutineOverrideResponseDto(
    @Schema(description = "루틴 ID", example = "1") Long routineId,
    @Schema(description = "override 적용 날짜 (yyyy-MM-dd 형식)", example = "2026-07-01")
        LocalDate overrideDate,
    @Schema(description = "실제 적용될 제목", example = "아침 스트레칭 (특별)") String effectiveTitle,
    @Schema(description = "실제 적용될 태그 ID. 없으면 null", example = "5") Long effectiveTagId,
    @Schema(description = "실제 적용될 태그 제목. 없으면 null", example = "운동") String effectiveTagTitle,
    @Schema(description = "실제 적용될 태그 색상. 없으면 null", example = "GREEN") String effectiveTagColor,
    @Schema(description = "실제 적용될 정렬 순서", example = "5000.0") double effectiveSortOrder,
    @Schema(description = "그날의 실제 마감시간 (회차 예외 시간 > 루틴 기본 시간 > 23:59:59)", example = "15:00:00")
        LocalTime effectiveTime,
    @Schema(description = "반복 유형 (DAILY/WEEKLY/MONTHLY)", example = "DAILY") String routineType,
    @Schema(description = "반복 날짜 배열. WEEKLY=요일 인덱스(월0…일6), MONTHLY=일자(1~31), DAILY=null")
        List<Integer> routineDays,
    @Schema(description = "루틴 기본 반복시간. 설정 안 했으면 null", example = "09:00:00") LocalTime routineTime,
    @Schema(description = "반복 종료일", example = "2099-12-31T08:00:00") LocalDateTime repeatEndDate,
    @JsonProperty("isSkipped") @Schema(description = "건너뜀 여부", example = "false") boolean isSkipped,
    @JsonProperty("isPinned") @Schema(description = "핀 여부", example = "false") boolean isPinned,
    @JsonProperty("isCompleted") @Schema(description = "완료 여부", example = "false")
        boolean isCompleted,
    @JsonProperty("hasOverride") @Schema(description = "override 존재 여부", example = "true")
        boolean hasOverride,
    @Schema(description = "이미 배치로 생성된 Todo ID. 없으면 null", example = "42") Long todoId,
    @Schema(description = "예약된 하위 투두 제목 목록 (행이 아직 없는 회차 전용). 없으면 빈 목록")
        List<String> reservedSubtodos) {

  public static RoutineOverrideResponseDto ofNoOverride(
      Routine routine, LocalDate date, Long todoId) {
    Tag tag = resolveTag(routine, null);
    return new RoutineOverrideResponseDto(
        routine.getId(),
        date,
        routine.getTitle(),
        tag != null ? tag.getId() : null,
        tag != null ? tag.getTitle() : null,
        tag != null ? tag.getColor() : null,
        routine.getDefaultSortOrder(),
        resolveTime(routine, null),
        routine.getRoutineType() != null ? routine.getRoutineType().name() : null,
        RoutineDays.toDays(routine.getRoutineType(), routine.getRoutineDate()),
        routine.getRoutineTime(),
        routine.getDueDate(),
        false,
        false,
        false,
        false,
        todoId,
        List.of());
  }

  public static RoutineOverrideResponseDto of(
      Routine routine, RoutineOverride override, Long todoId) {
    Tag effectiveTag = resolveTag(routine, override);
    String effectiveTitle = resolveTitle(routine, override);
    double effectiveSortOrder = resolveSortOrder(routine, override);

    return new RoutineOverrideResponseDto(
        routine.getId(),
        override.getOverrideDate(),
        effectiveTitle,
        effectiveTag != null ? effectiveTag.getId() : null,
        effectiveTag != null ? effectiveTag.getTitle() : null,
        effectiveTag != null ? effectiveTag.getColor() : null,
        effectiveSortOrder,
        resolveTime(routine, override),
        routine.getRoutineType() != null ? routine.getRoutineType().name() : null,
        RoutineDays.toDays(routine.getRoutineType(), routine.getRoutineDate()),
        routine.getRoutineTime(),
        routine.getDueDate(),
        override.isSkipped(),
        Boolean.TRUE.equals(override.getIsPinned()),
        Boolean.TRUE.equals(override.getIsCompleted()),
        true,
        todoId,
        override.getOverrideSubtodos() != null ? override.getOverrideSubtodos() : List.of());
  }

  // 시간 우선순위: 회차 예외 시간 > 루틴 기본 시간 > 23:59:59
  private static LocalTime resolveTime(Routine routine, RoutineOverride override) {
    if (override != null && override.getOverrideTime() != null) {
      return override.getOverrideTime();
    }
    return routine.getRoutineTime() != null ? routine.getRoutineTime() : LocalTime.of(23, 59, 59);
  }

  private static Tag resolveTag(Routine routine, RoutineOverride override) {
    return (override != null && override.getTag() != null) ? override.getTag() : routine.getTag();
  }

  private static String resolveTitle(Routine routine, RoutineOverride override) {
    return (override != null && override.getTitle() != null)
        ? override.getTitle()
        : routine.getTitle();
  }

  private static double resolveSortOrder(Routine routine, RoutineOverride override) {
    return (override != null && override.getSortOrder() != null)
        ? override.getSortOrder()
        : routine.getDefaultSortOrder();
  }
}
