package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "하위 루틴 수정 요청. title만 수정 가능.")
public record SubRoutineUpdateRequestDto(
    @Schema(
            description = "변경할 하위 루틴 제목",
            example = "유산소 30분",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목은 필수입니다.")
        String title) {}
