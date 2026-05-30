package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "하위 루틴 생성 요청. 엄마 루틴의 스케줄/태그/목표를 그대로 따라가며, title만 자체 값.")
public record SubRoutineCreateRequestDto(
    @Schema(description = "하위 루틴 제목", example = "스트레칭", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목은 필수입니다.")
        String title) {}
