package plana.replan.domain.goal.dto.explore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 목표 탐색 요청 (목표 + 종료일정만 받아 되물을 질문을 생성)")
public record GoalExploreRequest(
    @Schema(
            description = "목표 (자연어)",
            example = "토익 850점 이상 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(description = "종료 날짜 (yyyy-MM-dd 형식). 선택", example = "2026-05-01") String deadlineDate,
    @Schema(description = "종료 시간 (HH:mm 형식). 선택", example = "23:59") String deadlineTime) {}
