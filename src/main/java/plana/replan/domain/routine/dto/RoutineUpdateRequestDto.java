package plana.replan.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.LocalTime;
import plana.replan.domain.routine.entity.RoutineType;

@Schema(description = "엄마 루틴 수정 요청 (goalId 수정 불가)")
public record RoutineUpdateRequestDto(
    @Schema(
            description = "루틴 제목",
            example = "영어 단어 외우기",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목은 필수입니다.")
        String title,
    @Schema(description = "반복 종료 마감일 (ISO 8601 형식). null이면 종료일 제거", example = "2025-12-31T00:00:00")
        LocalDateTime dueDate,
    @Schema(
            description = "반복 시각 (HH:mm:ss). 반복되는 날의 마감 시각. null이면 23:59:59로 처리",
            example = "09:00:00")
        LocalTime routineTime,
    @Schema(
            description = "반복 유형 (DAILY / WEEKLY / MONTHLY)",
            example = "WEEKLY",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "반복 유형은 필수입니다.")
        RoutineType routineType,
    @Schema(
            description =
                "반복 날짜. WEEKLY: 요일 bitmask (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64). MONTHLY: 일자 bitmask (1일=1, 2일=2, 3일=4 … 여러 날 합산). DAILY: 불필요.",
            example = "21")
        Integer routineDate,
    @Schema(description = "태그 ID. null이면 태그 제거", example = "1") Long tagId) {}
