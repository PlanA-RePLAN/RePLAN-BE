package plana.replan.domain.goal.dto.recommend;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "AI 투두 추천 요청 (정제된 솔루션을 전달)")
public record TodoRecommendationRequest(
    @Schema(
            description = "목표",
            example = "토익 850점 달성 (LC 450·RC 450 이상)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(description = "마감 날짜 (yyyy-MM-dd 형식). 선택", example = "2026-05-01") String deadlineDate,
    @Schema(description = "마감 시간 (HH:mm 형식). 선택", example = "23:59") String deadlineTime,
    @Schema(description = "정제·수정이 끝난 솔루션 목록. 선택") List<SolutionInput> solutions,
    @Schema(description = "새로고침 횟수(0~3). 0 또는 생략은 첫 추천, 1~3은 다시 추천", example = "0")
        Integer refreshCount) {}
