package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plana.replan.domain.routine.entity.Routine;

@Schema(description = "하위 루틴 응답. title 외 모든 스케줄/태그/목표는 엄마 루틴을 따른다.")
public record SubRoutineResponseDto(
    @Schema(description = "하위 루틴 ID", example = "11") Long routineId,
    @Schema(description = "하위 루틴 제목", example = "스트레칭") String title,
    @Schema(description = "엄마 루틴 ID", example = "1") Long parentId) {

  public static SubRoutineResponseDto from(Routine child) {
    return new SubRoutineResponseDto(
        child.getId(),
        child.getTitle(),
        child.getParent() != null ? child.getParent().getId() : null);
  }
}
