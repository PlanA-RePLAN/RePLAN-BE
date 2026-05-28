package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 목표 정제 결과")
public record GoalRefinementResponse(
    @Schema(description = "정제된 목표") RefinedField goal,
    @Schema(description = "정제된 마감기한") RefinedDeadline deadline,
    @Schema(description = "정제된 현재 수준") RefinedField currentLevel,
    @Schema(description = "정제된 투자 가능 시간") RefinedField availableTime,
    @Schema(description = "정제된 특이사항") RefinedNotes notes) {}
