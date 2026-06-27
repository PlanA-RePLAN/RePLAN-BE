package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "루틴 인스턴스 내용 수정 요청")
public record RoutineOverrideContentRequestDto(
    @Schema(description = "제목 override. null이면 루틴 기본 제목 유지", example = "아침 스트레칭 (특별)") String title,
    @Schema(description = "태그 ID override. null이면 루틴 기본 태그 유지", example = "5") Long tagId) {}
