package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import plana.replan.domain.routine.entity.RoutineType;

@Schema(description = "루틴 생성 요청")
public record RoutineCreateRequestDto(
    @Schema(
            description = "루틴 제목",
            example = "영어 단어 외우기",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목은 필수입니다.")
        String title,
    @Schema(description = "반복 마감일 (ISO 8601 형식)", example = "2025-12-31T00:00:00")
        LocalDateTime dueDate,
    @Schema(
            description = "반복 유형 (DAILY / WEEKLY / MONTHLY)",
            example = "WEEKLY",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "반복 유형은 필수입니다.")
        RoutineType routineType,
    @Schema(
            description =
                "반복 날짜. WEEKLY: 요일 bitmask (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64). MONTHLY: 일자 (1~31). DAILY: 불필요.",
            example = "21")
        Integer routineDate,
    @Schema(description = "태그 ID", example = "1") Long tagId,
    @Schema(description = "목표 ID", example = "2") Long goalId) {}
